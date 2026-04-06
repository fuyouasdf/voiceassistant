package com.voiceassistant.app.ui.music

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ActivityLyricsFullscreenBinding
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.LyricLine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LyricsFullscreenActivity : AppCompatActivity() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    @Inject
    lateinit var jellyfinClient: JellyfinClient

    private lateinit var binding: ActivityLyricsFullscreenBinding
    private val lyricsAdapter = LyricsAdapter { line ->
        musicPlayer.seekTo(line.startMs)
        updateLyricsPosition(line.startMs)
    }
    private var progressJob: Job? = null
    private var lyricsJob: Job? = null
    private var currentLyricsSongId: String? = null
    private var currentLyrics: List<LyricLine> = emptyList()
    private var highlightedLyricIndex: Int = -1
    private var isUserScrollingLyrics = false
    private var pendingCenterLyricIndex: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLyricsFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupRecyclerView()
        setupListeners()
        observePlayerState()
    }

    override fun onStart() {
        super.onStart()
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (true) {
                updateLyricsPosition(musicPlayer.getState().currentPosition)
                delay(500)
            }
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

    private fun setupRecyclerView() {
        binding.recyclerLyrics.apply {
            layoutManager = LinearLayoutManager(this@LyricsFullscreenActivity)
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

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            musicPlayer.state.collectLatest { state ->
                val currentItem = state.playlist.getOrNull(state.currentIndex)
                binding.tvSongTitle.text = currentItem?.title ?: "暂无播放"
                binding.tvArtist.text = currentItem?.artist ?: "未知艺术家"
                syncLyrics(currentItem?.id)
                updatePlaybackSummary(state.currentPosition, state.duration)
            }
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
                renderLyricsState("当前歌曲没有可用歌词", "当前歌曲没有可用歌词")
            } else {
                lyricsAdapter.submitLyrics(currentLyrics, -1)
                binding.recyclerLyrics.scrollToPosition(0)
                updateLyricsPosition(musicPlayer.getState().currentPosition)
            }
        }
    }

    private fun updateLyricsPosition(positionMs: Long) {
        if (currentLyrics.isEmpty()) return
        val currentIndex = currentLyrics.indexOfLast { it.startMs <= positionMs }
        if (currentIndex < 0) {
            renderLyricsState("已加载 ${currentLyrics.size} 行歌词，可点击歌词跳转", "前奏中")
            lyricsAdapter.updateActiveLine(-1)
            highlightedLyricIndex = -1
            return
        }
        renderLyricsState("已加载 ${currentLyrics.size} 行歌词，可点击歌词跳转", null)
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
        layoutManager.scrollToPositionWithOffset(index, recyclerHeight / 2 - 72)
    }

    private fun updatePlaybackSummary(positionMs: Long, durationMs: Long) {
        binding.tvPlaybackTime.text = "${formatTime(positionMs)} / ${formatTime(durationMs)}"
    }

    private fun formatTime(positionMs: Long): String {
        if (positionMs <= 0L) return "00:00"
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
