package com.voiceassistant.domain.repository

/**
 * Repository interface for media playback operations (DLNA, local, etc.)
 */
interface PlayerRepository {
    /**
     * Play a media stream
     * @param url The stream URL to play
     * @param title The title of the media
     * @param artist The artist of the media
     */
    suspend fun play(url: String, title: String, artist: String): Result<Unit>

    /**
     * Pause playback
     */
    suspend fun pause(): Result<Unit>

    /**
     * Resume playback
     */
    suspend fun resume(): Result<Unit>

    /**
     * Stop playback
     */
    suspend fun stop(): Result<Unit>

    /**
     * Set volume (0-100)
     */
    suspend fun setVolume(volume: Int): Result<Unit>

    /**
     * Check if a player is available/connected
     */
    fun isPlayerAvailable(): Boolean
}