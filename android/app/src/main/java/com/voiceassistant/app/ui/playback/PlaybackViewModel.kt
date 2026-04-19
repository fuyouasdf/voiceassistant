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
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.QueueSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI-facing wrapper around [MusicPlayer].
 * Exposes playback state as [StateFlow] and provides control methods for the UI.
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val musicPlayer: MusicPlayer,
) : ViewModel() {

    private val _song = MutableStateFlow<MusicItem?>(null)
    val song: StateFlow<MusicItem?> = _song.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionDs = MutableStateFlow(0L)
    val positionDs: StateFlow<Long> = _positionDs.asStateFlow()

    private val _repeatMode = MutableStateFlow<com.voiceassistant.core.music.RepeatMode>(com.voiceassistant.core.music.RepeatMode.OFF)
    val repeatMode: StateFlow<com.voiceassistant.core.music.RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _queue = MutableStateFlow<List<MusicItem>>(emptyList())
    val queue: StateFlow<List<MusicItem>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _durationDs = MutableStateFlow(0L)
    val durationDs: StateFlow<Long> = _durationDs.asStateFlow()

    private val _isTempPlaylistActive = MutableStateFlow(false)
    val isTempPlaylistActive: StateFlow<Boolean> = _isTempPlaylistActive.asStateFlow()

    private var positionUpdateJob: Job? = null

    init {
        observeMusicPlayerState()
        startPositionUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
    }

    private fun observeMusicPlayerState() {
        viewModelScope.launch {
            musicPlayer.state.collectLatest { state ->
                _isPlaying.value = state.isPlaying
                _queue.value = state.playlist
                _queueIndex.value = state.currentIndex
                _isShuffled.value = state.isShuffleEnabled
                _repeatMode.value = state.repeatMode

                // Find current song from playlist
                val currentSong = state.playlist.getOrNull(state.currentIndex)
                _song.value = currentSong

                // Update duration from current song
                if (currentSong != null) {
                    _durationDs.value = currentSong.duration * 10L // seconds to deci-seconds
                }

                // Update temp playlist state
                _isTempPlaylistActive.value = musicPlayer.isTempPlaylistActive

                Timber.d("MusicPlayer state updated: isPlaying=${state.isPlaying}, song=${currentSong?.title}, index=${state.currentIndex}")
            }
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                val state = musicPlayer.getState()
                _positionDs.value = state.currentPosition / 100 // ms to deci-seconds

                delay(100)
            }
        }
    }

    // --- Playback controls ---

    fun play() {
        musicPlayer.resume()
    }

    fun pause() {
        musicPlayer.pause()
    }

    fun togglePlaying() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        musicPlayer.playNext()
    }

    fun prev() {
        musicPlayer.playPrevious()
    }

    fun seekTo(positionDs: Long) {
        musicPlayer.seekTo(positionDs * 100) // deci-seconds to milliseconds
    }

    fun seekToMs(positionMs: Long) {
        musicPlayer.seekTo(positionMs)
    }

    fun goto(index: Int) {
        musicPlayer.seekToIndex(index)
    }

    fun toggleShuffle() {
        musicPlayer.toggleShuffle()
    }

    fun toggleRepeat() {
        musicPlayer.toggleRepeat()
    }

    fun playSong(item: MusicItem) {
        musicPlayer.play(item)
    }

    fun playAll(items: List<MusicItem>, startIndex: Int = 0, shuffled: Boolean = false) {
        if (items.isEmpty()) return
        musicPlayer.playPlaylist(items, startIndex, QueueSource.PLAYLIST)
    }

    fun playAsTempPlaylist(items: List<MusicItem>, startIndex: Int = 0, source: QueueSource = QueueSource.PLAYLIST) {
        if (items.isEmpty()) return
        musicPlayer.playAsTempPlaylist(items, startIndex, source)
    }

    fun exitTempPlaylist() {
        musicPlayer.restoreOriginalPlaylist()
    }

    fun playNext(item: MusicItem) {
        // MusicPlayer.playNext() doesn't support adding item to queue
        // This would require implementing queue management in MusicPlayer
    }

    fun addToQueue(item: MusicItem) {
        // MusicPlayer doesn't support adding items to queue
    }

    fun moveQueueItem(src: Int, dst: Int) {
        // MusicPlayer doesn't expose this directly
    }

    fun removeQueueItem(at: Int) {
        // MusicPlayer doesn't expose this directly
    }

    fun stop() {
        musicPlayer.stop()
    }
}
