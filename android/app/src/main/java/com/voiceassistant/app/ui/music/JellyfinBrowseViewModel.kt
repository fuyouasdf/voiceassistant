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

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.QueueSource
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.SessionInfo
import com.voiceassistant.domain.model.Album
import com.voiceassistant.domain.model.Playlist
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val PREF_SELECTED_DEVICE_ID = "jellyfin_selected_device_id"
private const val PREF_SELECTED_DEVICE_NAME = "jellyfin_selected_device_name"
private const val LOCAL_DEVICE_SESSION_ID = "__local_device_session__"
private const val LOCAL_DEVICE_NAME = "本机"
private const val PREF_REMOTE_PLAYBACK_CHANGED = "remote_playback_changed"

/**
 * Jellyfin 浏览页面状态
 */
data class JellyfinBrowseUiState(
    val isLoading: Boolean = false,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isViewingAlbum: Boolean = false,
    val currentAlbumId: String? = null,
    val currentAlbumName: String = "",
    val error: String? = null,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val dlnaDevices: List<SessionInfo> = emptyList(),
    val selectedDlnaDevice: SessionInfo? = null,
    val isDlnaDiscovering: Boolean = false
)

@HiltViewModel
class JellyfinBrowseViewModel @Inject constructor(
    private val jellyfinClient: JellyfinClient,
    private val musicRepository: MusicRepository,
    private val sharedPreferences: SharedPreferences,
    private val playlistRepository: PlaylistRepository,
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinBrowseUiState())
    val uiState: StateFlow<JellyfinBrowseUiState> = _uiState.asStateFlow()

    // 播放列表
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // 记住上次选择的设备ID
    private val savedDeviceId: String?
        get() = sharedPreferences.getString(PREF_SELECTED_DEVICE_ID, null)

    private val localDevice = SessionInfo(
        id = LOCAL_DEVICE_SESSION_ID,
        deviceName = LOCAL_DEVICE_NAME,
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

    init {
        // 加载专辑列表
        loadAlbums()
        // 刷新DLNA设备列表（从Jellyfin会话获取）
        discoverDlnaDevices()
        // 加载播放列表
        loadPlaylists()
    }

    /**
     * 加载播放列表
     */
    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { list ->
                _playlists.value = list
            }
        }
    }

    /**
     * 加载专辑列表
     */
    fun loadAlbums() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            musicRepository.getAlbums().fold(
                onSuccess = { albums ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        albums = albums,
                        isViewingAlbum = false
                    )
                },
                onFailure = { e ->
                    Timber.e(e, "加载专辑失败")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载专辑失败: ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * 搜索歌曲
     */
    fun searchSongs(query: String) {
        if (query.isBlank()) {
            // 如果搜索框清空，恢复显示专辑
            _uiState.value = _uiState.value.copy(
                searchQuery = "",
                isSearching = false,
                songs = emptyList()
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                searchQuery = query,
                isSearching = true,
                error = null
            )
            musicRepository.searchSongs(query).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        songs = songs,
                        albums = emptyList()
                    )
                },
                onFailure = { e ->
                    Timber.e(e, "搜索失败")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "搜索失败: ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * 点击专辑，加载专辑中的歌曲
     */
    fun openAlbum(album: Album) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            musicRepository.getAlbumSongs(album.id).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        songs = songs,
                        isViewingAlbum = true,
                        currentAlbumId = album.id,
                        currentAlbumName = album.name,
                        albums = emptyList()
                    )
                },
                onFailure = { e ->
                    Timber.e(e, "加载专辑歌曲失败")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载歌曲失败: ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * 返回专辑列表
     */
    fun backToAlbums() {
        _uiState.value = _uiState.value.copy(
            isViewingAlbum = false,
            currentAlbumId = null,
            currentAlbumName = "",
            songs = emptyList()
        )
        loadAlbums()
    }

    /**
     * 播放歌曲到DLNA设备（通过Jellyfin会话控制）
     */
    fun playSong(song: Song) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(currentSong = song)

            val session = _uiState.value.selectedDlnaDevice
            if (session == null) {
                _uiState.value = _uiState.value.copy(error = "请先选择播放设备")
                return@launch
            }

            try {
                if (session.id == LOCAL_DEVICE_SESSION_ID) {
                    val visibleSongs = _uiState.value.songs
                    val playlistSongs = if (visibleSongs.any { it.id == song.id }) visibleSongs else listOf(song)
                    val startIndex = playlistSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    val musicItems = buildMusicItems(playlistSongs)
                    if (musicItems.isEmpty()) {
                        _uiState.value = _uiState.value.copy(error = "播放失败: 当前列表没有可播放歌曲")
                        return@launch
                    }

                    val actualStartIndex = musicItems.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    val currentItem = musicItems.getOrNull(actualStartIndex)
                    Timber.d(
                        "本机播放列表: size=${musicItems.size}, startIndex=$actualStartIndex, songId=${song.id}, playMethod=${currentItem?.streamPlayMethod}, container=${currentItem?.streamContainer}, transcoding=${currentItem?.isTranscoding}"
                    )
                    musicPlayer.playPlaylist(musicItems, actualStartIndex.takeIf { it < musicItems.size } ?: startIndex, QueueSource.BROWSER)
                    _uiState.value = _uiState.value.copy(isPlaying = true)
                } else {
                    // 切到远程设备前，彻底停止本机出声；本机队列会保留，切回本机时可重新 resume
                    stopLocalPlaybackIfActive()
                    val latestSession = syncRemoteSessionState(session.id) ?: session
                    if (!latestSession.supportsCommand("PlayMediaSource")) {
                        _uiState.value = _uiState.value.copy(
                            error = "设备 ${latestSession.deviceName} 不支持远程发起播放"
                        )
                        return@launch
                    }
                    // 保持与 d4590d71 一致：只通过 Jellyfin Session API playItem 发起远程播放
                    val result = musicRepository.playItem(session.id, song.id)
                    if (result.isFailure) {
                        _uiState.value = _uiState.value.copy(
                            error = "播放失败: ${result.exceptionOrNull()?.message}"
                        )
                    } else {
                        val syncedSession = syncRemoteSessionState(session.id)
                        // 优先检查 nowPlayingItem 是否匹配
                        val matchedItem = syncedSession?.nowPlayingItem?.id == song.id
                        // 检查播放状态（isPaused == false 表示正在播放）
                        val actualPlaying = syncedSession?.playbackState?.isPaused?.not() ?: true
                        // 如果 nowPlayingItem 匹配，或者播放状态显示正在播放，则认为成功
                        // DLNA 设备播放需要更多时间同步状态，不要求 nowPlayingItem 必须立即匹配
                        if (matchedItem || actualPlaying) {
                            _uiState.value = _uiState.value.copy(isPlaying = actualPlaying)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                error = "设备 ${latestSession.deviceName} 未确认开始播放"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "播放失败")
                _uiState.value = _uiState.value.copy(error = "播放失败: ${e.message}")
            }
        }
    }

    private suspend fun buildMusicItems(songs: List<Song>): List<MusicItem> {
        return songs.mapNotNull { listSong ->
            musicRepository.getStreamInfo(listSong.id).fold(
                onSuccess = { streamInfo ->
                    if (streamInfo.url.isBlank()) {
                        Timber.w("跳过无可用播放地址的歌曲: songId=${listSong.id}")
                        null
                    } else {
                        MusicItem(
                            id = listSong.id,
                            title = listSong.title,
                            artist = listSong.artist,
                            album = listSong.album,
                            duration = listSong.duration,
                            streamUrl = streamInfo.url,
                            coverUrl = listSong.coverUrl,
                            playbackSessionId = streamInfo.playSessionId,
                            mediaSourceId = streamInfo.mediaSourceId,
                            streamContainer = streamInfo.container,
                            streamPlayMethod = streamInfo.playMethod,
                            isTranscoding = streamInfo.isTranscoding
                        )
                    }
                },
                onFailure = { e ->
                    Timber.w(e, "构建本机播放列表失败，跳过歌曲: songId=${listSong.id}")
                    null
                }
            )
        }
    }

    /**
     * 播放/暂停
     */
    fun togglePlayPause() {
        viewModelScope.launch {
            val session = _uiState.value.selectedDlnaDevice ?: return@launch
            try {
                if (session.id == LOCAL_DEVICE_SESSION_ID) {
                    if (_uiState.value.isPlaying) {
                        musicPlayer.pause()
                        _uiState.value = _uiState.value.copy(isPlaying = false)
                    } else {
                        musicPlayer.resume()
                        _uiState.value = _uiState.value.copy(isPlaying = true)
                    }
                } else {
                    // 远程 DLNA 播放控制 - 直接执行命令，不检查 supportsCommand
                    // 因为 Jellyfin 报告的 DLNA 设备能力可能不完整（Pause/Unpause 通常支持但未报告）
                    if (_uiState.value.isPlaying) {
                        val result = executeRemotePlaybackCommand(
                            sessionId = session.id,
                            desiredPlaying = false
                        ) {
                            musicRepository.pause(session.id)
                        }
                        if (result.isFailure) {
                            _uiState.value = _uiState.value.copy(error = "播放控制失败: ${result.exceptionOrNull()?.message}")
                        }
                    } else {
                        val result = executeRemotePlaybackCommand(
                            sessionId = session.id,
                            desiredPlaying = true
                        ) {
                            musicRepository.unpause(session.id)
                        }
                        if (result.isFailure) {
                            _uiState.value = _uiState.value.copy(error = "播放控制失败: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "播放控制失败")
                _uiState.value = _uiState.value.copy(error = "播放控制失败: ${e.message}")
            }
        }
    }

    /**
     * 停止播放
     */
    fun stopPlayback() {
        viewModelScope.launch {
            val session = _uiState.value.selectedDlnaDevice ?: return@launch
            try {
                if (session.id == LOCAL_DEVICE_SESSION_ID) {
                    musicPlayer.stop()
                } else {
                    val latestSession = syncRemoteSessionState(session.id) ?: session
                    if (!latestSession.supportsCommand("Stop")) {
                        _uiState.value = _uiState.value.copy(
                            error = "设备 ${latestSession.deviceName} 不支持停止"
                        )
                        return@launch
                    }
                    musicRepository.stop(session.id)
                }
                _uiState.value = _uiState.value.copy(currentSong = null, isPlaying = false)
            } catch (e: Exception) {
                Timber.e(e, "停止播放失败")
                _uiState.value = _uiState.value.copy(error = "停止播放失败: ${e.message}")
            }
        }
    }

    /**
     * 选择投屏设备
     */
    fun selectDlnaDevice(device: SessionInfo) {
        viewModelScope.launch {
            val previousDevice = _uiState.value.selectedDlnaDevice
            val switchedFromLocalToRemote =
                previousDevice?.id == LOCAL_DEVICE_SESSION_ID && device.id != LOCAL_DEVICE_SESSION_ID

            if (switchedFromLocalToRemote) {
                stopLocalPlaybackIfActive()
            }

            val nextIsPlaying = if (device.id == LOCAL_DEVICE_SESSION_ID) {
                musicPlayer.getState().isPlaying
            } else {
                // Only consider playing if: 1) nowPlayingItem exists (not stopped), 2) not paused
                device.nowPlayingItem != null && (device.playbackState?.isPaused?.not() ?: false)
            }

            _uiState.value = _uiState.value.copy(
                selectedDlnaDevice = device,
                isPlaying = nextIsPlaying
            )
            persistSelectedDevice(device)

            // 同步远程设备状态以获取最新音量等信息
            if (device.id != LOCAL_DEVICE_SESSION_ID) {
                syncRemoteSessionState(device.id)
            }
        }
    }

    private fun persistSelectedDevice(device: SessionInfo) {
        sharedPreferences.edit()
            .putString(PREF_SELECTED_DEVICE_ID, device.id)
            .putString(PREF_SELECTED_DEVICE_NAME, device.deviceName)
            .apply()
        Timber.d("已保存投屏设备: ${device.deviceName} (${device.id})")
    }

    private fun stopLocalPlaybackIfActive() {
        val localState = musicPlayer.getState()
        val hasLocalPlaybackContext =
            localState.isPlaying || localState.playlist.isNotEmpty() || localState.currentIndex >= 0
        if (hasLocalPlaybackContext) {
            Timber.d("停止本机播放以切换到远程设备")
            musicPlayer.stop()
        }
    }

    private suspend fun executeRemotePlaybackCommand(
        sessionId: String,
        desiredPlaying: Boolean,
        command: suspend () -> Result<Boolean>
    ): Result<Boolean> {
        val commandResult = command()

        // Give DLNA device time to process the command before syncing state
        // Without this delay, syncRemoteSessionState may get stale state
        kotlinx.coroutines.delay(300)

        val syncedSession = syncRemoteSessionState(sessionId)
        val actualPlaying = syncedSession?.playbackState?.isPaused?.not()

        if (commandResult.isSuccess) {
            if (actualPlaying != null) {
                _uiState.value = _uiState.value.copy(isPlaying = actualPlaying)
            } else {
                _uiState.value = _uiState.value.copy(isPlaying = desiredPlaying)
            }
            return commandResult
        }

        if (actualPlaying == desiredPlaying) {
            Timber.w(
                "远程播放命令返回失败，但会话状态已符合预期: sessionId=$sessionId, desiredPlaying=$desiredPlaying, error=${commandResult.exceptionOrNull()?.message}"
            )
            _uiState.value = _uiState.value.copy(isPlaying = actualPlaying)
            return Result.success(true)
        }

        return commandResult
    }

    private suspend fun syncRemoteSessionState(sessionId: String): SessionInfo? {
        return try {
            // Check if remote playback was changed by voice command (IntentExecutor)
            // If so, we need to force a sync to get the latest state
            val remotePlaybackChanged = sharedPreferences.getBoolean(PREF_REMOTE_PLAYBACK_CHANGED, false)

            val sessions = jellyfinClient.getSessions()
            val castableDevices = sessions.filter { it.supportsMediaControl && it.isActive }
            val allDevices = listOf(localDevice) + castableDevices
            val syncedSession = castableDevices.find { it.id == sessionId }

            val selectedDevice = when {
                _uiState.value.selectedDlnaDevice?.id == sessionId && syncedSession != null -> syncedSession
                else -> _uiState.value.selectedDlnaDevice
            }

            // Calculate isPlaying: nowPlayingItem must exist AND not paused
            val isCurrentlyPlaying = syncedSession?.nowPlayingItem != null &&
                (syncedSession.playbackState?.isPaused?.not() ?: false)

            _uiState.value = _uiState.value.copy(
                dlnaDevices = allDevices,
                selectedDlnaDevice = selectedDevice,
                isPlaying = if (remotePlaybackChanged) isCurrentlyPlaying else (_uiState.value.selectedDlnaDevice?.id != sessionId && isCurrentlyPlaying)
            )

            // Clear the flag after syncing
            if (remotePlaybackChanged) {
                sharedPreferences.edit().putBoolean(PREF_REMOTE_PLAYBACK_CHANGED, false).apply()
            }

            syncedSession
        } catch (e: Exception) {
            Timber.w(e, "同步远程会话状态失败: sessionId=$sessionId")
            null
        }
    }

    /**
     * 刷新DLNA设备列表（从Jellyfin会话获取）
     */
    fun discoverDlnaDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDlnaDiscovering = true)
            try {
                refreshDlnaDevices()
            } finally {
                _uiState.value = _uiState.value.copy(isDlnaDiscovering = false)
            }
        }
    }

    /**
     * 从Jellyfin会话刷新可投屏设备列表
     */
    private suspend fun refreshDlnaDevices() {
        try {
            val sessions = jellyfinClient.getSessions()
            // 过滤出支持媒体控制且活跃的会话（这些就是可投屏设备）
            val castableDevices = sessions.filter { it.supportsMediaControl && it.isActive }
            val allDevices = listOf(localDevice) + castableDevices
            Timber.d("发现 ${castableDevices.size} 个可投屏设备，本机设备已加入列表")

            val currentSelectionId = _uiState.value.selectedDlnaDevice?.id
            val currentSelectedDevice = currentSelectionId?.let { currentId ->
                allDevices.find { it.id == currentId }
            }
            val savedDevice = savedDeviceId?.let { savedId ->
                allDevices.find { it.id == savedId }
            }
            val selectedDevice = currentSelectedDevice
                ?: savedDevice
                ?: localDevice

            _uiState.value = _uiState.value.copy(
                dlnaDevices = allDevices,
                selectedDlnaDevice = selectedDevice
            )

            when {
                currentSelectedDevice != null -> {
                    Timber.d("保留当前选择的设备: ${currentSelectedDevice.deviceName}")
                }
                savedDevice != null -> {
                    Timber.d("已恢复上次选择的设备: ${savedDevice.deviceName}")
                }
            }

            // 同步远程设备的音量等信息
            if (selectedDevice.id != LOCAL_DEVICE_SESSION_ID) {
                syncRemoteSessionState(selectedDevice.id)
            }
        } catch (e: Exception) {
            Timber.e(e, "刷新设备列表失败")
            _uiState.value = _uiState.value.copy(error = "刷新设备列表失败: ${e.message}")
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 格式化时长
     */
    fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "%d:%02d".format(min, sec)
    }

    /**
     * 添加歌曲到播放列表
     */
    fun addToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            musicRepository.getStreamUrl(song.id).fold(
                onSuccess = { streamUrl ->
                    Timber.d("addToPlaylist: song.id=${song.id}, streamUrl=$streamUrl")
                    val domainSong = Song(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        duration = song.duration,
                        url = streamUrl,
                        coverUrl = song.coverUrl
                    )
                    playlistRepository.addSongToPlaylist(playlistId, domainSong)
                },
                onFailure = { e ->
                    Timber.e(e, "添加到播放列表失败")
                    _uiState.value = _uiState.value.copy(error = "添加到播放列表失败: ${e.message}")
                }
            )
        }
    }

    /**
     * 创建播放列表
     */
    suspend fun createPlaylist(name: String): Long {
        return playlistRepository.createPlaylist(name)
    }
}
