package com.voiceassistant.data.repository

import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.PlaybackQueueManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PlaybackQueueManager that manages
 * the local playback queue state.
 */
@Singleton
class PlaybackQueueManagerImpl @Inject constructor() : PlaybackQueueManager {

    private val playQueue = mutableListOf<Song>()
    private var currentIndex: Int = -1

    override fun setQueue(songs: List<Song>) {
        playQueue.clear()
        playQueue.addAll(songs)
        currentIndex = 0
    }

    override fun getCurrentSong(): Song? {
        return if (currentIndex in playQueue.indices) {
            playQueue[currentIndex]
        } else null
    }

    override fun getNextSong(): Song? {
        val nextIndex = currentIndex + 1
        return if (nextIndex in playQueue.indices) {
            playQueue[nextIndex]
        } else null
    }

    override fun getPreviousSong(): Song? {
        val prevIndex = currentIndex - 1
        return if (prevIndex in playQueue.indices) {
            playQueue[prevIndex]
        } else null
    }

    override fun advanceToNext() {
        if (currentIndex + 1 in playQueue.indices) {
            currentIndex++
        }
    }

    override fun revertToPrevious() {
        if (currentIndex - 1 in playQueue.indices) {
            currentIndex--
        }
    }

    override fun hasNext(): Boolean {
        return currentIndex + 1 in playQueue.indices
    }

    override fun hasPrevious(): Boolean {
        return currentIndex - 1 in playQueue.indices
    }

    override fun clear() {
        playQueue.clear()
        currentIndex = -1
    }
}