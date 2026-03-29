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
