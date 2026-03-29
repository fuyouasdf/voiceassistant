package com.voiceassistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalSongDao {
    @Query("SELECT * FROM local_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getSongsByPlaylist(playlistId: Long): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getSongsByPlaylistSync(playlistId: Long): List<LocalSongEntity>

    @Query("SELECT * FROM local_songs WHERE id = :id")
    suspend fun getSongById(id: Long): LocalSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: LocalSongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<LocalSongEntity>)

    @Delete
    suspend fun deleteSong(song: LocalSongEntity)

    @Query("DELETE FROM local_songs WHERE id = :id")
    suspend fun deleteSongById(id: Long)

    @Query("DELETE FROM local_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongsByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM local_songs WHERE playlistId = :playlistId")
    suspend fun getSongCount(playlistId: Long): Int
}