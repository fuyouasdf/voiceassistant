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

import com.voiceassistant.domain.model.Song

/**
 * Manages the playback queue for local playback.
 * This interface allows the domain layer to manage queue state
 * that was previously managed in IntentExecutor (core layer).
 */
interface PlaybackQueueManager {
    /**
     * Set the play queue with a list of songs
     */
    fun setQueue(songs: List<Song>)

    /**
     * Get the current song in the queue
     */
    fun getCurrentSong(): Song?

    /**
     * Get the next song in the queue
     */
    fun getNextSong(): Song?

    /**
     * Get the previous song in the queue
     */
    fun getPreviousSong(): Song?

    /**
     * Advance to the next song (call after playing current song)
     */
    fun advanceToNext()

    /**
     * Go back to the previous song (call after playing current song)
     */
    fun revertToPrevious()

    /**
     * Check if there is a next song available
     */
    fun hasNext(): Boolean

    /**
     * Check if there is a previous song available
     */
    fun hasPrevious(): Boolean

    /**
     * Clear the queue
     */
    fun clear()
}