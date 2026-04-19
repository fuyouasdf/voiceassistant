package com.voiceassistant.app.ui.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ActivityPlaylistBinding
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val LOCAL_DEVICE_SESSION_ID = "__local_device_session__"

/**
 * 播放列表页面
 * 用于显示和管理播放列表中的歌曲
 */
@AndroidEntryPoint
class PlaylistActivity : AppCompatActivity() {

    private val viewModel: PlaylistViewModel by viewModels()
    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var songAdapter: PlaylistAdapter
    private var dlnaDialog: AlertDialog? = null

    @javax.inject.Inject
    lateinit var musicPlayer: MusicPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get playlistId from intent and load playlist
        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        if (playlistId == -1L) {
            Toast.makeText(this, "无效的播放列表", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupRecyclerView()
        setupListeners()
        observeState()

        // Load playlist
        viewModel.setPlaylistId(playlistId)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }

        // Card now playing needs to adjust for both system bars and IME (keyboard)
        ViewCompat.setOnApplyWindowInsetsListener(binding.cardNowPlaying) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val params = view.layoutParams as android.widget.LinearLayout.LayoutParams
            // 取 systemBars 和 ime 中的较大值，键盘弹出时 ime.bottom > 0，键盘收起时 systemBars.bottom > 0
            params.bottomMargin = maxOf(systemBars.bottom, ime.bottom) +
                resources.getDimensionPixelSize(R.dimen.spacing_lg)
            view.layoutParams = params
            windowInsets
        }

