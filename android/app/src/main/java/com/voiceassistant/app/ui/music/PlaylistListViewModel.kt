package com.voiceassistant.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.domain.model.Playlist
import com.voiceassistant.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PlaylistListItem(
    val playlist: Playlist,
    val songCount: Int
)

data class PlaylistListUiState(
    val isLoading: Boolean = false,
    val playlists: List<PlaylistListItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistListUiState(isLoading = true))
    val uiState: StateFlow<PlaylistListUiState> = _uiState.asStateFlow()

    init {
        observePlaylists()
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collectLatest { playlists ->
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                try {
                    val items = playlists.map { playlist ->
                        async {
                            PlaylistListItem(
                                playlist = playlist,
                                songCount = playlistRepository.getPlaylistSongCount(playlist.id)
                            )
                        }
                    }.map { it.await() }

                    _uiState.value = PlaylistListUiState(
                        isLoading = false,
                        playlists = items
                    )
                } catch (e: Exception) {
                    Timber.e(e, "加载播放列表列表失败")
                    _uiState.value = PlaylistListUiState(
                        isLoading = false,
                        error = "加载播放列表失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistRepository.createPlaylist(name)
            } catch (e: Exception) {
                Timber.e(e, "创建播放列表失败")
                _uiState.value = _uiState.value.copy(error = "创建播放列表失败: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
