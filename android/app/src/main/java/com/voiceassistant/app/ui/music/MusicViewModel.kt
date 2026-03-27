package com.voiceassistant.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.local.PlaylistEntity
import com.voiceassistant.data.remote.JellyfinAlbum
import com.voiceassistant.data.remote.JellyfinArtist
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.JellyfinSong
import com.voiceassistant.data.repository.PlaylistRepository
import com.voiceassistant.app.di.ConfigHolder
import com.voiceassistant.domain.model.Song
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 音乐页面 UI 状态
 */
data class MusicUiState(
    val isLoading: Boolean = false,
    val songs: List<JellyfinSong> = emptyList(),
    val albums: List<com.voiceassistant.data.remote.JellyfinAlbum> = emptyList(),
    val artists: List<com.voiceassistant.data.remote.JellyfinArtist> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val searchResults: List<JellyfinSong> = emptyList(),
    val currentSong: MusicItem? = null,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val currentCategory: MusicCategory = MusicCategory.SONGS
)

enum class MusicCategory {
    SONGS, ALBUMS, ARTISTS
}

/**
 * 音乐页面 ViewModel
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val jellyfinClient: JellyfinClient,
    private val musicPlayer: MusicPlayer,
    private val playlistRepository: PlaylistRepository,
    private val configHolder: ConfigHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        // 监听播放状态
        viewModelScope.launch {
            musicPlayer.state.collect { playerState ->
                _uiState.update {
                    it.copy(
                        isPlaying = playerState.isPlaying,
                        currentSong = playerState.playlist.getOrNull(playerState.currentIndex)
                    )
                }
            }
        }

        // 监听播放列表
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    /**
     * 初始化 Jellyfin 连接
     */
    fun initJellyfin() {
        // ApiClient 已经在 AppModule 中配置好了
        // 如果需要重新配置，可以在这里更新
        viewModelScope.launch {
            val result = jellyfinClient.testConnection()
            result.onFailure { e ->
                Timber.e(e, "Jellyfin connection failed")
                _uiState.update { it.copy(error = "连接失败: ${e.message}") }
            }
        }
    }

    /**
     * 加载歌曲列表
     */
    fun loadSongs() {
        initJellyfin()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentCategory = MusicCategory.SONGS) }

            try {
                val songs = jellyfinClient.searchSongs("", 50)
                _uiState.update { it.copy(songs = songs, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load songs")
                _uiState.update { it.copy(isLoading = false, error = "加载歌曲失败: ${e.message}") }
            }
        }
    }

    /**
     * 加载专辑列表
     */
    fun loadAlbums(parentId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentCategory = MusicCategory.ALBUMS) }

            try {
                val albums = jellyfinClient.getAlbums(parentId)
                _uiState.update { it.copy(albums = albums, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load albums")
                _uiState.update { it.copy(isLoading = false, error = "加载专辑失败: ${e.message}") }
            }
        }
    }

    /**
     * 加载艺术家列表
     */
    fun loadArtists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentCategory = MusicCategory.ARTISTS) }

            try {
                val artists = jellyfinClient.getArtists()
                _uiState.update { it.copy(artists = artists, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load artists")
                _uiState.update { it.copy(isLoading = false, error = "加载艺术家失败: ${e.message}") }
            }
        }
    }

    /**
     * 搜索歌曲
     */
    fun searchSongs(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val results = jellyfinClient.searchSongs(query)
                _uiState.update { it.copy(searchResults = results, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to search songs")
                _uiState.update { it.copy(isLoading = false, error = "搜索失败: ${e.message}") }
            }
        }
    }

    /**
     * 播放歌曲
     */
    fun playSong(song: JellyfinSong) {
        viewModelScope.launch {
            // 异步获取流媒体 URL
            val streamUrl = jellyfinClient.getStreamUrl(song.id)
            Timber.d("Playing song: ${song.title}, stream URL: $streamUrl")

            if (streamUrl.isEmpty()) {
                _uiState.update { it.copy(error = "无法获取播放地址") }
                return@launch
            }

            val item = MusicItem(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                streamUrl = streamUrl,
                coverUrl = jellyfinClient.getCoverUrl(song.id)
            )

            // 如果正在播放同一首歌列表，则切换播放/暂停
            val currentPlaylist = _uiState.value.songs.map { s ->
                MusicItem(
                    id = s.id,
                    title = s.title,
                    artist = s.artist,
                    album = s.album,
                    duration = s.duration,
                    streamUrl = "", // 需要异步获取
                    coverUrl = jellyfinClient.getCoverUrl(s.id)
                )
            }

            val currentIndex = currentPlaylist.indexOfFirst { it.id == item.id }
            if (currentIndex >= 0) {
                musicPlayer.playPlaylist(currentPlaylist, currentIndex)
            } else {
                musicPlayer.play(item)
            }
        }
    }

    /**
     * 播放播放列表
     */
    fun playPlaylist(playlist: List<MusicItem>, startIndex: Int = 0) {
        musicPlayer.playPlaylist(playlist, startIndex)
    }

    /**
     * 切换播放/暂停
     */
    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            musicPlayer.pause()
        } else {
            musicPlayer.resume()
        }
    }

    /**
     * 播放上一首
     */
    fun playPrevious() {
        musicPlayer.playPrevious()
    }

    /**
     * 播放下一首
     */
    fun playNext() {
        musicPlayer.playNext()
    }

    /**
     * 创建播放列表
     */
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistRepository.createPlaylist(name)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create playlist")
                _uiState.update { it.copy(error = "创建播放列表失败: ${e.message}") }
            }
        }
    }

    /**
     * 删除播放列表
     */
    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(playlistId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete playlist")
                _uiState.update { it.copy(error = "删除播放列表失败: ${e.message}") }
            }
        }
    }

    /**
     * 添加歌曲到播放列表
     */
    fun addToPlaylist(playlistId: Long, song: JellyfinSong) {
        viewModelScope.launch {
            try {
                // 异步获取流 URL
                val streamUrl = jellyfinClient.getStreamUrl(song.id)
                val coverUrl = jellyfinClient.getCoverUrl(song.id)

                val domainSong = Song(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    url = streamUrl,
                    coverUrl = coverUrl
                )
                playlistRepository.addSongToPlaylist(playlistId, domainSong)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add song to playlist")
                _uiState.update { it.copy(error = "添加歌曲失败: ${e.message}") }
            }
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // 不释放 MusicPlayer，因为它是单例
    }
}