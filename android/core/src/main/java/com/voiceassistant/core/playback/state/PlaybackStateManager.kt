/*
 * Copyright (c) 2023 Auxio Project
 * PlaybackStateManager.kt is part of Auxio.
 * Adapted for Voice Assistant project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.voiceassistant.core.playback.state

import com.voiceassistant.core.music.MusicItem
import timber.log.Timber

/**
 * Core playback state controller class.
 *
 * This should ***NOT*** be used outside of the playback module.
 * - If you want to use the playback state in the UI, use PlaybackViewModel as it can withstand
 *   volatile UIs.
 * - If you want to use the playback state with the ExoPlayer instance or system-side things, use
 *   PlaybackService.
 *
 * Internal consumers should usually use [Listener], however the component that manages the player
 * itself should instead use [PlaybackStateHolder].
 */
interface PlaybackStateManager {
    /** The current [Progression] of the audio player */
    val progression: Progression

    /** The current [RepeatMode]. */
    val repeatMode: RepeatMode

    /** The current [MusicItem] being played. Null if nothing is playing. */
    val currentSong: MusicItem?

    /** The current queue of [MusicItem]s. */
    val queue: List<MusicItem>

    /** The index of the currently playing [MusicItem] in the queue. */
    val index: Int

    /** Whether the queue is shuffled or not. */
    val isShuffled: Boolean

    /** The audio session ID of the internal player. Null if no internal player exists. */
    val currentAudioSessionId: Int?

    /**
     * Add a [Listener] to this instance. This can be used to receive changes in the playback state.
     * Will immediately invoke [Listener] methods to initialize the instance with the current state.
     *
     * @param listener The [Listener] to add.
     * @see Listener
     */
    fun addListener(listener: Listener)

    /**
     * Remove a [Listener] from this instance, preventing it from receiving any further updates.
     *
     * @param listener The [Listener] to remove.
     * @see Listener
     */
    fun removeListener(listener: Listener)

    /**
     * Register an [PlaybackStateHolder] for this instance. This instance will handle translating
     * the current playback state into audio playback. There can be only one [PlaybackStateHolder]
     * at a time.
     *
     * @param stateHolder The [PlaybackStateHolder] to register.
     */
    fun registerStateHolder(stateHolder: PlaybackStateHolder)

    /**
     * Unregister the [PlaybackStateHolder] from this instance.
     *
     * @param stateHolder The [PlaybackStateHolder] to unregister.
     */
    fun unregisterStateHolder(stateHolder: PlaybackStateHolder)

    /**
     * Start new playback.
     *
     * @param items The list of items to play.
     * @param startIndex The index of the item to start playing from.
     * @param shuffled Whether to shuffle the queue.
     */
    fun play(items: List<MusicItem>, startIndex: Int = 0, shuffled: Boolean = false)

    /**
     * Go to the next [MusicItem] in the queue.
     */
    fun next()

    /**
     * Go to the previous [MusicItem] in the queue.
     */
    fun prev()

    /**
     * Play a [MusicItem] at the given position in the queue.
     *
     * @param index The position of the [MusicItem] in the queue to start playing.
     */
    fun goto(index: Int)

    /**
     * Add [MusicItem]s to the top of the queue.
     *
     * @param items The [MusicItem]s to add.
     */
    fun playNext(items: List<MusicItem>)

    /**
     * Add a [MusicItem] to the top of the queue.
     *
     * @param item The [MusicItem] to add.
     */
    fun playNext(item: MusicItem) = playNext(listOf(item))

    /**
     * Add [MusicItem]s to the end of the queue.
     *
     * @param items The [MusicItem]s to add.
     */
    fun addToQueue(items: List<MusicItem>)

    /**
     * Add a [MusicItem] to the end of the queue.
     *
     * @param item The [MusicItem] to add.
     */
    fun addToQueue(item: MusicItem) = addToQueue(listOf(item))

    /**
     * Move a [MusicItem] in the queue.
     *
     * @param src The position of the [MusicItem] to move in the queue.
     * @param dst The destination position in the queue.
     */
    fun moveQueueItem(src: Int, dst: Int)

    /**
     * Remove a [MusicItem] from the queue.
     *
     * @param at The position of the [MusicItem] to remove in the queue.
     */
    fun removeQueueItem(at: Int)

