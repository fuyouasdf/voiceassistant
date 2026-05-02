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

import com.voiceassistant.domain.model.Album
import com.voiceassistant.domain.model.LyricsResult
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
     * Search albums by query
     */
    suspend fun searchAlbums(query: String): Result<List<Album>>

    /**
     * Get all albums (browsing)
     */
    suspend fun getAlbums(): Result<List<Album>>

    /**
     * Get songs in an album
     */
    suspend fun getAlbumSongs(albumId: String): Result<List<Song>>

    /**
     * Get a single item by ID
     */
    suspend fun getItem(itemId: String): Result<Song?>

    /**
     * Get lyrics for a song
     */
    suspend fun getLyrics(itemId: String): Result<LyricsResult?>

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

    /**
     * Get current volume (0-100) from a Jellyfin session
     */
    suspend fun getVolume(sessionId: String): Result<Int>

    /**
     * Get stream information for a song (includes URL and playback session info)
     */
    suspend fun getStreamInfo(songId: String): Result<StreamInfo>

    /**
     * Refresh stream URL for a song (bypasses cache)
     * Used when playback fails due to expired URL
     * @param songId The song ID to refresh
     * @return Fresh stream URL or failure
     */
    suspend fun refreshStreamUrl(songId: String): Result<String>
}

/**
 * Stream information for playback
 */
data class StreamInfo(
    val url: String,
    val playSessionId: String?,
    val mediaSourceId: String?,
    val playMethod: String?,
    val container: String?,
    val isTranscoding: Boolean
)
