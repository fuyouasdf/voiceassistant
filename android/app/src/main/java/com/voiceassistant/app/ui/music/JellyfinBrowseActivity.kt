package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.voiceassistant.app.R
import com.voiceassistant.data.remote.JellyfinAlbum
import com.voiceassistant.data.remote.JellyfinSong
import com.voiceassistant.data.remote.SessionInfo
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJellyfinBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupRecyclerViews()
        setupListeners()
        observeState()
    }

    private fun initViews() {
        // Views are accessed through binding
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

        // Song list - provide a no-op for playlist click since we don't have that feature
        songAdapter = SongAdapter(
            onSongClick = { song -> viewModel.playSong(song) },
            onAddToPlaylistClick = { _ -> }
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
}
