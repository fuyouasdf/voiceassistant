package com.voiceassistant.app.ui.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.voiceassistant.app.R
import com.voiceassistant.data.remote.JellyfinAlbum
import com.voiceassistant.data.remote.JellyfinSong
import com.voiceassistant.data.remote.SessionInfo
import com.voiceassistant.data.repository.PlaylistRepository
import com.voiceassistant.domain.model.Song
import com.voiceassistant.app.databinding.ActivityJellyfinBrowseBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Jellyfin 浏览页面
 * 用于浏览专辑、搜索歌曲、播放到DLNA设备
 */
@AndroidEntryPoint
class JellyfinBrowseActivity : AppCompatActivity() {

    private val viewModel: JellyfinBrowseViewModel by viewModels()
    private lateinit var binding: ActivityJellyfinBrowseBinding

    // Adapters
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var songAdapter: SongAdapter

    // DLNA Dialog
    private var dlnaDialog: AlertDialog? = null
    private var lastKnownDeviceCount = 0

    private lateinit var topBar: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJellyfinBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        initViews()
        setupInsets()
        setupRecyclerViews()
        setupListeners()
        observeState()
    }

    private fun initViews() {
        topBar = findViewById(R.id.topBar)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.cardNowPlaying) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as LinearLayout.LayoutParams
            params.bottomMargin = insets.bottom + 16.dpToPx()
            view.layoutParams = params
            windowInsets
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupRecyclerViews() {
        // Album grid - 2 columns
        albumAdapter = AlbumAdapter { album ->
            viewModel.openAlbum(album)
        }
        binding.recyclerAlbums.apply {
            layoutManager = GridLayoutManager(this@JellyfinBrowseActivity, 2)
            adapter = albumAdapter
        }

        // Song list - playlist click shows add-to-playlist dialog
        songAdapter = SongAdapter(
            onSongClick = { song -> viewModel.playSong(song) },
            onAddToPlaylistClick = { song -> showAddToPlaylistDialog(song) }
        )
        binding.recyclerSongs.apply {
            layoutManager = LinearLayoutManager(this@JellyfinBrowseActivity)
            adapter = songAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            if (viewModel.uiState.value.isViewingAlbum) {
                viewModel.backToAlbums()
            } else {
                finish()
            }
        }

        binding.btnPlaylists.setOnClickListener {
            showPlaylistsDialog()
        }

        binding.etSearch.addTextChangedListener { text ->
            viewModel.searchSongs(text?.toString() ?: "")
        }

        binding.chipDlnaDevice.setOnClickListener {
            showDlnaDeviceDialog()
        }

        binding.btnPlayPause.setOnClickListener {
            viewModel.togglePlayPause()
        }

        binding.btnStop.setOnClickListener {
            viewModel.stopPlayback()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Loading
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                // Albums
                if (!state.isSearching && state.albums.isNotEmpty()) {
                    binding.recyclerAlbums.visibility = View.VISIBLE
                    binding.recyclerSongs.visibility = View.GONE
                    albumAdapter.submitList(state.albums)
                }

                // Songs
                if (state.songs.isNotEmpty()) {
                    binding.recyclerAlbums.visibility = View.GONE
                    binding.recyclerSongs.visibility = View.VISIBLE
                    songAdapter.submitList(state.songs)
                }

                // Empty state
                val showEmpty = !state.isLoading && state.albums.isEmpty() && state.songs.isEmpty()
                binding.tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE

                // Now playing
                state.currentSong?.let { song ->
                    binding.cardNowPlaying.visibility = View.VISIBLE
                    binding.tvNowPlayingTitle.text = song.title
                    binding.tvNowPlayingArtist.text = song.artist ?: "未知艺术家"
                } ?: run {
                    binding.cardNowPlaying.visibility = View.GONE
                }

                // Play/Pause button
                binding.btnPlayPause.setImageResource(
                    if (state.isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )

                // DLNA device
                state.selectedDlnaDevice?.let { device ->
                    binding.chipDlnaDevice.text = device.deviceName
                } ?: run {
                    binding.chipDlnaDevice.text = "选择设备"
                }

                // Update DLNA dialog when devices change
                if (state.dlnaDevices.size != lastKnownDeviceCount) {
                    lastKnownDeviceCount = state.dlnaDevices.size
                    if (dlnaDialog != null && dlnaDialog!!.isShowing) {
                        updateDlnaDialog(state.dlnaDevices)
                    }
                }

                // Error
                state.error?.let { error ->
                    Toast.makeText(this@JellyfinBrowseActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }

                // Back button for album view
                binding.btnBack.visibility = if (state.isViewingAlbum || state.isSearching) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showDlnaDeviceDialog() {
        val devices = viewModel.uiState.value.dlnaDevices

        // Show dialog immediately, start discovery if no devices
        val deviceNames = if (devices.isEmpty()) {
            arrayOf("正在搜索设备...")
        } else {
            devices.map { it.deviceName }.toTypedArray()
        }

        dlnaDialog?.dismiss()
        dlnaDialog = AlertDialog.Builder(this)
            .setTitle("选择投屏设备")
            .setItems(deviceNames) { _, which ->
                if (devices.isNotEmpty() && which < devices.size) {
                    viewModel.selectDlnaDevice(devices[which])
                }
            }
            .setNeutralButton("刷新") { _, _ ->
                viewModel.discoverDlnaDevices()
            }
            .setNegativeButton("取消", null)
            .create()

        dlnaDialog?.show()

        // Start discovery if no devices
        if (devices.isEmpty()) {
            viewModel.discoverDlnaDevices()
        }
    }

    private fun updateDlnaDialog(devices: List<SessionInfo>) {
        if (devices.isEmpty()) return

        dlnaDialog?.dismiss()
        dlnaDialog = AlertDialog.Builder(this)
            .setTitle("选择投屏设备")
            .setItems(devices.map { it.deviceName }.toTypedArray()) { _, which ->
                if (which < devices.size) {
                    viewModel.selectDlnaDevice(devices[which])
                }
            }
            .setNeutralButton("刷新") { _, _ ->
                viewModel.discoverDlnaDevices()
            }
            .setNegativeButton("取消", null)
            .create()

        dlnaDialog?.show()
    }

    /**
     * 显示播放列表对话框
     */
    private fun showPlaylistsDialog() {
        val playlists = viewModel.playlists.value

        if (playlists.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("播放列表")
                .setMessage("还没有播放列表\n\n从歌曲列表点击\"更多\"来创建")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val playlistNames = playlists.map { playlist ->
            val count = if (playlist.songIds.isEmpty()) 0 else playlist.songIds.split(",").size
            playlist.name + " (" + count + "首)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("播放列表")
            .setItems(playlistNames) { _, which ->
                if (which < playlists.size) {
                    showPlaylistSongsDialog(playlists[which])
                }
            }
            .setNeutralButton("新建") { _, _ ->
                showCreateEmptyPlaylistDialog()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示播放列表中的歌曲对话框
     */
    private fun showPlaylistSongsDialog(playlist: com.voiceassistant.data.local.PlaylistEntity) {
        val intent = Intent(this, PlaylistActivity::class.java).apply {
            putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, playlist.id)
        }
        startActivity(intent)
    }

    /**
     * 显示创建空播放列表对话框
     */
    private fun showCreateEmptyPlaylistDialog() {
        val editText = EditText(this).apply {
            hint = "播放列表名称"
        }

        AlertDialog.Builder(this)
            .setTitle("创建播放列表")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            viewModel.createPlaylist(name)
                            Toast.makeText(
                                this@JellyfinBrowseActivity,
                                "已创建 $name",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@JellyfinBrowseActivity,
                                "创建失败: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        this,
                        "名称不能为空",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示添加到播放列表对话框
     */
    private fun showAddToPlaylistDialog(song: JellyfinSong) {
        val playlists = viewModel.playlists.value

        if (playlists.isEmpty()) {
            // 没有播放列表，直接显示创建对话框
            showCreatePlaylistDialog(song)
            return
        }

        val playlistNames = playlists.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("添加到播放列表")
            .setItems(playlistNames) { _, which ->
                if (which < playlists.size) {
                    viewModel.addToPlaylist(playlists[which].id, song)
                    Toast.makeText(
                        this,
                        "已添加到 ${playlists[which].name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNeutralButton("创建新播放列表") { _, _ ->
                showCreatePlaylistDialog(song)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示创建播放列表对话框
     */
    private fun showCreatePlaylistDialog(song: JellyfinSong) {
        val editText = EditText(this).apply {
            hint = "播放列表名称"
        }

        AlertDialog.Builder(this)
            .setTitle("创建播放列表")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createPlaylistAndAddSong(name, song)
                } else {
                    Toast.makeText(
                        this@JellyfinBrowseActivity,
                        "名称不能为空",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 创建播放列表并添加歌曲
     */
    private fun createPlaylistAndAddSong(playlistName: String, song: JellyfinSong) {
        lifecycleScope.launch {
            try {
                val playlistId = viewModel.createPlaylist(playlistName)
                viewModel.addToPlaylist(playlistId, song)
                Toast.makeText(
                    this@JellyfinBrowseActivity,
                    "已创建 $playlistName 并添加歌曲",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@JellyfinBrowseActivity,
                    "创建播放列表失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
