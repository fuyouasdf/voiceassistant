package com.voiceassistant.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.QueueSource
import com.voiceassistant.data.remote.JellyfinClient
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
    private val playlistRepository: PlaylistRepository,
    private val jellyfinClient: JellyfinClient,
    private val musicPlayer: MusicPlayer
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
            try {
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
                    "playlist playSong: queueSize=${musicItems.size}, startIndex=$startIndex, songId=${song.songId}, playMethod=${currentItem?.streamPlayMethod}, container=${currentItem?.streamContainer}"
                )
                musicPlayer.playPlaylist(musicItems, startIndex, QueueSource.PLAYLIST)
            } catch (e: Exception) {
                Timber.e(e, "播放播放列表歌曲失败")
                _uiState.value = _uiState.value.copy(error = "播放失败: ${e.message}")
            }
        }
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
