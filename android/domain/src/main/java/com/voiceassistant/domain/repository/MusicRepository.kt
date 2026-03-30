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

    /**
     * Play item to a Jellyfin session (uses Jellyfin Session API)
     * @param sessionId The session ID to play to
     * @param itemId The item ID to play
     * @return Result indicating success or failure
     */
    suspend fun playItem(sessionId: String, itemId: String): Result<Boolean>
}