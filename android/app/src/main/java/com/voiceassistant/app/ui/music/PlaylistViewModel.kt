package com.voiceassistant.app.ui.music

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.QueueSource
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.SessionInfo
import com.voiceassistant.domain.model.Playlist
import com.voiceassistant.domain.model.PlaylistSong
import com.voiceassistant.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val PREF_SELECTED_DEVICE_ID = "jellyfin_selected_device_id"
private const val LOCAL_DEVICE_SESSION_ID = "__local_device_session__"
private const val LOCAL_DEVICE_NAME = "本机"

/**
 * 播放列表页面状态
 */
data class PlaylistUiState(
    val isLoading: Boolean = false,
    val playlist: Playlist? = null,
    val songs: List<PlaylistSong> = emptyList(),
    val error: String? = null,
    val dlnaDevices: List<SessionInfo> = emptyList(),
    val selectedDlnaDevice: SessionInfo? = null,
    val isPlaying: Boolean = false,
    val currentSong: PlaylistSong? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val jellyfinClient: JellyfinClient,
    private val musicPlayer: MusicPlayer,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private var playlistId: Long = -1L

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

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
        // 加载保存的设备选择
        loadSavedDevice()
        // 刷新设备列表
        refreshDevices()
    }

    private fun loadSavedDevice() {
        val savedDeviceId = sharedPreferences.getString(PREF_SELECTED_DEVICE_ID, null)
        if (savedDeviceId != null) {
            // 如果保存的是远程设备ID，需要同步状态
            viewModelScope.launch {
                syncRemoteSessionState(savedDeviceId)
            }
        } else {
            // 默认选择本机
            _uiState.value = _uiState.value.copy(selectedDlnaDevice = localDevice)
        }
    }

    private suspend fun syncRemoteSessionState(sessionId: String): SessionInfo? {
        return try {
            val sessions = jellyfinClient.getSessions()
            val castableDevices = sessions.filter { it.supportsMediaControl && it.isActive }
            val allDevices = listOf(localDevice) + castableDevices
            val syncedSession = castableDevices.find { it.id == sessionId }

            val selectedDevice = when {
                _uiState.value.selectedDlnaDevice?.id == sessionId && syncedSession != null -> syncedSession
                else -> _uiState.value.selectedDlnaDevice
            }

            _uiState.value = _uiState.value.copy(
                selectedDlnaDevice = selectedDevice,
                isPlaying = syncedSession?.playbackState?.isPaused?.not() ?: _uiState.value.isPlaying
            )

            syncedSession
        } catch (e: Exception) {
            Timber.w(e, "同步远程会话状态失败: sessionId=$sessionId")
            null
        }
    }

    private fun refreshDevices() {
        viewModelScope.launch {
            try {
                val sessions = jellyfinClient.getSessions()
                val castableDevices = sessions.filter { it.supportsMediaControl && it.isActive }
                val allDevices = listOf(localDevice) + castableDevices

                val savedDeviceId = sharedPreferences.getString(PREF_SELECTED_DEVICE_ID, null)
                val currentSelectionId = _uiState.value.selectedDlnaDevice?.id
                val savedDevice = savedDeviceId?.let { savedId ->
                    allDevices.find { it.id == savedId }
                }
                val currentSelectedDevice = currentSelectionId?.let { currentId ->
                    allDevices.find { it.id == currentId }
                }
                val selectedDevice = currentSelectedDevice ?: savedDevice ?: localDevice

                _uiState.value = _uiState.value.copy(
                    dlnaDevices = allDevices,
                    selectedDlnaDevice = selectedDevice
                )
            } catch (e: Exception) {
                Timber.e(e, "刷新设备列表失败")
                // 出错时使用本机
                _uiState.value = _uiState.value.copy(
                    dlnaDevices = listOf(localDevice),
                    selectedDlnaDevice = localDevice
                )
            }
        }
    }

    /**
     * 选择播放设备
     */
    fun selectDlnaDevice(device: SessionInfo) {
        val previousDevice = _uiState.value.selectedDlnaDevice
        val switchedFromLocalToRemote =
            previousDevice?.id == LOCAL_DEVICE_SESSION_ID && device.id != LOCAL_DEVICE_SESSION_ID

        if (switchedFromLocalToRemote) {
            stopLocalPlaybackIfActive()
        }

        val nextIsPlaying = if (device.id == LOCAL_DEVICE_SESSION_ID) {
            musicPlayer.getState().isPlaying
        } else {
            device.playbackState?.isPaused?.not() ?: false
        }

        _uiState.value = _uiState.value.copy(
            selectedDlnaDevice = device,
            isPlaying = nextIsPlaying
        )

        // 保存选择
        sharedPreferences.edit()
            .putString(PREF_SELECTED_DEVICE_ID, device.id)
            .apply()
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

    /**
     * 设置播放列表 ID 并加载
     */
    fun setPlaylistId(id: Long) {
        playlistId = id
        loadPlaylist()
    }

    /**
     * 加载播放列表
     */
    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val playlist = playlistRepository.getPlaylistById(playlistId)
                if (playlist != null) {
                    val songs = playlistRepository.getPlaylistSongs(playlist)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        playlist = playlist,
                        songs = songs
                    )
                    warmupPlaybackInfo(songs)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "播放列表不存在"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "加载播放列表失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    private fun warmupPlaybackInfo(songs: List<PlaylistSong>) {
        val songIds = songs.asSequence()
            .map { it.songId }
            .filter { it.isNotBlank() }
            .take(3)
            .toList()
        if (songIds.isEmpty()) return

        viewModelScope.launch {
            try {
                jellyfinClient.prefetchPlaybackInfo(songIds)
            } catch (e: Exception) {
                Timber.w(e, "播放列表预热 playback info 失败")
            }
        }
    }

    /**
     * 从播放列表移除歌曲
     */
    fun removeSong(songId: String) {
        viewModelScope.launch {
            try {
                playlistRepository.removeSongFromPlaylist(playlistId, songId)
                // 重新加载
                loadPlaylist()
            } catch (e: Exception) {
                Timber.e(e, "移除歌曲失败")
                _uiState.value = _uiState.value.copy(error = "移除失败: ${e.message}")
            }
        }
    }

    /**
     * 播放播放列表中的歌曲
     * 不直接使用缓存 streamUrl，避免使用过期的 Jellyfin 播放参数。
     */
    fun playSong(song: PlaylistSong) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(currentSong = song)

            val session = _uiState.value.selectedDlnaDevice
            if (session == null) {
                _uiState.value = _uiState.value.copy(error = "请先选择播放设备")
                return@launch
            }

            try {
                if (session.id == LOCAL_DEVICE_SESSION_ID) {
                    // 本机播放
                    val playlistSongs = _uiState.value.songs
                    val songsToPlay = if (playlistSongs.any { it.songId == song.songId }) playlistSongs else listOf(song)
                    val musicItems = buildMusicItems(songsToPlay)
                    if (musicItems.isEmpty()) {
                        _uiState.value = _uiState.value.copy(error = "播放失败: 当前播放列表没有可播放歌曲")
                        return@launch
                    }

                    val startIndex = musicItems.indexOfFirst { it.id == song.songId }.coerceAtLeast(0)
                    val currentItem = musicItems.getOrNull(startIndex)
                    Timber.d(
                        "playlist playSong (local): queueSize=${musicItems.size}, startIndex=$startIndex, songId=${song.songId}, playMethod=${currentItem?.streamPlayMethod}, container=${currentItem?.streamContainer}"
                    )
                    musicPlayer.playPlaylist(musicItems, startIndex, QueueSource.PLAYLIST)
                    _uiState.value = _uiState.value.copy(isPlaying = true)
                } else {
                    // 远程播放 - 先停止本机
                    stopLocalPlaybackIfActive()
                    val latestSession = syncRemoteSessionState(session.id) ?: session
                    if (!latestSession.supportsCommand("PlayMediaSource")) {
                        _uiState.value = _uiState.value.copy(
                            error = "设备 ${latestSession.deviceName} 不支持远程发起播放"
                        )
                        return@launch
                    }
                    // 通过 Jellyfin Session API 发起远程播放
                    val result = jellyfinClient.playItem(session.id, song.songId)
                    if (result.isFailure) {
                        _uiState.value = _uiState.value.copy(
                            error = "播放失败: ${result.exceptionOrNull()?.message}"
                        )
                    } else {
                        val syncedSession = syncRemoteSessionState(session.id)
                        // 优先检查 nowPlayingItem 是否匹配
                        val matchedItem = syncedSession?.nowPlayingItem?.id == song.songId
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
                Timber.e(e, "播放播放列表歌曲失败")
                _uiState.value = _uiState.value.copy(error = "播放失败: ${e.message}")
            }
        }
    }

    /**
     * 播放/暂停切换
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
                            jellyfinClient.pause(session.id)
                        }
                        if (result.isFailure) {
                            _uiState.value = _uiState.value.copy(error = "播放控制失败: ${result.exceptionOrNull()?.message}")
                        }
                    } else {
                        val result = executeRemotePlaybackCommand(
                            sessionId = session.id,
                            desiredPlaying = true
                        ) {
                            jellyfinClient.unpause(session.id)
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

    private suspend fun executeRemotePlaybackCommand(
        sessionId: String,
        desiredPlaying: Boolean,
        command: suspend () -> Result<Boolean>
    ): Result<Boolean> {
        val commandResult = command()
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

    private suspend fun buildMusicItems(songs: List<PlaylistSong>): List<MusicItem> {
        return songs.mapNotNull { playlistSong ->
            try {
                val streamInfo = jellyfinClient.getStreamInfo(playlistSong.songId)
                if (streamInfo.url.isBlank()) {
                    Timber.w("跳过无可用播放地址的播放列表歌曲: songId=${playlistSong.songId}")
                    null
                } else {
                    MusicItem(
                        id = playlistSong.songId,
                        title = playlistSong.title,
                        artist = playlistSong.artist,
                        album = playlistSong.album,
                        duration = playlistSong.duration,
                        streamUrl = streamInfo.url,
                        coverUrl = playlistSong.coverUrl,
                        playbackSessionId = streamInfo.playSessionId,
                        mediaSourceId = streamInfo.mediaSourceId,
                        streamContainer = streamInfo.container,
                        streamPlayMethod = streamInfo.playMethod?.name,
                        isTranscoding = streamInfo.isTranscoding
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "构建播放列表队列失败，跳过歌曲: songId=${playlistSong.songId}")
                null
            }
        }
    }

    /**
     * 删除播放列表
     */
    fun deletePlaylist() {
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(playlistId)
            } catch (e: Exception) {
                Timber.e(e, "删除播放列表失败")
                _uiState.value = _uiState.value.copy(error = "删除失败: ${e.message}")
            }
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