    /**
     * (Re)shuffle or (Re)order this instance.
     *
     * @param shuffled Whether to shuffle the queue or not.
     */
    fun shuffled(shuffled: Boolean)

    /**
     * Acknowledges that an event has happened that modified the state held by the current
     * [PlaybackStateHolder].
     *
     * @param stateHolder The [PlaybackStateHolder] to synchronize with.
     * @param ack The [StateAck] to acknowledge.
     */
    fun ack(stateHolder: PlaybackStateHolder, ack: StateAck)

    /**
     * Update whether playback is ongoing or not.
     *
     * @param isPlaying Whether playback is ongoing or not.
     */
    fun playing(isPlaying: Boolean)

    /**
     * Update the current [RepeatMode].
     *
     * @param repeatMode The new [RepeatMode].
     */
    fun repeatMode(repeatMode: RepeatMode)

    /**
     * Seek to the given position in the currently playing [MusicItem].
     *
     * @param positionMs The position to seek to, in milliseconds.
     */
    fun seekTo(positionMs: Long)

    fun endSession()

    /**
     * The interface for receiving updates from [PlaybackStateManager].
     */
    interface Listener {
        /**
         * Called when the index of the currently playing item has changed.
         *
         * @param index The new index of the currently playing [MusicItem].
         */
        fun onIndexMoved(index: Int) {}

        /**
         * Called when the queue changed.
         *
         * @param queue The songs of the new queue.
         * @param index The new index of the currently playing [MusicItem].
         */
        fun onQueueChanged(queue: List<MusicItem>, index: Int) {}

        /**
         * Called when the queue has changed in a non-trivial manner (such as re-shuffling).
         *
         * @param queue The songs of the new queue.
         * @param index The new index of the currently playing [MusicItem].
         * @param isShuffled Whether the queue is shuffled or not.
         */
        fun onQueueReordered(queue: List<MusicItem>, index: Int, isShuffled: Boolean) {}

        /**
         * Called when a new playback configuration was created.
         *
         * @param queue The queue of [MusicItem]s to play from.
         * @param index The index of the currently playing [MusicItem].
         * @param isShuffled Whether the queue is shuffled or not.
         */
        fun onNewPlayback(
            queue: List<MusicItem>,
            index: Int,
            isShuffled: Boolean,
        ) {}

        /**
         * Called when the state of the audio player changes.
         *
         * @param progression The new state of the audio player.
         */
        fun onProgressionChanged(progression: Progression) {}

        /**
         * Called when the [RepeatMode] changes.
         *
         * @param repeatMode The new [RepeatMode].
         */
        fun onRepeatModeChanged(repeatMode: RepeatMode) {}

        fun onSessionEnded() {}
    }
}

class PlaybackStateManagerImpl : PlaybackStateManager {
    private data class StateMirror(
        val progression: Progression,
        val repeatMode: RepeatMode,
        val queue: List<MusicItem>,
        val index: Int,
        val isShuffled: Boolean,
        val rawQueue: RawQueue,
    )

    private val listeners = mutableListOf<PlaybackStateManager.Listener>()

    @Volatile
    private var stateMirror =
        StateMirror(
            progression = Progression.nil(),
            repeatMode = RepeatMode.NONE,
            queue = emptyList(),
            index = -1,
            isShuffled = false,
            rawQueue = RawQueue.nil(),
        )

    @Volatile
    private var stateHolder: PlaybackStateHolder? = null

    @Volatile
    private var isInitialized = false

    override val progression
        get() = stateMirror.progression

    override val repeatMode
        get() = stateMirror.repeatMode

    override val currentSong
        get() = stateMirror.queue.getOrNull(stateMirror.index)

    override val queue
        get() = stateMirror.queue

    override val index
        get() = stateMirror.index

    override val isShuffled
        get() = stateMirror.isShuffled

    override val currentAudioSessionId: Int?
        get() = stateHolder?.audioSessionId

