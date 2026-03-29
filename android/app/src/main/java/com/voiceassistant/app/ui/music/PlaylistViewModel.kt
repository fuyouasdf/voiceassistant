package com.voiceassistant.app.ui.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * 播放列表页面状态
 */
data class PlaylistUiState(
    val isLoading: Boolean = false,
    val playlist: Playlist? = null,
    val songs: List<PlaylistSong> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private var playlistId: Long = -1L

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

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
