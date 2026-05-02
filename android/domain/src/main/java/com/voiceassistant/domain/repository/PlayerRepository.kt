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