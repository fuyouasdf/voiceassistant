package com.voiceassistant.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.voiceassistant.data.local.PlaylistDao
import com.voiceassistant.data.local.PlaylistEntity
import com.voiceassistant.data.local.PlaylistSong as DataPlaylistSong
import com.voiceassistant.domain.model.Playlist
import com.voiceassistant.domain.model.PlaylistSong
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    private val gson = Gson()

    // ==================== PlaylistRepository Implementation ====================

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPlaylistById(id: Long): Playlist? {
        return playlistDao.getPlaylistById(id)?.toDomain()
    }

    override suspend fun createPlaylist(name: String): Long {
        val playlist = PlaylistEntity(name = name)
        return playlistDao.insertPlaylist(playlist)
    }

    override suspend fun deletePlaylist(id: Long) {
        playlistDao.deletePlaylistById(id)
    }

    override suspend fun renamePlaylist(id: Long, newName: String) {
        val playlist = playlistDao.getPlaylistById(id) ?: return
        playlistDao.updatePlaylist(playlist.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return

        // Parse existing songs
        val currentSongs = parseSongList(playlist.songData).toMutableList()

        // Check if already exists
        if (!currentSongs.any { it.songId == song.id }) {
            val playlistSong = DataPlaylistSong(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                streamUrl = song.url ?: "",
                coverUrl = song.coverUrl
            )
            currentSongs.add(playlistSong)

            // Update database
            val newSongIds = currentSongs.joinToString(",") { it.songId }
            val newSongData = gson.toJson(currentSongs)
            playlistDao.updatePlaylistSongs(playlistId, newSongIds, newSongData)
        }
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return

        val currentSongs = parseSongList(playlist.songData).toMutableList()
        currentSongs.removeAll { it.songId == songId }

        val newSongIds = currentSongs.joinToString(",") { it.songId }
        val newSongData = gson.toJson(currentSongs)
        playlistDao.updatePlaylistSongs(playlistId, newSongIds, newSongData)
    }

    override fun getPlaylistSongs(playlist: Playlist): List<PlaylistSong> {
        // Parse from songData
        if (playlist.songData.isNotEmpty()) {
            return parseSongList(playlist.songData).map { it.toDomain() }
        }
        // Legacy: parse from songIds (only IDs, no details)
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

    override suspend fun getPlaylistSongCount(playlistId: Long): Int {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return 0
        return parseSongList(playlist.songData).size
    }

    // ==================== Helper Methods ====================

    private fun parseSongList(songData: String): List<DataPlaylistSong> {
        if (songData.isEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<DataPlaylistSong>>() {}.type
            gson.fromJson(songData, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "解析歌曲列表失败")
            emptyList()
        }
    }

    // ==================== Extension Functions ====================

    private fun PlaylistEntity.toDomain(): Playlist = Playlist(
        id = id,
        name = name,
        songIds = songIds,
        songData = songData,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun DataPlaylistSong.toDomain(): PlaylistSong = PlaylistSong(
        songId = songId,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        streamUrl = streamUrl,
        coverUrl = coverUrl
    )
}
