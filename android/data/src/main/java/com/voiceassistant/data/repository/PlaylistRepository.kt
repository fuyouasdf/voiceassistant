package com.voiceassistant.data.repository

import com.voiceassistant.data.local.PlaylistDao
import com.voiceassistant.data.local.PlaylistEntity
import com.voiceassistant.data.local.PlaylistSong
import com.voiceassistant.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    /**
     * 获取所有播放列表
     */
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    /**
     * 获取播放列表
     */
    suspend fun getPlaylistById(id: Long): PlaylistEntity? = playlistDao.getPlaylistById(id)

    /**
     * 创建播放列表
     */
    suspend fun createPlaylist(name: String): Long {
        val playlist = PlaylistEntity(name = name)
        return playlistDao.insertPlaylist(playlist)
    }

    /**
     * 删除播放列表
     */
    suspend fun deletePlaylist(id: Long) {
        playlistDao.deletePlaylistById(id)
    }

    /**
     * 更新播放列表名称
     */
    suspend fun renamePlaylist(id: Long, newName: String) {
        val playlist = playlistDao.getPlaylistById(id) ?: return
        playlistDao.updatePlaylist(playlist.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 添加歌曲到播放列表
     */
    suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        val currentSongs = if (playlist.songIds.isEmpty()) {
            mutableListOf()
        } else {
            playlist.songIds.split(",").toMutableList()
        }

        // 检查是否已存在
        if (!currentSongs.contains(song.id)) {
            currentSongs.add(song.id)
            playlistDao.updatePlaylistSongs(playlistId, currentSongs.joinToString(","))
        }
    }

    /**
     * 从播放列表移除歌曲
     */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        val currentSongs = playlist.songIds.split(",").toMutableList()

        currentSongs.remove(songId)
        playlistDao.updatePlaylistSongs(playlistId, currentSongs.joinToString(","))
    }

    /**
     * 获取播放列表中的歌曲数量
     */
    suspend fun getPlaylistSongCount(playlistId: Long): Int {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return 0
        return if (playlist.songIds.isEmpty()) 0 else playlist.songIds.split(",").size
    }

    /**
     * 将 PlaylistEntity 转换为 PlaylistSong 列表
     * 需要传入歌曲数据查询接口
     */
    suspend fun getPlaylistSongs(
        playlistId: Long,
        getSongById: suspend (String) -> Song?
    ): List<PlaylistSong> {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return emptyList()
        val songIds = if (playlist.songIds.isEmpty()) emptyList() else playlist.songIds.split(",")

        return songIds.mapNotNull { songId ->
            val song = getSongById(songId)
            if (song != null) {
                PlaylistSong(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    streamUrl = "", // 外部提供
                    coverUrl = null
                )
            } else null
        }
    }
}