        // RecyclerView songs list needs to adjust padding when keyboard is visible
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerSongs) { view, windowInsets ->
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomPadding)
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        songAdapter = PlaylistAdapter(
            onSongClick = { song -> viewModel.playSong(song) },
            onDeleteClick = { song ->
                showDeleteSongDialog(song.songId, song.title)
            }
        )
        binding.recyclerSongs.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = songAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDeletePlaylist.setOnClickListener {
            showDeletePlaylistDialog()
        }

        binding.chipDlnaDevice.setOnClickListener {
            showDlnaDeviceDialog()
        }

        binding.btnPlayPauseMini.setOnClickListener {
            viewModel.togglePlayPause()
        }
        binding.btnPreviousMini.setOnClickListener {
            musicPlayer.playPrevious()
        }
        binding.btnNextMini.setOnClickListener {
            musicPlayer.playNext()
        }

        binding.btnStopMini.setOnClickListener {
            viewModel.stopPlayback()
        }

        binding.cardNowPlaying.setOnClickListener {
            openNowPlayingIfLocalSelected()
        }
    }

    private fun showDlnaDeviceDialog() {
        val devices = viewModel.uiState.value.dlnaDevices
        val deviceNames = devices.map { it.deviceName }.toTypedArray()
        dlnaDialog?.dismiss()
        dlnaDialog = AlertDialog.Builder(this)
            .setTitle("选择播放设备")
            .setItems(deviceNames) { _, which ->
                if (which < devices.size) {
                    viewModel.selectDlnaDevice(devices[which])
                }
            }
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Loading
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                // Playlist info
                state.playlist?.let { playlist ->
                    binding.tvPlaylistName.text = playlist.name
                }

                // Songs
                songAdapter.submitList(state.songs)

                // Empty state
                val isEmpty = !state.isLoading && state.songs.isEmpty()
                binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerSongs.visibility = if (isEmpty) View.GONE else View.VISIBLE

                // Song count
                binding.tvSongCount.text = "${state.songs.size} 首歌曲"

                // Selected DLNA device
                binding.chipDlnaDevice.text = state.selectedDlnaDevice?.deviceName ?: "本机"

                // Error
                state.error?.let { error ->
                    Toast.makeText(this@PlaylistActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }

        // 监听临时播放列表状态变化
        lifecycleScope.launch {
            var wasActive = false
            musicPlayer.state.collectLatest {
                val isActive = musicPlayer.isTempPlaylistActive
                if (isActive && !wasActive) {
                    Toast.makeText(this@PlaylistActivity, "临时播放列表模式，点击右侧图标退出", Toast.LENGTH_LONG).show()
                } else if (!isActive && wasActive) {
                    Toast.makeText(this@PlaylistActivity, "已退出临时播放列表", Toast.LENGTH_SHORT).show()
                }
                wasActive = isActive
            }
        }

        lifecycleScope.launch {
            musicPlayer.state.collectLatest { playerState ->
                val state = viewModel.uiState.value
                val selectedDevice = state.selectedDlnaDevice
                // selectedDevice 为 null 时表示使用本机（默认设备）
                val isLocalDevice = selectedDevice == null || selectedDevice.id == LOCAL_DEVICE_SESSION_ID

                if (isLocalDevice) {
                    // 本地播放：使用 musicPlayer.state
                    var currentItem = playerState.playlist.getOrNull(playerState.currentIndex)
                    if (currentItem == null && playerState.currentSongId != null) {
                        currentItem = playerState.playlist.find { it.id == playerState.currentSongId }
                    }

                    if (currentItem == null) {
                        binding.cardNowPlaying.visibility = View.GONE
                        return@collectLatest
                    }

                    binding.cardNowPlaying.visibility = View.VISIBLE
                    binding.tvNowPlayingTitle.text = currentItem.title
                    val artist = currentItem.artist ?: getString(R.string.artist_unknown)
                    val playbackState = if (playerState.isPlaying) getString(R.string.state_playing) else getString(R.string.state_paused)
                    binding.tvNowPlayingArtist.text = "$playbackState · $artist"
                    binding.tvNowPlayingStatus.text = buildMiniPlayerStatus(currentItem)
                    val progress = if (playerState.duration > 0) {
                        ((playerState.currentPosition * 1000) / playerState.duration).toInt().coerceIn(0, 1000)
                    } else {
                        0
                    }
                    binding.progressNowPlaying.progress = progress
                    binding.tvNowPlayingTime.text =
                        "${formatTime(playerState.currentPosition)} / ${formatTime(playerState.duration)}"
                    binding.btnPreviousMini.isEnabled = playerState.playlist.isNotEmpty()
                    binding.btnNextMini.isEnabled = playerState.playlist.isNotEmpty()
                } else {
                    // 远程播放：使用 session 的 nowPlayingItem
                    val session = state.selectedDlnaDevice
                    val isPlaying = state.isPlaying
                    val nowPlayingItem = session?.nowPlayingItem

                    if (nowPlayingItem == null) {
                        binding.cardNowPlaying.visibility = View.GONE
                        return@collectLatest
                    }

                    binding.cardNowPlaying.visibility = View.VISIBLE
                    binding.tvNowPlayingTitle.text = nowPlayingItem.name ?: getString(R.string.unknown)
                    val artist = nowPlayingItem.artists.firstOrNull() ?: getString(R.string.artist_unknown)
                    val playbackState = if (isPlaying) getString(R.string.state_playing) else getString(R.string.state_paused)
                    binding.tvNowPlayingArtist.text = "$playbackState · $artist"
                    binding.tvNowPlayingStatus.text = getString(R.string.casting_to_device, session?.deviceName ?: "")
                    binding.progressNowPlaying.progress = 0
                    binding.tvNowPlayingTime.text = "--:-- / --:--"
                    binding.btnPreviousMini.isEnabled = false
                    binding.btnNextMini.isEnabled = false
                }
            }
        }
    }

    private fun showDeleteSongDialog(songId: String, songTitle: String) {
        AlertDialog.Builder(this)
            .setTitle("移除歌曲")
            .setMessage("确定要从播放列表中移除「$songTitle」吗？")
            .setPositiveButton("移除") { _, _ ->
                viewModel.removeSong(songId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeletePlaylistDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除播放列表")
            .setMessage("确定要删除这个播放列表吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deletePlaylist()
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openNowPlayingIfLocalSelected() {
        val selectedDevice = viewModel.uiState.value.selectedDlnaDevice ?: return
        if (selectedDevice.id != LOCAL_DEVICE_SESSION_ID) return
        startActivity(Intent(this, NowPlayingActivity::class.java))
    }

    private fun buildMiniPlayerStatus(currentItem: MusicItem): String {
        val container = currentItem.streamContainer?.uppercase() ?: getString(R.string.unknown)
        return if (currentItem.isTranscoding) {
            getString(R.string.transcoding_to_aac, container)
        } else {
            getString(R.string.direct_playback, container)
        }
    }

    private fun formatTime(positionMs: Long): String {
        if (positionMs <= 0L) return "00:00"
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlistId"
    }
}
