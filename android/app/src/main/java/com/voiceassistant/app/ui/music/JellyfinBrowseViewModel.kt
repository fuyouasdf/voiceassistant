package com.voiceassistant.app.ui.music

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.remote.JellyfinAlbum
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.JellyfinSong
import com.voiceassistant.data.remote.SessionInfo
import com.voiceassistant.domain.model.Playlist
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val PREF_SELECTED_DEVICE_ID = "jellyfin_selected_device_id"

/**
 * Jellyfin 浏览页面状态
 */
data class JellyfinBrowseUiState(
    val isLoading: Boolean = false,
    val albums: List<JellyfinAlbum> = emptyList(),
    val songs: List<JellyfinSong> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isViewingAlbum: Boolean = false,
    val currentAlbumId: String? = null,
    val currentAlbumName: String = "",
    val error: String? = null,
    val currentSong: JellyfinSong? = null,
    val isPlaying: Boolean = false,
    val dlnaDevices: List<SessionInfo> = emptyList(),
    val selectedDlnaDevice: SessionInfo? = null,
    val isDlnaDiscovering: Boolean = false
)

@HiltViewModel
class JellyfinBrowseViewModel @Inject constructor(
    private val jellyfinClient: JellyfinClient,
    private val sharedPreferences: SharedPreferences,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinBrowseUiState())
    val uiState: StateFlow<JellyfinBrowseUiState> = _uiState.asStateFlow()

    // 播放列表
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // 记住上次选择的设备ID
    private val savedDeviceId: String?
        get() = sharedPreferences.getString(PREF_SELECTED_DEVICE_ID, null)

    init {
        // 加载专辑列表
        loadAlbums()
        // 刷新DLNA设备列表（从Jellyfin会话获取）
        refreshDlnaDevices()
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
            try {
                val albums = jellyfinClient.getAlbums()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    albums = albums,
                    isViewingAlbum = false
                )
            } catch (e: Exception) {
                Timber.e(e, "加载专辑失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载专辑失败: ${e.message}"
                )
            }
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
            try {
                val songs = jellyfinClient.searchSongs(query)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    songs = songs,
                    albums = emptyList()
                )
            } catch (e: Exception) {
                Timber.e(e, "搜索失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "搜索失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 点击专辑，加载专辑中的歌曲
     */
    fun openAlbum(album: JellyfinAlbum) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val songs = jellyfinClient.getItems(album.id)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    songs = songs,
                    isViewingAlbum = true,
                    currentAlbumId = album.id,
                    currentAlbumName = album.name,
                    albums = emptyList()
                )
            } catch (e: Exception) {
                Timber.e(e, "加载专辑歌曲失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载歌曲失败: ${e.message}"
                )
            }
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
    fun playSong(song: JellyfinSong) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(currentSong = song)

            val session = _uiState.value.selectedDlnaDevice
            if (session == null) {
                _uiState.value = _uiState.value.copy(error = "请先选择投屏设备")
                return@launch
            }

            try {
                // 使用Jellyfin Session API 播放到目标设备
                val result = jellyfinClient.playItem(session.id, song.id)
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        error = "播放失败: ${result.exceptionOrNull()?.message}"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isPlaying = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "播放失败")
                _uiState.value = _uiState.value.copy(error = "播放失败: ${e.message}")
            }
        }
    }

    /**
     * 播放/暂停
     */
    fun togglePlayPause() {
        viewModelScope.launch {
            val session = _uiState.value.selectedDlnaDevice ?: return@launch
            try {
                if (_uiState.value.isPlaying) {
                    jellyfinClient.pause(session.id)
                    _uiState.value = _uiState.value.copy(isPlaying = false)
                } else {
                    jellyfinClient.unpause(session.id)
                    _uiState.value = _uiState.value.copy(isPlaying = true)
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
                jellyfinClient.stop(session.id)
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
        _uiState.value = _uiState.value.copy(selectedDlnaDevice = device)
        // 保存设备ID
        sharedPreferences.edit().putString(PREF_SELECTED_DEVICE_ID, device.id).apply()
        Timber.d("已保存投屏设备: ${device.deviceName} (${device.id})")
    }

    /**
     * 刷新DLNA设备列表（从Jellyfin会话获取）
     */
    fun discoverDlnaDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDlnaDiscovering = true)
            refreshDlnaDevices()
            _uiState.value = _uiState.value.copy(isDlnaDiscovering = false)
        }
    }

    /**
     * 从Jellyfin会话刷新可投屏设备列表
     */
    private fun refreshDlnaDevices() {
        viewModelScope.launch {
            try {
                val sessions = jellyfinClient.getSessions()
                // 过滤出支持媒体控制且活跃的会话（这些就是可投屏设备）
                val castableDevices = sessions.filter { it.supportsMediaControl && it.isActive }
                Timber.d("发现 ${castableDevices.size} 个可投屏设备")

                // 尝试恢复上次选择的设备
                val savedDevice = savedDeviceId?.let { savedId ->
                    castableDevices.find { it.id == savedId }
                }
                val selectedDevice = savedDevice
                    ?: _uiState.value.selectedDlnaDevice
                    ?: castableDevices.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    dlnaDevices = castableDevices,
                    selectedDlnaDevice = selectedDevice
                )

                if (savedDevice != null) {
                    Timber.d("已恢复上次选择的设备: ${savedDevice.deviceName}")
                }
            } catch (e: Exception) {
                Timber.e(e, "刷新设备列表失败")
                _uiState.value = _uiState.value.copy(error = "刷新设备列表失败: ${e.message}")
            }
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
    fun addToPlaylist(playlistId: Long, song: JellyfinSong) {
        viewModelScope.launch {
            try {
                val domainSong = Song(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    url = null,
                    coverUrl = song.coverUrl
                )
                playlistRepository.addSongToPlaylist(playlistId, domainSong)
            } catch (e: Exception) {
                Timber.e(e, "添加到播放列表失败")
                _uiState.value = _uiState.value.copy(error = "添加到播放列表失败: ${e.message}")
            }
        }
    }

    /**
     * 创建播放列表
     */
    suspend fun createPlaylist(name: String): Long {
        return playlistRepository.createPlaylist(name)
    }
}
