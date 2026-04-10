/*
 * Copyright (c) 2024 Auxio Project
 * ExoPlaybackStateHolder.kt is part of Auxio.
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

package com.voiceassistant.core.playback.service

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.playback.state.PlaybackStateHolder
import com.voiceassistant.core.playback.state.PlaybackStateManager
import com.voiceassistant.core.playback.state.Progression
import com.voiceassistant.core.playback.state.RawQueue
import com.voiceassistant.core.playback.state.RepeatMode
import com.voiceassistant.core.playback.state.StateAck
import timber.log.Timber

/**
 * ExoPlayer-based implementation of [PlaybackStateHolder].
 * Handles all audio playback using ExoPlayer with proper state management.
 */
class ExoPlaybackStateHolder(
    private val context: Context,
    private val playbackManager: PlaybackStateManager,
) : PlaybackStateHolder, Player.Listener {

    private var player: ExoPlayer? = null
    private var currentQueue: List<MusicItem> = emptyList()
    private var currentShuffled: Boolean = false

    var sessionOngoing = false
        private set

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    fun attach() {
        if (player == null) {
            player = createPlayer()
        }
        player?.addListener(this)
        playbackManager.registerStateHolder(this)
    }

    fun release() {
        playbackManager.unregisterStateHolder(this)
        player?.removeListener(this)
        player?.release()
        player = null
    }

    private fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    override val progression: Progression
        get() {
            val p = player ?: return Progression.nil()
            val duration = p.duration.coerceAtLeast(0)
            val position = p.currentPosition.coerceAtLeast(0).coerceAtMost(duration)
            return Progression.from(p.playWhenReady, p.isPlaying, position)
        }

    override val repeatMode: RepeatMode
        get() {
            val p = player ?: return RepeatMode.NONE
            return when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> RepeatMode.NONE
                Player.REPEAT_MODE_ONE -> RepeatMode.TRACK
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.NONE
            }
        }

    override val audioSessionId: Int
        get() = player?.audioSessionId ?: 0

    override fun resolveQueue(): RawQueue {
        val p = player ?: return RawQueue.nil()
        val heap = currentQueue
        val shuffledMapping =
            if (currentShuffled && p.shuffleModeEnabled) {
                p.unscrambleQueueIndices()
            } else {
                emptyList()
            }
        return RawQueue(heap, shuffledMapping, p.currentMediaItemIndex)
    }

    override fun newPlayback(queue: List<MusicItem>, startIndex: Int, shuffled: Boolean) {
        if (queue.isEmpty()) return

        val p = player ?: return
        currentQueue = queue
        currentShuffled = shuffled

        p.shuffleModeEnabled = shuffled

        val mediaItems = queue.map { it.toMediaItem() }
        p.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)

        p.prepare()
        p.play()
        sessionOngoing = true

        playbackManager.ack(this, StateAck.NewPlayback)
    }

    override fun playing(playing: Boolean) {
        player?.playWhenReady = playing
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        playbackManager.ack(this, StateAck.ProgressionChanged)
    }

    override fun repeatMode(repeatMode: RepeatMode) {
        val p = player ?: return
        p.repeatMode =
            when (repeatMode) {
                RepeatMode.NONE -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.TRACK -> Player.REPEAT_MODE_ONE
            }
        playbackManager.ack(this, StateAck.RepeatModeChanged)
    }

    override fun next() {
        val p = player ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNext()
        } else if (p.repeatMode == Player.REPEAT_MODE_ALL) {
            p.seekTo(0, C.TIME_UNSET)
        }
        playbackManager.ack(this, StateAck.IndexMoved)
    }

    override fun prev() {
        val p = player ?: return
        if (p.currentPosition > 3000) {
            // If position > 3 seconds, go to beginning of current track
            p.seekTo(0)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPrevious()
        } else if (p.repeatMode == Player.REPEAT_MODE_ALL) {
            p.seekTo(p.mediaItemCount - 1, C.TIME_UNSET)
        } else {
            p.seekTo(0)
        }
        playbackManager.ack(this, StateAck.IndexMoved)
    }

    override fun goto(index: Int) {
        val p = player ?: return
        val indices = p.unscrambleQueueIndices()
        if (indices.isEmpty()) return

        val trueIndex = indices[index]
        p.seekTo(trueIndex, C.TIME_UNSET)
        playbackManager.ack(this, StateAck.IndexMoved)
    }

    override fun playNext(items: List<MusicItem>, ack: StateAck.PlayNext) {
        val p = player ?: return
        if (items.isEmpty()) return

        currentQueue = currentQueue.toMutableList().apply {
            val insertIndex = (ack.at).coerceIn(0, size)
            add(insertIndex, items.first())
        }

        val nextIndex =
            if (p.currentTimeline.isEmpty) {
                C.INDEX_UNSET
            } else {
                p.currentTimeline.getNextWindowIndex(
                    p.currentMediaItemIndex,
                    Player.REPEAT_MODE_OFF,
                    p.shuffleModeEnabled,
                )
            }

        if (nextIndex == C.INDEX_UNSET) {
            p.addMediaItems(items.map { it.toMediaItem() })
        } else {
            p.addMediaItems(nextIndex, items.map { it.toMediaItem() })
        }

        playbackManager.ack(this, ack)
    }

    override fun addToQueue(items: List<MusicItem>, ack: StateAck.AddToQueue) {
        val p = player ?: return
        if (items.isEmpty()) return

        currentQueue = currentQueue + items
        p.addMediaItems(items.map { it.toMediaItem() })
        playbackManager.ack(this, ack)
    }

    override fun move(from: Int, to: Int, ack: StateAck.Move) {
        val p = player ?: return
        val indices = p.unscrambleQueueIndices()
        if (indices.isEmpty()) return

        val trueFrom = indices[from]
        val trueTo = indices[to]

        when {
            trueFrom > trueTo -> {
                p.moveMediaItem(trueFrom, trueTo)
                p.moveMediaItem(trueTo + 1, trueFrom)
            }
            trueTo > trueFrom -> {
                p.moveMediaItem(trueFrom, trueTo)
                p.moveMediaItem(trueTo - 1, trueFrom)
            }
        }

        // Update local queue
        val mutableQueue = currentQueue.toMutableList()
        val item = mutableQueue.removeAt(from)
        mutableQueue.add(to, item)
        currentQueue = mutableQueue

        playbackManager.ack(this, ack)
    }

    override fun remove(at: Int, ack: StateAck.Remove) {
        val p = player ?: return
        val indices = p.unscrambleQueueIndices()
        if (indices.isEmpty()) return

        val trueIndex = indices[at]

        // Update local queue
        if (at in currentQueue.indices) {
            currentQueue = currentQueue.toMutableList().apply { removeAt(at) }
        }

        p.removeMediaItem(trueIndex)
        playbackManager.ack(this, ack)
    }

    override fun shuffled(shuffled: Boolean) {
        val p = player ?: return
        p.shuffleModeEnabled = shuffled
        currentShuffled = shuffled
        playbackManager.ack(this, StateAck.QueueReordered)
    }

    override fun applySavedState(
        rawQueue: RawQueue,
        positionMs: Long,
        repeatMode: RepeatMode,
        ack: StateAck.NewPlayback?,
    ) {
        val p = player ?: return

        if (rawQueue.heap.isEmpty()) {
            reset(ack ?: StateAck.NewPlayback)
            return
        }

        currentQueue = rawQueue.heap
        currentShuffled = rawQueue.isShuffled

        p.setMediaItems(rawQueue.heap.map { it.toMediaItem() })
        p.shuffleModeEnabled = rawQueue.isShuffled

        p.seekTo(rawQueue.heapIndex, C.TIME_UNSET)
        p.prepare()
        p.pause()

        this.repeatMode(repeatMode)
        p.seekTo(positionMs)

        ack?.let { playbackManager.ack(this, it) }
    }

    override fun endSession() {
        playbackManager.playing(false)
        sessionOngoing = false
        playbackManager.ack(this, StateAck.SessionEnded)
    }

    override fun reset(ack: StateAck.NewPlayback) {
        player?.setMediaItems(listOf())
        currentQueue = emptyList()
        currentShuffled = false
        sessionOngoing = false
        playbackManager.ack(this, ack)
    }

    // --- PLAYER OVERRIDES ---

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (player?.playWhenReady == true) {
            sessionOngoing = true
            openAudioEffectSession()
        } else {
            closeAudioEffectSession()
        }
        playbackManager.ack(this, StateAck.ProgressionChanged)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playbackManager.ack(this, StateAck.ProgressionChanged)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            if (player?.repeatMode != Player.REPEAT_MODE_ALL) {
                goto(0)
                player?.pause()
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playbackManager.ack(this, StateAck.IndexMoved)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        playbackManager.ack(this, StateAck.ProgressionChanged)
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.e("Player error: ${error.message}")
        player?.prepare()
        next()
    }

    private fun openAudioEffectSession() {
        try {
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open audio effect session")
        }
    }

    private fun closeAudioEffectSession() {
        try {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to close audio effect session")
        }
    }

    private fun Player.unscrambleQueueIndices(): List<Int> {
        val timeline = currentTimeline
        if (timeline.isEmpty) {
            return emptyList()
        }
        val queue = mutableListOf<Int>()

        val currentMediaItemIndex = currentMediaItemIndex
        queue.add(currentMediaItemIndex)

        var firstMediaItemIndex = currentMediaItemIndex
        var lastMediaItemIndex = currentMediaItemIndex
        val shuffleModeEnabled = shuffleModeEnabled

        while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET)) {
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                lastMediaItemIndex =
                    timeline.getNextWindowIndex(
                        lastMediaItemIndex,
                        Player.REPEAT_MODE_OFF,
                        shuffleModeEnabled,
                    )
                if (lastMediaItemIndex != C.INDEX_UNSET) {
                    queue.add(lastMediaItemIndex)
                }
            }
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                firstMediaItemIndex =
                    timeline.getPreviousWindowIndex(
                        firstMediaItemIndex,
                        Player.REPEAT_MODE_OFF,
                        shuffleModeEnabled,
                    )
                if (firstMediaItemIndex != C.INDEX_UNSET) {
                    queue.add(0, firstMediaItemIndex)
                }
            }
        }

        return queue
    }

    private fun MusicItem.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(id)
            .build()
    }
}
