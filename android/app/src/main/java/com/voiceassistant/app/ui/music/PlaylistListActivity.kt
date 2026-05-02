/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.app.ui.music

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.voiceassistant.app.databinding.ActivityPlaylistListBinding
import com.voiceassistant.app.ui.playback.MiniPlayerFragment
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.SessionInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LOCAL_DEVICE_SESSION_ID = "__local_device_session__"
private const val PREF_SELECTED_DEVICE_ID = "jellyfin_selected_device_id"

@AndroidEntryPoint
class PlaylistListActivity : AppCompatActivity(), MiniPlayerFragment.OnMiniPlayerClickListener {

    private val viewModel: PlaylistListViewModel by viewModels()
    private val playbackViewModel: PlaylistViewModel by viewModels()
    private lateinit var binding: ActivityPlaylistListBinding
    private lateinit var playlistAdapter: PlaylistListAdapter
    private var dlnaDialog: AlertDialog? = null

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var jellyfinClient: JellyfinClient

    override fun onMiniPlayerClicked() {
        startActivity(Intent(this, NowPlayingActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        // 同步 DLNA 会话状态
        playbackViewModel.syncDlnaSessionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupMiniPlayer(savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeState()
        observePlayback()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerPlaylists) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPadding = systemBars.bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomPadding)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.miniPlayerContainer) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom
            view.layoutParams = params
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.cardNowPlaying) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + resources.getDimensionPixelSize(R.dimen.spacing_lg)
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
        playlistAdapter = PlaylistListAdapter { item ->
            startActivity(
                Intent(this, PlaylistActivity::class.java).apply {
                    putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, item.playlist.id)
                }
            )
        }
        binding.recyclerPlaylists.apply {
            layoutManager = LinearLayoutManager(this@PlaylistListActivity)
            adapter = playlistAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCreatePlaylist.setOnClickListener { showCreatePlaylistDialog() }

        binding.chipDlnaDevice.setOnClickListener {
            showDlnaDeviceDialog()
        }

        binding.btnPlayPauseMini.setOnClickListener {
            playbackViewModel.togglePlayPause()
        }

        binding.btnPreviousMini.setOnClickListener {
            // DLNA 不支持上一首
        }

        binding.btnNextMini.setOnClickListener {
            // DLNA 不支持下一首
        }

        binding.btnStopMini.setOnClickListener {
            playbackViewModel.stopPlayback()
        }
    }

    private fun showDlnaDeviceDialog() {
        lifecycleScope.launch {
            val devices = jellyfinClient.getSessions()
                .filter { it.supportsMediaControl && it.isActive }

            val localDevice = SessionInfo(
                id = LOCAL_DEVICE_SESSION_ID,
                deviceName = "本机",
                deviceId = LOCAL_DEVICE_SESSION_ID,
                client = "VoiceAssistant",
                userName = null,
                userId = null,
                isActive = true,
                supportsMediaControl = true,
                supportedCommands = listOf("Pause", "Unpause", "Stop", "Seek", "NextTrack", "PreviousTrack"),
                playbackState = null,
                nowPlayingItem = null
            )
            val allDevices = listOf(localDevice) + devices

            val deviceNames = allDevices.map { it.deviceName }.toTypedArray()
            dlnaDialog?.dismiss()
            dlnaDialog = AlertDialog.Builder(this@PlaylistListActivity)
                .setTitle("选择播放设备")
                .setItems(deviceNames) { _, which ->
                    if (which < allDevices.size) {
                        val selectedDevice = allDevices[which]
                        playbackViewModel.selectDlnaDevice(selectedDevice)
                        Toast.makeText(
                            this@PlaylistListActivity,
                            if (selectedDevice.id == LOCAL_DEVICE_SESSION_ID) "已切换到本机播放" else "已切换到 ${selectedDevice.deviceName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                playlistAdapter.submitList(state.playlists)

                val isEmpty = !state.isLoading && state.playlists.isEmpty()
                binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerPlaylists.visibility = if (isEmpty) View.GONE else View.VISIBLE
                binding.tvPlaylistCount.text = "${state.playlists.size} 个播放列表"

                state.error?.let { error ->
                    Toast.makeText(this@PlaylistListActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun observePlayback() {
        lifecycleScope.launch {
            playbackViewModel.uiState.collectLatest { state ->
                val deviceId = state.selectedDlnaDevice?.id
                val isLocalDevice = deviceId == null || deviceId == LOCAL_DEVICE_SESSION_ID

                // 更新 chip 显示
                binding.chipDlnaDevice.text = state.selectedDlnaDevice?.deviceName ?: "本机"

                if (isLocalDevice) {
                    binding.miniPlayerContainer.visibility = View.VISIBLE
                    binding.cardNowPlaying.visibility = View.GONE
                } else {
                    binding.miniPlayerContainer.visibility = View.GONE
                    binding.cardNowPlaying.visibility = View.VISIBLE
                    updateDlnaNowPlaying(state)
                }
            }
        }
    }

    private fun updateDlnaNowPlaying(state: PlaylistUiState) {
        val session = state.selectedDlnaDevice
        val isPlaying = state.isPlaying
        val nowPlayingItem = session?.nowPlayingItem

        val playbackStateText = if (isPlaying) getString(R.string.state_playing) else getString(R.string.state_paused)

        if (nowPlayingItem != null) {
            binding.tvNowPlayingTitle.text = nowPlayingItem.name ?: getString(R.string.unknown)
            val artist = nowPlayingItem.artists.firstOrNull() ?: getString(R.string.artist_unknown)
            binding.tvNowPlayingArtist.text = "$playbackStateText · $artist"
            binding.progressNowPlaying.progress = 0
            // 显示时长（DLNA 无法获取实时进度，进度始终为 0）
            val durationSeconds = if (nowPlayingItem.durationTicks > 0) {
                (nowPlayingItem.durationTicks / 10000000).toInt()
            } else {
                0
            }
            val totalTime = if (durationSeconds > 0) formatTime(durationSeconds * 1000L) else "--:--"
            binding.tvNowPlayingTime.text = "00:00 / $totalTime"
        } else if (isPlaying) {
            binding.tvNowPlayingTitle.text = getString(R.string.state_playing)
            binding.tvNowPlayingArtist.text = getString(R.string.casting_to_device, session?.deviceName ?: "")
            binding.progressNowPlaying.progress = 0
            binding.tvNowPlayingTime.text = "--:-- / --:--"
        } else {
            binding.tvNowPlayingTitle.text = getString(R.string.waiting_for_playback)
            binding.tvNowPlayingArtist.text = getString(R.string.casting_to_device, session?.deviceName ?: "")
            binding.progressNowPlaying.progress = 0
            binding.tvNowPlayingTime.text = "--:-- / --:--"
        }
        binding.tvNowPlayingStatus.text = getString(R.string.casting_to_device, session?.deviceName ?: "")

        binding.btnPlayPauseMini.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        binding.btnPreviousMini.isEnabled = false
        binding.btnNextMini.isEnabled = false
    }

    private fun formatTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun showCreatePlaylistDialog() {
        val editText = EditText(this).apply {
            hint = "播放列表名称"
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle("新建播放列表")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.createPlaylist(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
