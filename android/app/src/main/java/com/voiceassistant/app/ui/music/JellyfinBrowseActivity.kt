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
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.data.remote.JellyfinAlbum
import com.voiceassistant.data.remote.SessionInfo
import com.voiceassistant.domain.model.Playlist
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.PlaylistRepository
import com.voiceassistant.app.databinding.ActivityJellyfinBrowseBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Jellyfin 浏览页面
 * 用于浏览专辑、搜索歌曲、播放到本机或DLNA设备
 */
@AndroidEntryPoint
class JellyfinBrowseActivity : AppCompatActivity() {
    companion object {
        private const val LOCAL_DEVICE_SESSION_ID = "__local_device_session__"
    }

    @javax.inject.Inject
    lateinit var musicPlayer: MusicPlayer

    private val viewModel: JellyfinBrowseViewModel by viewModels()
    private lateinit var binding: ActivityJellyfinBrowseBinding

    // Adapters
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var songAdapter: SongAdapter
    private var lastKnownSong: Song? = null

    // Device Dialog
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

        // Card now playing needs to adjust for both system bars and IME (keyboard)
        ViewCompat.setOnApplyWindowInsetsListener(binding.cardNowPlaying) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val params = view.layoutParams as LinearLayout.LayoutParams
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

    private fun setupRecyclerViews() {
        // Album grid - 2 columns
        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                viewModel.openAlbum(album)
            }
        )
        binding.recyclerAlbums.apply {
            layoutManager = GridLayoutManager(this@JellyfinBrowseActivity, 2)
            adapter = albumAdapter
        }

        // Song list - playlist click shows add-to-playlist dialog
        songAdapter = SongAdapter(
            onSongClick = { song ->
                viewModel.playSong(song)
            },
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
        binding.btnPreviousMini.setOnClickListener {
            musicPlayer.playPrevious()
        }
        binding.btnNextMini.setOnClickListener {
            musicPlayer.playNext()
        }

        binding.btnStop.setOnClickListener {
            viewModel.stopPlayback()
            lastKnownSong = null
        }

        binding.cardNowPlaying.setOnClickListener {
            openNowPlayingIfLocalSelected()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Loading indicator
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                // Content display (albums/songs)
                updateContentUi(state)

                // Now playing card
                updateNowPlayingCard(state)

                // Playback controls
                updatePlaybackControls(state)

                // Device selection UI
                updateDeviceListUi(state)

                // Error handling
                state.error?.let { error ->
                    Toast.makeText(this@JellyfinBrowseActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }

                // Back button visibility
                binding.btnBack.visibility = if (state.isViewingAlbum || state.isSearching) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            musicPlayer.state.collectLatest { playerState ->
                val selectedDevice = viewModel.uiState.value.selectedDlnaDevice
                if (selectedDevice?.id != LOCAL_DEVICE_SESSION_ID) {
                    return@collectLatest
                }
                val currentItem = playerState.playlist.getOrNull(playerState.currentIndex)
                if (currentItem != null) {
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
                    binding.tvNowPlayingTitle.text = getString(R.string.no_playback_content)
                    binding.tvNowPlayingArtist.text = selectedDevice.deviceName
                    binding.tvNowPlayingStatus.text = getString(R.string.no_playback_content)
                    binding.progressNowPlaying.progress = 0
                    binding.tvNowPlayingTime.text = "00:00 / 00:00"
                    binding.btnPreviousMini.isEnabled = false
                    binding.btnNextMini.isEnabled = false
                }
            }
        }
    }

    /**
     * Update content display (albums/songs grid)
     */
    private fun updateContentUi(state: JellyfinBrowseUiState) {
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
    }

    /**
     * Update now playing card UI
     */
    private fun updateNowPlayingCard(state: JellyfinBrowseUiState) {
        val displaySong = state.currentSong ?: lastKnownSong
        state.currentSong?.let { currentSong ->
            lastKnownSong = currentSong
        }
        val localPlayerState = musicPlayer.getState()
        val localCurrentItem = if (state.selectedDlnaDevice?.id == LOCAL_DEVICE_SESSION_ID) {
            localPlayerState.playlist.getOrNull(localPlayerState.currentIndex)
        } else {
            null
        }

        val hasPlaybackContext =
            localCurrentItem != null || displaySong != null || state.selectedDlnaDevice != null
        binding.cardNowPlaying.visibility = if (hasPlaybackContext) View.VISIBLE else View.GONE

        localCurrentItem?.let { item ->
            binding.tvNowPlayingTitle.text = item.title
            val playbackState = if (localPlayerState.isPlaying) getString(R.string.state_playing) else getString(R.string.state_paused)
            val artist = item.artist ?: getString(R.string.artist_unknown)
            binding.tvNowPlayingArtist.text = "$playbackState · $artist"
            renderMiniPlayerForSelectedDevice(state)
        } ?: displaySong?.let { song ->
            binding.tvNowPlayingTitle.text = song.title
            val playbackState = if (state.isPlaying) getString(R.string.state_playing) else getString(R.string.state_paused)
            val artist = song.artist ?: getString(R.string.artist_unknown)
            binding.tvNowPlayingArtist.text = "$playbackState · $artist"
            renderMiniPlayerForSelectedDevice(state)
        } ?: run {
            binding.tvNowPlayingTitle.text = getString(R.string.no_playback_content)
            binding.tvNowPlayingArtist.text = state.selectedDlnaDevice?.deviceName ?: getString(R.string.select_playback_device)
            renderMiniPlayerForSelectedDevice(state)
        }
    }

    /**
     * Update playback control buttons
     */
    private fun updatePlaybackControls(state: JellyfinBrowseUiState) {
        binding.btnPlayPause.setImageResource(
            if (state.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    /**
     * Update device selection UI and sync device dialog
     */
    private fun updateDeviceListUi(state: JellyfinBrowseUiState) {
        // Playback device chip
        state.selectedDlnaDevice?.let { device ->
            binding.chipDlnaDevice.text = device.deviceName
        } ?: run {
            binding.chipDlnaDevice.text = getString(R.string.select_device)
        }

        // Update device dialog when device list changes
        if (state.dlnaDevices.size != lastKnownDeviceCount) {
            lastKnownDeviceCount = state.dlnaDevices.size
            if (dlnaDialog != null && dlnaDialog!!.isShowing) {
                updateDlnaDialog(state.dlnaDevices)
            }
        }
    }

    private fun renderMiniPlayerForSelectedDevice(state: JellyfinBrowseUiState) {
        val selectedDevice = state.selectedDlnaDevice
        if (selectedDevice == null) {
            binding.tvNowPlayingStatus.text = getString(R.string.select_playback_device)
            binding.progressNowPlaying.progress = 0
            binding.tvNowPlayingTime.text = "00:00 / 00:00"
            binding.btnPreviousMini.isEnabled = false
            binding.btnNextMini.isEnabled = false
            return
        }

        if (selectedDevice.id == LOCAL_DEVICE_SESSION_ID) {
            return
        }

        val remoteState = if (state.isPlaying) getString(R.string.casting_to_device, selectedDevice.deviceName) else getString(R.string.connected_to_device, selectedDevice.deviceName)
        binding.tvNowPlayingStatus.text = remoteState
        binding.progressNowPlaying.progress = 0
        binding.tvNowPlayingTime.text = "--:-- / --:--"
        binding.btnPreviousMini.isEnabled = false
        binding.btnNextMini.isEnabled = false
    }

    private fun showDlnaDeviceDialog() {
        val devices = viewModel.uiState.value.dlnaDevices
        val deviceNames = devices.map { it.deviceName }.toTypedArray()

        dlnaDialog?.dismiss()
        dlnaDialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_device)
            .setItems(deviceNames) { _, which ->
                if (which < devices.size) {
                    viewModel.selectDlnaDevice(devices[which])
                }
            }
            .setNeutralButton(R.string.refresh) { _, _ ->
                viewModel.discoverDlnaDevices()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dlnaDialog?.show()

        viewModel.discoverDlnaDevices()
    }

    private fun updateDlnaDialog(devices: List<SessionInfo>) {
        if (devices.isEmpty()) return

        dlnaDialog?.dismiss()
        dlnaDialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_device)
            .setItems(devices.map { it.deviceName }.toTypedArray()) { _, which ->
                if (which < devices.size) {
                    viewModel.selectDlnaDevice(devices[which])
                }
            }
            .setNeutralButton(R.string.refresh) { _, _ ->
                viewModel.discoverDlnaDevices()
            }
            .setNegativeButton(R.string.btn_cancel, null)
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
                .setTitle(R.string.playlist_empty_title)
                .setMessage(R.string.playlist_empty_message)
                .setPositiveButton(R.string.btn_ok, null)
                .show()
            return
        }

        val playlistNames = playlists.map { playlist ->
            val count = if (playlist.songIds.isEmpty()) 0 else playlist.songIds.split(",").size
            getString(R.string.playlist_song_count, playlist.name, count)
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_empty_title)
            .setItems(playlistNames) { _, which ->
                if (which < playlists.size) {
                    showPlaylistSongsDialog(playlists[which])
                }
            }
            .setNeutralButton(R.string.btn_new) { _, _ ->
                showCreateEmptyPlaylistDialog()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /**
     * 显示播放列表中的歌曲对话框
     */
    private fun showPlaylistSongsDialog(playlist: Playlist) {
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
            hint = getString(R.string.playlist_name_hint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_playlist)
            .setView(editText)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            viewModel.createPlaylist(name)
                            Toast.makeText(
                                this@JellyfinBrowseActivity,
                                getString(R.string.playlist_created, name),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@JellyfinBrowseActivity,
                                getString(R.string.playlist_create_failed, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        this,
                        R.string.playlist_name_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /**
     * 显示添加到播放列表对话框
     */
    private fun showAddToPlaylistDialog(song: Song) {
        val playlists = viewModel.playlists.value

        if (playlists.isEmpty()) {
            // 没有播放列表，直接显示创建对话框
            showCreatePlaylistDialog(song)
            return
        }

        val playlistNames = playlists.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.add_to_playlist)
            .setItems(playlistNames) { _, which ->
                if (which < playlists.size) {
                    viewModel.addToPlaylist(playlists[which].id, song)
                    Toast.makeText(
                        this,
                        getString(R.string.added_to_playlist, playlists[which].name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNeutralButton(R.string.create_new_playlist) { _, _ ->
                showCreatePlaylistDialog(song)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /**
     * 显示创建播放列表对话框
     */
    private fun showCreatePlaylistDialog(song: Song) {
        val editText = EditText(this).apply {
            hint = getString(R.string.playlist_name_hint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_playlist)
            .setView(editText)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createPlaylistAndAddSong(name, song)
                } else {
                    Toast.makeText(
                        this@JellyfinBrowseActivity,
                        R.string.playlist_name_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /**
     * 创建播放列表并添加歌曲
     */
    private fun createPlaylistAndAddSong(playlistName: String, song: Song) {
        lifecycleScope.launch {
            try {
                val playlistId = viewModel.createPlaylist(playlistName)
                viewModel.addToPlaylist(playlistId, song)
                Toast.makeText(
                    this@JellyfinBrowseActivity,
                    getString(R.string.playlist_created_with_song, playlistName),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@JellyfinBrowseActivity,
                    getString(R.string.playlist_create_failed_with_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openNowPlayingIfLocalSelected() {
        val selectedDevice = viewModel.uiState.value.selectedDlnaDevice ?: return
        if (selectedDevice.id != LOCAL_DEVICE_SESSION_ID) return
        startActivity(Intent(this, NowPlayingActivity::class.java))
    }

    private fun buildMiniPlayerStatus(currentItem: com.voiceassistant.core.music.MusicItem): String {
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

    override fun onDestroy() {
        dlnaDialog?.dismiss()
        dlnaDialog = null
        super.onDestroy()
    }
}
