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
    suspend fun getStreamUrl(songId: String): Result<String>

    /**
     * Play item to a Jellyfin session (uses Jellyfin Session API)
     * @param sessionId The session ID to play to
     * @param itemId The item ID to play
     * @return Result indicating success or failure
     */
    suspend fun playItem(sessionId: String, itemId: String): Result<Boolean>

    /**
     * Pause playback on a Jellyfin session
     */
    suspend fun pause(sessionId: String): Result<Boolean>

    /**
     * Resume playback on a Jellyfin session
     */
    suspend fun unpause(sessionId: String): Result<Boolean>

    /**
     * Stop playback on a Jellyfin session
     */
    suspend fun stop(sessionId: String): Result<Boolean>

    /**
     * Skip to next track on a Jellyfin session
     */
    suspend fun nextTrack(sessionId: String): Result<Boolean>

    /**
     * Skip to previous track on a Jellyfin session
     */
    suspend fun previousTrack(sessionId: String): Result<Boolean>

    /**
     * Set volume (0-100) on a Jellyfin session
     */
    suspend fun setVolume(sessionId: String, volume: Int): Result<Boolean>
}
