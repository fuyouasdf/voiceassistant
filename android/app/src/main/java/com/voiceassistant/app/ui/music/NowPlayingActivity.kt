package com.voiceassistant.app.ui.music

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.FragmentNowPlayingBinding
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.RepeatMode
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.LyricLine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NowPlayingActivity : AppCompatActivity() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    @Inject
    lateinit var jellyfinClient: JellyfinClient

    private lateinit var binding: FragmentNowPlayingBinding
    private var progressJob: Job? = null
    private var isUserScrubbing = false
    private var lyricsJob: Job? = null
    private var currentLyricsSongId: String? = null
    private var currentLyrics: List<LyricLine> = emptyList()
    private var highlightedLyricIndex: Int = -1
    private var isUserScrollingLyrics = false
    private var pendingCenterLyricIndex: Int? = null
    private val lyricsAdapter = LyricsAdapter { line ->
        musicPlayer.seekTo(line.startMs)
        updateLyricsPosition(line.startMs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupLyricsList()
        setupListeners()
        observePlayerState()
    }

    override fun onStart() {
        super.onStart()
        startProgressUpdates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-observe player state to ensure UI is up-to-date
        // The collectLatest in observePlayerState will handle updates
    }

    private fun setupLyricsList() {
        binding.recyclerLyrics.apply {
            layoutManager = LinearLayoutManager(this@NowPlayingActivity)
            adapter = lyricsAdapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    isUserScrollingLyrics = newState != RecyclerView.SCROLL_STATE_IDLE
                    if (!isUserScrollingLyrics) {
                        pendingCenterLyricIndex?.let { index ->
                            pendingCenterLyricIndex = null
                            centerLyricLine(index)
                        }
                    }
                }
            })
        }
    }

    override fun onStop() {
        progressJob?.cancel()
        progressJob = null
        super.onStop()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showMoreMenu() }

        binding.btnPlayPause.setOnClickListener {
            if (musicPlayer.getState().isPlaying) {
                musicPlayer.pause()
            } else {
                musicPlayer.resume()
            }
        }
        binding.btnPrevious.setOnClickListener { musicPlayer.playPrevious() }
        binding.btnNext.setOnClickListener { musicPlayer.playNext() }
        binding.btnFavorite.setOnClickListener {
            val isFavorite = musicPlayer.toggleFavorite()
            updateFavoriteButton(isFavorite)
        }
        binding.btnShuffle.setOnClickListener {
            val enabled = musicPlayer.toggleShuffle()
            updateShuffleButton(enabled)
        }
        binding.btnRepeat.setOnClickListener {
            val mode = musicPlayer.toggleRepeat()
            updateRepeatButton(mode)
        }
        binding.btnPlaylist.setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }
        binding.lyricsCard.setOnClickListener {
            startActivity(Intent(this, LyricsFullscreenActivity::class.java))
        }

        binding.defaultTimeBar.addListener(
            object : androidx.media3.ui.TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    isUserScrubbing = true
                }

                override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    binding.tvCurrentTime.text = formatTime(position)
                }

                override fun onScrubStop(
                    timeBar: androidx.media3.ui.TimeBar,
                    position: Long,
                    canceled: Boolean
                ) {
                    isUserScrubbing = false
                    if (!canceled) {
                        musicPlayer.seekTo(position)
                    }
                }
            }
        )
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            musicPlayer.state.collectLatest { state ->
                val currentItem = state.playlist.getOrNull(state.currentIndex)
                binding.tvSongTitle.text = currentItem?.title ?: "暂无播放"
                binding.tvArtist.text = currentItem?.artist ?: "未知艺术家"
                binding.tvAlbum.text = currentItem?.album ?: ""
                binding.tvPlaybackSummary.text = buildPlaybackSummary(state.isPlaying, currentItem != null)
                binding.tvQueueInfo.text = buildQueueInfo(state.currentIndex, state.playlist.size)
                binding.tvStreamStatus.text = buildStreamStatus(currentItem)
                binding.btnPlayPause.setImageResource(
                    if (state.isPlaying) R.drawable.ic_pause
                    else R.drawable.ic_play
                )
                applyButtonState(binding.btnPrevious, state.playlist.isNotEmpty())
                applyButtonState(binding.btnNext, state.playlist.isNotEmpty())
                applyButtonState(binding.btnPlayPause, state.playlist.isNotEmpty())
                applyButtonState(binding.btnShuffle, state.playlist.size > 1)
                applyButtonState(binding.btnRepeat, state.playlist.isNotEmpty())
                applyButtonState(binding.btnPlaylist, state.playlist.isNotEmpty())
                applyButtonState(binding.btnFavorite, state.playlist.isNotEmpty())
                binding.defaultTimeBar.isEnabled = state.duration > 0
                binding.ivCover.load(currentItem?.coverUrl) {
                    placeholder(R.drawable.ic_music)
                    error(R.drawable.ic_music)
                }
                syncLyrics(currentItem?.id)
                binding.tvTotalTime.text = formatTime(state.duration)
                if (!isUserScrubbing) {
                    binding.defaultTimeBar.setDuration(state.duration)
                    binding.defaultTimeBar.setPosition(state.currentPosition)
                    binding.tvCurrentTime.text = formatTime(state.currentPosition)
                    updateLyricsPosition(state.currentPosition)
                }
                updateFavoriteButton(musicPlayer.isFavorite())
                updateShuffleButton(state.isShuffleEnabled)
                updateRepeatButton(state.repeatMode)
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (true) {
                val state = musicPlayer.getState()
                if (!isUserScrubbing) {
                    binding.defaultTimeBar.setDuration(state.duration)
                    binding.defaultTimeBar.setPosition(state.currentPosition)
                    binding.tvCurrentTime.text = formatTime(state.currentPosition)
                    binding.tvTotalTime.text = formatTime(state.duration)
                    updateLyricsPosition(state.currentPosition)
                }
                delay(500)
            }
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        binding.btnFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled
            else R.drawable.ic_favorite_outline
        )
        binding.btnFavorite.setColorFilter(
            ContextCompat.getColor(
                this,
                if (isFavorite) R.color.primary else R.color.text_secondary
            )
        )
    }

    private fun updateShuffleButton(enabled: Boolean) {
        // Change icon AND color for active state
        binding.btnShuffle.setImageResource(
            if (enabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        )
        binding.btnShuffle.setColorFilter(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.primary else R.color.text_secondary
            )
        )
        binding.btnShuffle.alpha = if (enabled) 1f else 0.72f
    }

    private fun updateRepeatButton(mode: RepeatMode) {
        // Change icon AND color for each mode
        when (mode) {
            RepeatMode.OFF -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnRepeat.alpha = 0.72f
            }
            RepeatMode.ALL -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                binding.btnRepeat.alpha = 1f
            }
            RepeatMode.ONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                binding.btnRepeat.alpha = 1f
            }
        }
        binding.btnRepeat.contentDescription = when (mode) {
            RepeatMode.OFF -> "循环关闭"
            RepeatMode.ALL -> "列表循环"
            RepeatMode.ONE -> "单曲循环"
        }
    }

    private fun showMoreMenu() {
        val state = musicPlayer.getState()
        val currentItem = state.playlist.getOrNull(state.currentIndex)
        val options = arrayOf("查看流信息", "打开 Jellyfin 浏览", "全屏歌词", "停止播放")
        AlertDialog.Builder(this)
            .setTitle("更多")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showStreamInfoDialog(currentItem)
                    1 -> startActivity(Intent(this, JellyfinBrowseActivity::class.java))
                    2 -> startActivity(Intent(this, LyricsFullscreenActivity::class.java))
                    3 -> {
                        musicPlayer.stop()
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showStreamInfoDialog(currentItem: com.voiceassistant.core.music.MusicItem?) {
        if (currentItem == null) {
            AlertDialog.Builder(this)
                .setTitle("流信息")
                .setMessage("当前没有正在播放的内容")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val message = buildString {
            appendLine("标题: ${currentItem.title}")
            appendLine("艺术家: ${currentItem.artist ?: "未知艺术家"}")
            appendLine("容器: ${currentItem.streamContainer ?: "未知"}")
            appendLine("播放方式: ${currentItem.streamPlayMethod ?: "未知"}")
            appendLine("转码: ${if (currentItem.isTranscoding) "是" else "否"}")
            appendLine("会话 ID: ${currentItem.playbackSessionId ?: "无"}")
            append("媒体源 ID: ${currentItem.mediaSourceId ?: "无"}")
        }

        AlertDialog.Builder(this)
            .setTitle("流信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun buildStreamStatus(currentItem: com.voiceassistant.core.music.MusicItem?): String {
        if (currentItem == null) {
            return "当前没有播放内容"
        }

        val container = currentItem.streamContainer?.uppercase() ?: "未知格式"
        return if (currentItem.isTranscoding) {
            "$container，正在服务器转码为 AAC"
        } else {
            val method = currentItem.streamPlayMethod ?: "DIRECT"
            "$container，本机直连播放（$method）"
        }
    }

    private fun buildPlaybackSummary(isPlaying: Boolean, hasCurrentItem: Boolean): String {
        return when {
            !hasCurrentItem -> "等待播放"
            isPlaying -> "本机播放中"
            else -> "已暂停"
        }
    }

    private fun syncLyrics(songId: String?) {
        if (songId == currentLyricsSongId) return
        currentLyricsSongId = songId
        lyricsJob?.cancel()
        currentLyrics = emptyList()
        highlightedLyricIndex = -1
        lyricsAdapter.submitLyrics(emptyList(), -1)
        renderLyricsState(
            status = if (songId == null) "当前没有播放内容" else "正在加载歌词",
            emptyMessage = if (songId == null) "当前没有播放内容" else "歌词加载中"
        )
        if (songId == null) return

        lyricsJob = lifecycleScope.launch {
            val result = jellyfinClient.getLyrics(songId)
            currentLyrics = result?.lines.orEmpty()
            if (currentLyrics.isEmpty()) {
                lyricsAdapter.submitLyrics(emptyList(), -1)
                renderLyricsState(
                    status = "当前歌曲没有可用歌词",
                    emptyMessage = "当前歌曲没有可用歌词"
                )
            } else {
                lyricsAdapter.submitLyrics(currentLyrics, -1)
                binding.recyclerLyrics.scrollToPosition(0)
                updateLyricsPosition(musicPlayer.getState().currentPosition)
            }
        }
    }

    private fun updateLyricsPosition(positionMs: Long) {
        if (currentLyrics.isEmpty()) {
            return
        }
        val currentIndex = currentLyrics.indexOfLast { it.startMs <= positionMs }
        if (currentIndex < 0) {
            renderLyricsState(
                status = "已加载 ${currentLyrics.size} 行歌词，可点击歌词跳转",
                emptyMessage = "前奏中"
            )
            lyricsAdapter.updateActiveLine(-1)
            highlightedLyricIndex = -1
            return
        }
        renderLyricsState(
            status = "已加载 ${currentLyrics.size} 行歌词，可点击歌词跳转",
            emptyMessage = null
        )
        lyricsAdapter.updateActiveLine(currentIndex)
        if (highlightedLyricIndex != currentIndex) {
            highlightedLyricIndex = currentIndex
            if (isUserScrollingLyrics) {
                pendingCenterLyricIndex = currentIndex
            } else {
                centerLyricLine(currentIndex)
            }
        }
    }

    private fun renderLyricsState(status: String, emptyMessage: String?) {
        binding.tvLyricsStatus.text = status
        binding.tvLyricsEmpty.text = emptyMessage ?: ""
        binding.tvLyricsEmpty.visibility = if (emptyMessage != null) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerLyrics.visibility = if (emptyMessage != null && currentLyrics.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun centerLyricLine(index: Int) {
        val layoutManager = binding.recyclerLyrics.layoutManager as? LinearLayoutManager ?: return
        val recyclerHeight = binding.recyclerLyrics.height
        if (recyclerHeight <= 0) {
            binding.recyclerLyrics.post { centerLyricLine(index) }
            return
        }
        layoutManager.scrollToPositionWithOffset(index, recyclerHeight / 2 - 48)
    }

    private fun buildQueueInfo(currentIndex: Int, total: Int): String {
        if (total <= 0) {
            return "当前队列为空"
        }
        return "第 ${currentIndex + 1} 首 / 共 $total 首"
    }

    private fun applyButtonState(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.38f
    }

    private fun formatTime(positionMs: Long): String {
        if (positionMs <= 0L) return "00:00"
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