    @Synchronized
    override fun addListener(listener: PlaybackStateManager.Listener) {
        Timber.d("Adding $listener to listeners")
        listeners.add(listener)

        if (isInitialized) {
            Timber.d("Sending initial state to $listener")
            listener.onNewPlayback(
                stateMirror.queue,
                stateMirror.index,
                stateMirror.isShuffled,
            )
            listener.onProgressionChanged(stateMirror.progression)
            listener.onRepeatModeChanged(stateMirror.repeatMode)
        }
    }

    @Synchronized
    override fun removeListener(listener: PlaybackStateManager.Listener) {
        Timber.d("Removing $listener from listeners")
        if (!listeners.remove(listener)) {
            Timber.w("Listener $listener was not added prior, cannot remove")
        }
    }

    @Synchronized
    override fun registerStateHolder(stateHolder: PlaybackStateHolder) {
        if (this.stateHolder != null) {
            Timber.w("Internal player is already registered")
            return
        }

        this.stateHolder = stateHolder
        if (isInitialized && currentSong != null) {
            stateHolder.applySavedState(
                stateMirror.rawQueue,
                stateMirror.progression.calculateElapsedPositionMs(),
                stateMirror.repeatMode,
                null,
            )
        }
    }

    @Synchronized
    override fun unregisterStateHolder(stateHolder: PlaybackStateHolder) {
        if (this.stateHolder !== stateHolder) {
            Timber.w("Given internal player did not match current internal player")
            return
        }

        Timber.d("Unregistering internal player $stateHolder")
        this.stateHolder = null
    }

    // --- PLAYING FUNCTIONS ---

    @Synchronized
    override fun play(items: List<MusicItem>, startIndex: Int, shuffled: Boolean) {
        val stateHolder = stateHolder ?: return
        if (items.isEmpty()) return

        Timber.d("Playing ${items.size} items, startIndex=$startIndex, shuffled=$shuffled")
        isInitialized = true
        stateHolder.newPlayback(items, startIndex, shuffled)
    }

    // --- QUEUE FUNCTIONS ---

    @Synchronized
    override fun next() {
        val stateHolder = stateHolder ?: return
        Timber.d("Going to next song")
        stateHolder.next()
    }

    @Synchronized
    override fun prev() {
        val stateHolder = stateHolder ?: return
        Timber.d("Going to previous song")
        stateHolder.prev()
    }

    @Synchronized
    override fun goto(index: Int) {
        val stateHolder = stateHolder ?: return
        Timber.d("Going to index $index")
        stateHolder.goto(index)
    }

    @Synchronized
    override fun playNext(items: List<MusicItem>) {
        if (currentSong == null) {
            Timber.d("Nothing playing, short-circuiting to new playback")
            play(items)
        } else {
            val stateHolder = stateHolder ?: return
            Timber.d("Adding ${items.size} items to start of queue")
            stateHolder.playNext(items, StateAck.PlayNext(stateMirror.index + 1, items.size))
        }
    }

    @Synchronized
    override fun addToQueue(items: List<MusicItem>) {
        if (currentSong == null) {
            Timber.d("Nothing playing, short-circuiting to new playback")
            play(items)
        } else {
            val stateHolder = stateHolder ?: return
            Timber.d("Adding ${items.size} items to end of queue")
            stateHolder.addToQueue(items, StateAck.AddToQueue(queue.size, items.size))
        }
    }

    @Synchronized
    override fun moveQueueItem(src: Int, dst: Int) {
        val stateHolder = stateHolder ?: return
        Timber.d("Moving item $src to position $dst")
        stateHolder.move(src, dst, StateAck.Move(src, dst))
    }

    @Synchronized
    override fun removeQueueItem(at: Int) {
        val stateHolder = stateHolder ?: return
        Timber.d("Removing item at $at")
        stateHolder.remove(at, StateAck.Remove(at))
    }

    @Synchronized
    override fun shuffled(shuffled: Boolean) {
        val stateHolder = stateHolder ?: return
        Timber.d("Reordering queue [shuffled=$shuffled]")
        stateHolder.shuffled(shuffled)
    }

    // --- INTERNAL PLAYER FUNCTIONS ---

    @Synchronized
    override fun playing(isPlaying: Boolean) {
        val stateHolder = stateHolder ?: return
        Timber.d("Updating playing state to $isPlaying")
        stateHolder.playing(isPlaying)
    }

