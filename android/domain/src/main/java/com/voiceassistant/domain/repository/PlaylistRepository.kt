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

package com.voiceassistant.domain.repository

import com.voiceassistant.domain.model.Playlist
import com.voiceassistant.domain.model.PlaylistSong
import com.voiceassistant.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for playlist operations
 */
interface PlaylistRepository {
    /**
     * Get all playlists
     */
    fun getAllPlaylists(): Flow<List<Playlist>>

    /**
     * Get playlist by ID
     */
    suspend fun getPlaylistById(id: Long): Playlist?

    /**
     * Create a new playlist
     */
    suspend fun createPlaylist(name: String): Long

    /**
     * Delete a playlist
     */
    suspend fun deletePlaylist(id: Long)

    /**
     * Rename a playlist
     */
    suspend fun renamePlaylist(id: Long, newName: String)

    /**
     * Add a song to a playlist
     */
    suspend fun addSongToPlaylist(playlistId: Long, song: Song)

    /**
     * Remove a song from a playlist
     */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    /**
     * Get songs in a playlist
     */
    fun getPlaylistSongs(playlist: Playlist): List<PlaylistSong>

    /**
     * Get playlist song count
     */
    suspend fun getPlaylistSongCount(playlistId: Long): Int
}
