/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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