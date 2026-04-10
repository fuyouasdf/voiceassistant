/*
 * Copyright (c) 2024 Auxio Project
 * PlaybackViewModel.kt is part of Auxio.
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

package com.voiceassistant.app.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.playback.state.PlaybackStateManager
import com.voiceassistant.core.playback.state.Progression
import com.voiceassistant.core.playback.state.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI-facing wrapper around [PlaybackStateManager].
 * Exposes playback state as [StateFlow] and provides control methods for the UI.
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val playbackManager: PlaybackStateManager,
) : ViewModel(), PlaybackStateManager.Listener {

    private val _song = MutableStateFlow<MusicItem?>(null)
    val song: StateFlow<MusicItem?> = _song.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionDs = MutableStateFlow(0L)
    val positionDs: StateFlow<Long> = _positionDs.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _queue = MutableStateFlow<List<MusicItem>>(emptyList())
    val queue: StateFlow<List<MusicItem>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _durationDs = MutableStateFlow(0L)
    val durationDs: StateFlow<Long> = _durationDs.asStateFlow()

    private var positionUpdateJob: Job? = null

    init {
        playbackManager.addListener(this)
        startPositionUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.removeListener(this)
        positionUpdateJob?.cancel()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                val progression = playbackManager.progression
                _positionDs.value = progression.calculateElapsedPositionMs() / 100 // deci-seconds
                _isPlaying.value = progression.isPlaying

                // Update duration from current song
                val currentSong = _song.value
                if (currentSong != null) {
                    _durationDs.value = currentSong.duration * 10L // seconds to deci-seconds
                }

                delay(100)
            }
        }
    }

    // --- PlaybackStateManager.Listener implementation ---

    override fun onIndexMoved(index: Int) {
        _queueIndex.value = index
        _song.value = _queue.value.getOrNull(index)
        Timber.d("Index moved to $index, song: ${_song.value?.title}")
    }

    override fun onQueueChanged(queue: List<MusicItem>, index: Int) {
        _queue.value = queue
        _queueIndex.value = index
        _song.value = queue.getOrNull(index)
        Timber.d("Queue changed: ${queue.size} items, index: $index")
    }

    override fun onQueueReordered(queue: List<MusicItem>, index: Int, isShuffled: Boolean) {
        _queue.value = queue
        _queueIndex.value = index
        _isShuffled.value = isShuffled
        _song.value = queue.getOrNull(index)
        Timber.d("Queue reordered, shuffled: $isShuffled")
    }

    override fun onNewPlayback(queue: List<MusicItem>, index: Int, isShuffled: Boolean) {
        _queue.value = queue
        _queueIndex.value = index
        _isShuffled.value = isShuffled
        _song.value = queue.getOrNull(index)
        Timber.d("New playback: ${queue.size} items, shuffled: $isShuffled")
    }

    override fun onProgressionChanged(progression: Progression) {
        _isPlaying.value = progression.isPlaying
        _positionDs.value = progression.calculateElapsedPositionMs() / 100
    }

    override fun onRepeatModeChanged(repeatMode: RepeatMode) {
        _repeatMode.value = repeatMode
        Timber.d("Repeat mode changed to $repeatMode")
    }

    override fun onSessionEnded() {
        _song.value = null
        _isPlaying.value = false
        _queue.value = emptyList()
        _queueIndex.value = -1
        Timber.d("Session ended")
    }

    // --- Playback controls ---

    fun play() {
        playbackManager.playing(true)
    }

    fun pause() {
        playbackManager.playing(false)
    }

    fun togglePlaying() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        playbackManager.next()
    }

    fun prev() {
        playbackManager.prev()
    }

    fun seekTo(positionDs: Long) {
        playbackManager.seekTo(positionDs * 100) // deci-seconds to milliseconds
    }

    fun seekToMs(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun goto(index: Int) {
        playbackManager.goto(index)
    }

    fun toggleShuffle() {
        playbackManager.shuffled(!_isShuffled.value)
    }

    fun toggleRepeat() {
        val newMode = _repeatMode.value.increment()
        playbackManager.repeatMode(newMode)
    }

    fun playSong(item: MusicItem) {
        val index = _queue.value.indexOf(item)
        if (index >= 0) {
            goto(index)
        } else {
            // Play the song as a new queue
            playbackManager.play(listOf(item), 0, false)
        }
    }

    fun playAll(items: List<MusicItem>, startIndex: Int = 0, shuffled: Boolean = false) {
        if (items.isEmpty()) return
        playbackManager.play(items, startIndex, shuffled)
    }

    fun playNext(item: MusicItem) {
        playbackManager.playNext(item)
    }

    fun addToQueue(item: MusicItem) {
        playbackManager.addToQueue(item)
    }

    fun moveQueueItem(src: Int, dst: Int) {
        playbackManager.moveQueueItem(src, dst)
    }

    fun removeQueueItem(at: Int) {
        playbackManager.removeQueueItem(at)
    }

    fun stop() {
        playbackManager.endSession()
    }
}
