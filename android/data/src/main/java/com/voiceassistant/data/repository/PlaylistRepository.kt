package com.voiceassistant.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val gson = Gson()

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

        // 解析现有歌曲数据
        val currentSongs = parseSongList(playlist.songData).toMutableList()

        // 检查是否已存在
        if (!currentSongs.any { it.songId == song.id }) {
            val playlistSong = PlaylistSong(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                streamUrl = song.url ?: "",
                coverUrl = song.coverUrl
            )
            currentSongs.add(playlistSong)

            // 更新数据库
            val newSongIds = currentSongs.joinToString(",") { it.songId }
            val newSongData = gson.toJson(currentSongs)
            playlistDao.updatePlaylistSongs(playlistId, newSongIds, newSongData)
        }
    }

    /**
     * 从播放列表移除歌曲
     */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return

        val currentSongs = parseSongList(playlist.songData).toMutableList()
        currentSongs.removeAll { it.songId == songId }

        val newSongIds = currentSongs.joinToString(",") { it.songId }
        val newSongData = gson.toJson(currentSongs)
        playlistDao.updatePlaylistSongs(playlistId, newSongIds, newSongData)
    }

    /**
     * 获取播放列表中的歌曲
     */
    fun getPlaylistSongs(playlist: PlaylistEntity): List<PlaylistSong> {
        // 优先从 songData 解析
        if (playlist.songData.isNotEmpty()) {
            return parseSongList(playlist.songData)
        }
        // 兼容旧数据：从 songIds 解析（只包含 ID，没有详细信息）
        if (playlist.songIds.isNotEmpty()) {
            return playlist.songIds.split(",").map { id ->
                PlaylistSong(
                    songId = id,
                    title = "未知歌曲",
                    artist = null,
                    album = null,
                    duration = 0,
                    streamUrl = "",
                    coverUrl = null
                )
            }
        }
        return emptyList()
    }

    /**
     * 解析歌曲列表
     */
    private fun parseSongList(songData: String): List<PlaylistSong> {
        if (songData.isEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<PlaylistSong>>() {}.type
            gson.fromJson(songData, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "解析歌曲列表失败")
            emptyList()
        }
    }

    /**
     * 获取播放列表中的歌曲数量
     */
    suspend fun getPlaylistSongCount(playlistId: Long): Int {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return 0
        return parseSongList(playlist.songData).size
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