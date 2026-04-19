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