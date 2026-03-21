package com.voiceassistant.domain.repository

import com.voiceassistant.domain.model.Song

/**
 * Repository interface for music operations
 */
interface MusicRepository {
    /**
     * Test connection to music server
     */
    suspend fun testConnection(): Result<Boolean>

    /**
     * Search songs by query
     */
    suspend fun searchSongs(query: String): Result<List<Song>>

    /**
     * Get stream URL for a song
     */
    suspend fun getStreamUrl(songId: String): String
}