    @Synchronized
    override fun repeatMode(repeatMode: RepeatMode) {
        val stateHolder = stateHolder ?: return
        Timber.d("Updating repeat mode to $repeatMode")
        stateHolder.repeatMode(repeatMode)
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        val stateHolder = stateHolder ?: return
        Timber.d("Seeking to ${positionMs}ms")
        stateHolder.seekTo(positionMs)
    }

    @Synchronized
    override fun endSession() {
        val stateHolder = stateHolder ?: return
        Timber.d("Ending session")
        stateHolder.endSession()
    }

    @Synchronized
    override fun ack(stateHolder: PlaybackStateHolder, ack: StateAck) {
        if (this.stateHolder !== stateHolder) {
            Timber.w("Given internal player did not match current internal player")
            return
        }

        when (ack) {
            is StateAck.IndexMoved -> {
                val rawQueue = stateHolder.resolveQueue()
                stateMirror = stateMirror.copy(
                    index = rawQueue.resolveIndex(),
                    rawQueue = rawQueue
                )
                listeners.forEach { it.onIndexMoved(stateMirror.index) }
            }

            is StateAck.PlayNext -> {
                val rawQueue = stateHolder.resolveQueue()
                stateMirror = stateMirror.copy(
                    queue = rawQueue.resolveSongs(),
                    rawQueue = rawQueue
                )
                listeners.forEach {
                    it.onQueueChanged(stateMirror.queue, stateMirror.index)
                }
            }

            is StateAck.AddToQueue -> {
                val rawQueue = stateHolder.resolveQueue()
                stateMirror = stateMirror.copy(
                    queue = rawQueue.resolveSongs(),
                    rawQueue = rawQueue
                )
                listeners.forEach {
                    it.onQueueChanged(stateMirror.queue, stateMirror.index)
                }
            }

            is StateAck.Move -> {
                val rawQueue = stateHolder.resolveQueue()
                val newIndex = rawQueue.resolveIndex()
                stateMirror = stateMirror.copy(
                    queue = rawQueue.resolveSongs(),
                    index = newIndex,
                    rawQueue = rawQueue,
                )
                listeners.forEach {
                    it.onQueueChanged(stateMirror.queue, stateMirror.index)
                }
            }

            is StateAck.Remove -> {
                val rawQueue = stateHolder.resolveQueue()
                val newIndex = rawQueue.resolveIndex()
                stateMirror = stateMirror.copy(
                    queue = rawQueue.resolveSongs(),
                    index = newIndex,
                    rawQueue = rawQueue,
                )
                listeners.forEach {
                    it.onQueueChanged(stateMirror.queue, stateMirror.index)
                }
            }

            is StateAck.QueueReordered -> {
                val rawQueue = stateHolder.resolveQueue()
                stateMirror = stateMirror.copy(
                    queue = rawQueue.resolveSongs(),
                    index = rawQueue.resolveIndex(),
                    isShuffled = rawQueue.isShuffled,
                    rawQueue = rawQueue,
                )
                listeners.forEach {
                    it.onQueueReordered(
                        stateMirror.queue,
                        stateMirror.index,
                        stateMirror.isShuffled,
                    )
                }
            }

            is StateAck.NewPlayback -> {
                val rawQueue = stateHolder.resolveQueue()
                stateMirror = stateMirror.copy(
                    queue = rawQueue.resolveSongs(),
                    index = rawQueue.resolveIndex(),
                    isShuffled = rawQueue.isShuffled,
                    rawQueue = rawQueue,
                )
                listeners.forEach {
                    it.onNewPlayback(
                        stateMirror.queue,
                        stateMirror.index,
                        stateMirror.isShuffled,
                    )
                }
            }

            is StateAck.ProgressionChanged -> {
                stateMirror = stateMirror.copy(progression = stateHolder.progression)
                listeners.forEach { it.onProgressionChanged(stateMirror.progression) }
            }

            is StateAck.RepeatModeChanged -> {
                stateMirror = stateMirror.copy(repeatMode = stateHolder.repeatMode)
                listeners.forEach { it.onRepeatModeChanged(stateMirror.repeatMode) }
            }

            is StateAck.SessionEnded -> {
                listeners.forEach { it.onSessionEnded() }
            }
        }
    }
}
