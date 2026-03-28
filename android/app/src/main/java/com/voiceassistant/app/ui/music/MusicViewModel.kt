package com.voiceassistant.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.local.PlaylistEntity
import com.voiceassistant.data.remote.JellyfinAlbum
import com.voiceassistant.data.remote.JellyfinArtist
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.JellyfinItem
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
    val folderItems: List<JellyfinItem> = emptyList(), // 文件夹内的项目（艺术家、专辑、歌曲混合）
    val currentSong: MusicItem? = null,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val currentCategory: MusicCategory = MusicCategory.ALBUMS,
    val needsJellyfinConfig: Boolean = false,
    val navigationStack: List<MusicCategory> = listOf(MusicCategory.ALBUMS), // 导航历史栈
    // 播放列表详情
    val currentPlaylist: PlaylistEntity? = null,
    val currentPlaylistSongs: List<JellyfinSong> = emptyList(),
    val isLoadingPlaylistDetail: Boolean = false
)

enum class MusicCategory {
    SONGS, ALBUMS, ARTISTS, FOLDER
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
        // 设置 seek 回调：当 ExoPlayer 的 range seek 不支持时，通过重新加载流来实现 seek
        musicPlayer.setSeekCallback(object : MusicPlayer.SeekCallback {
            override suspend fun onSeekToGetStreamUrl(item: MusicItem, positionMs: Long): String? {
                return try {
                    val url = jellyfinClient.getStreamUrlWithStartTime(
                        songId = item.id,
                        startTimeMs = positionMs,
                        playSessionId = item.playbackSessionId,
                        mediaSourceId = item.mediaSourceId
                    )
                    if (url.isNotEmpty()) url else null
                } catch (e: Exception) {
                    Timber.e(e, "getStreamUrlWithStartTime failed")
                    null
                }
            }
        })

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
        Timber.d("initJellyfin: jellyfinUrl=${configHolder.jellyfinUrl}, apiKey=${if (configHolder.jellyfinApiKey.isNotEmpty()) "已设置" else "未设置"}")
        viewModelScope.launch {
            val result = jellyfinClient.testConnection()
            result.onSuccess {
                Timber.d("Jellyfin连接成功")
            }
            result.onFailure { e ->
                Timber.e(e, "Jellyfin连接失败")
                _uiState.update { it.copy(error = "连接失败: ${e.message}") }
            }
        }
    }

    /**
     * 加载歌曲列表
     */
    fun loadSongs() {
        Timber.d("loadSongs: 开始加载歌曲")
        // 检查 Jellyfin 是否已配置
        if (configHolder.jellyfinUrl.isEmpty() || configHolder.jellyfinApiKey.isEmpty()) {
            Timber.w("Jellyfin未配置: url=${configHolder.jellyfinUrl}, apiKey=${configHolder.jellyfinApiKey}")
            _uiState.update { it.copy(needsJellyfinConfig = true, isLoading = false) }
            return
        }

        initJellyfin()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentCategory = MusicCategory.SONGS) }
            Timber.d("loadSongs: 正在请求歌曲列表...")

            try {
                // 使用 getItems 获取所有歌曲，而不是 searchSongs（后者需要搜索词）
                val songs = jellyfinClient.getAllSongs()
                Timber.d("loadSongs: 获取到${songs.size}首歌曲")
                _uiState.update { it.copy(songs = songs, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "loadSongs: 加载歌曲失败")
                _uiState.update { it.copy(isLoading = false, error = "加载歌曲失败: ${e.message}") }
            }
        }
    }

    /**
     * 加载专辑列表
     */
    fun loadAlbums(parentId: String? = null) {
        Timber.d("loadAlbums: parentId=$parentId, 开始加载专辑")
        // 检查 Jellyfin 是否已配置
        if (configHolder.jellyfinUrl.isEmpty() || configHolder.jellyfinApiKey.isEmpty()) {
            Timber.w("Jellyfin未配置: url=${configHolder.jellyfinUrl}, apiKey=${configHolder.jellyfinApiKey}")
            _uiState.update { it.copy(needsJellyfinConfig = true, isLoading = false) }
            return
        }

        initJellyfin()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentCategory = MusicCategory.ALBUMS) }
            Timber.d("loadAlbums: 正在请求专辑列表...")

            try {
                val albums = jellyfinClient.getAlbums(parentId)
                Timber.d("loadAlbums: 获取到${albums.size}张专辑")
                _uiState.update { it.copy(albums = albums, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "loadAlbums: 加载专辑失败")
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
     * 加载专辑/文件夹下的内容
     */
    fun loadAlbumSongs(albumId: String) {
        viewModelScope.launch {
            // 保存当前状态到导航栈，然后进入新的 FOLDER 状态
            // 注意：navigationStack 只保存"之前的"状态，不保存当前位置
            val currentNavState = _uiState.value.currentCategory
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    currentCategory = MusicCategory.FOLDER,
                    navigationStack = state.navigationStack + listOf(currentNavState)
                )
            }

            try {
                // 使用 getFolderItems 获取所有类型的项目
                val items = jellyfinClient.getFolderItems(albumId)
                Timber.d("loadAlbumSongs: 获取到${items.size}个混合项目")

                // 分类处理
                val songs = items.filter { it.isAudio }.map { item ->
                    JellyfinSong(
                        id = item.id,
                        title = item.name,
                        artist = item.artist,
                        album = item.albumName,
                        duration = item.duration,
                        coverUrl = jellyfinClient.getCoverUrl(item.id)
                    )
                }

                val subAlbums = items.filter { it.isAlbum || it.isFolder }.map { item ->
                    JellyfinAlbum(
                        id = item.id,
                        name = item.name,
                        artist = item.artist,
                        imageTag = null
                    )
                }

                _uiState.update {
                    it.copy(
                        songs = songs,
                        albums = subAlbums,
                        folderItems = items,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load folder items")
                _uiState.update { it.copy(isLoading = false, error = "加载失败: ${e.message}") }
            }
        }
    }

    /**
     * 返回上一级导航
     * @return true 如果成功返回，false 如果已经在最顶层
     */
    fun navigateBack(): Boolean {
        val currentStack = _uiState.value.navigationStack
        if (currentStack.isEmpty()) {
            // 导航栈为空，无法返回
            return false
        }

        val previousCategory = currentStack.last()
        val newStack = currentStack.dropLast(1)

        // 恢复之前的分类状态
        _uiState.update { it.copy(currentCategory = previousCategory, navigationStack = newStack) }

        // 根据恢复的分类加载对应的数据
        when (previousCategory) {
            MusicCategory.ALBUMS -> loadAlbums()
            MusicCategory.ARTISTS -> loadArtists()
            MusicCategory.SONGS -> loadSongs()
            MusicCategory.FOLDER -> {
                // 之前的 FOLDER 状态需要特殊处理，加载最近的专辑/艺术家列表
                if (newStack.isNotEmpty() && newStack.last() == MusicCategory.FOLDER) {
                    // 如果再之前也是 FOLDER，需要找到正确的父级
                    // 这里简单处理为加载专辑列表
                    loadAlbums()
                } else {
                    // 恢复导航栈后再加载对应的数据
                    // 由于已经更新了 currentCategory，不需要额外操作
                }
            }
        }

        return true
    }

    /**
     * 检查是否可以返回
     */
    fun canNavigateBack(): Boolean {
        return _uiState.value.navigationStack.size > 1
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
     * 搜索专辑
     */
    fun searchAlbums(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val results = jellyfinClient.getAlbums(searchTerm = query)
                _uiState.update { it.copy(albums = results, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to search albums")
                _uiState.update { it.copy(isLoading = false, error = "搜索失败: ${e.message}") }
            }
        }
    }

    /**
     * 播放歌曲
     */
    fun playSong(song: JellyfinSong) {
        android.util.Log.i("♪", "VM playSong called: ${song.title}")
        viewModelScope.launch {
            // 异步获取流媒体 URL 和会话信息
            val streamInfo = jellyfinClient.getStreamInfo(song.id)
            android.util.Log.i("♪", "VM got streamInfo: ${song.title}, sessionId=${streamInfo.playSessionId}")
            Timber.d("Playing song: ${song.title}, stream URL: ${streamInfo.url}")

            if (streamInfo.url.isEmpty()) {
                _uiState.update { it.copy(error = "无法获取播放地址") }
                return@launch
            }

            val item = MusicItem(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                streamUrl = streamInfo.url,
                coverUrl = jellyfinClient.getCoverUrl(song.id),
                playbackSessionId = streamInfo.playSessionId,
                mediaSourceId = streamInfo.mediaSourceId
            )

            // 如果正在播放同一首歌列表，则切换播放/暂停
            musicPlayer.play(item)
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
     * 加载播放列表详情
     */
    fun loadPlaylistDetail(playlistId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPlaylistDetail = true) }

            try {
                // 获取播放列表信息
                val playlist = playlistRepository.getPlaylistById(playlistId)
                if (playlist == null) {
                    _uiState.update {
                        it.copy(
                            isLoadingPlaylistDetail = false,
                            error = "播放列表不存在"
                        )
                    }
                    return@launch
                }

                // 获取播放列表中的歌曲
                val songIds = if (playlist.songIds.isEmpty()) {
                    emptyList()
                } else {
                    playlist.songIds.split(",")
                }

                // 逐个获取歌曲详情
                val songs = songIds.mapNotNull { songId ->
                    jellyfinClient.getItem(songId)
                }

                _uiState.update {
                    it.copy(
                        currentPlaylist = playlist,
                        currentPlaylistSongs = songs,
                        isLoadingPlaylistDetail = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load playlist detail")
                _uiState.update {
                    it.copy(
                        isLoadingPlaylistDetail = false,
                        error = "加载播放列表失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 获取封面图片 URL
     */
    fun getCoverUrl(itemId: String): String {
        return jellyfinClient.getCoverUrl(itemId)
    }

    /**
     * 处理文件夹项目点击
     */
    fun onFolderItemClick(item: com.voiceassistant.data.remote.JellyfinItem) {
        when {
            item.isAudio -> {
                // 点击的是歌曲，查找对应的 JellyfinSong 并播放
                val song = _uiState.value.songs.find { it.id == item.id }
                song?.let { playSong(it) }
            }
            item.isAlbum || item.isFolder -> {
                // 点击的是专辑或文件夹，加载其内容
                loadAlbumSongs(item.id)
            }
            item.isArtist -> {
                // 点击的是艺术家，加载该艺术家的内容
                loadAlbumSongs(item.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 不释放 MusicPlayer，因为它是单例
    }
}