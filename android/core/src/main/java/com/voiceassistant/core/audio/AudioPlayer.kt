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

package com.voiceassistant.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Audio player for playing TTS audio with streaming support and channel reuse
 */
class AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 44100
    private var isStreamPrepared: Boolean = false
    private var playJob: Job? = null

    /**
     * Prepare audio track for streaming playback
     * Call this before writing samples incrementally
     */
    fun prepareStream(sampleRate: Int): Boolean {
        return try {
            currentSampleRate = sampleRate
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // Release existing track if any
            release()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 4)  // Larger buffer for streaming
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isStreamPrepared = true
            Timber.d("AudioPlayer: stream prepared at $sampleRate Hz")
            true
        } catch (e: Exception) {
            Timber.e(e, "AudioPlayer: failed to prepare stream")
            false
        }
    }

    /**
     * Write samples to the audio track (for streaming)
     * Returns number of bytes written
     */
    fun write(samples: FloatArray): Int {
        val track = audioTrack ?: return 0
        return try {
            val shortSamples = ShortArray(samples.size) { i ->
                (samples[i] * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            track.write(shortSamples, 0, shortSamples.size)
        } catch (e: Exception) {
            Timber.e(e, "AudioPlayer: error writing samples")
            0
        }
    }

    /**
     * Stop and finalize the current stream
     */
    fun finalizeStream() {
        try {
            audioTrack?.stop()
            isStreamPrepared = false
        } catch (e: Exception) {
            Timber.w(e, "AudioPlayer: error stopping stream")
        }
    }

    /**
     * Release the audio track
     */
    fun release() {
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Timber.w(e, "AudioPlayer: error releasing")
        } finally {
            audioTrack = null
            isStreamPrepared = false
        }
    }

    /**
     * Play audio samples with specified sample rate and call onComplete when done
     * Reuses an existing prepared AudioTrack if available and compatible
     */
    fun play(samples: FloatArray, sampleRate: Int = 44100, onComplete: () -> Unit) {
        if (samples.isEmpty()) {
            Timber.w("AudioPlayer: no samples to play")
            onComplete()
            return
        }

        val reusePreparedTrack = audioTrack != null && isStreamPrepared && currentSampleRate == sampleRate

        if (!reusePreparedTrack) {
            // Create new AudioTrack only if no prepared track is available
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // Release existing track if incompatible
            release()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isStreamPrepared = false // We're managing it manually now
        }

        // Play in background using coroutine
        playJob = kotlinx.coroutines.MainScope().launch {
            try {
                // Convert Float samples to Short array (PCM 16-bit)
                val shortSamples = ShortArray(samples.size) { i ->
                    (samples[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                // Write audio on IO dispatcher to avoid blocking main thread
                withContext(Dispatchers.IO) {
                    audioTrack?.write(shortSamples, 0, shortSamples.size)
                }

                if (!reusePreparedTrack) {
                    audioTrack?.stop()
                    audioTrack?.release()
                    audioTrack = null
                }

                // Call completion
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "Error playing audio")
                if (!reusePreparedTrack) {
                    audioTrack?.release()
                    audioTrack = null
                }
                onComplete()
            }
        }
    }

    /**
     * Play audio samples asynchronously
     */
    suspend fun playAsync(samples: FloatArray): Unit = withContext(Dispatchers.IO) {
        play(samples) {}
    }

    /**
     * Stop current playback
     */
    fun stop() {
        release()
    }

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    /**
     * Check if stream is prepared
     */
    fun isStreamPrepared(): Boolean = isStreamPrepared
}