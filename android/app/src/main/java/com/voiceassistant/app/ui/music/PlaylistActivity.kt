package com.voiceassistant.app.ui.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import com.voiceassistant.app.ui.playback.MiniPlayerFragment
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
        setupMiniPlayer(savedInstanceState)
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

        // RecyclerView songs list needs to adjust padding when keyboard is visible
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerSongs) { view, windowInsets ->
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomPadding)
            windowInsets
        }

        // Mini player container needs to adjust for system bars and IME
        ViewCompat.setOnApplyWindowInsetsListener(binding.miniPlayerContainer) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = maxOf(systemBars.bottom, ime.bottom)
            view.layoutParams = params
            windowInsets
        }
    }

    private fun setupMiniPlayer(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
                .commit()
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

        // Mini player click opens NowPlayingActivity
        binding.miniPlayerContainer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
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
                    val selectedDevice = devices[which]
                    viewModel.selectDlnaDevice(selectedDevice)
                    Toast.makeText(
                        this,
                        if (selectedDevice.id == LOCAL_DEVICE_SESSION_ID) "已切换到本机播放" else "已切换到 ${selectedDevice.deviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
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

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlistId"
    }
}
