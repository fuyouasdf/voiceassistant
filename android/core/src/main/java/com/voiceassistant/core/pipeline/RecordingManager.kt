package com.voiceassistant.core.pipeline

import com.voiceassistant.core.audio.AudioCapture
import timber.log.Timber

/**
 * Manages audio recording lifecycle and buffering.
 *
 * Responsibilities:
 * - Start/stop audio capture
 * - Buffer incoming audio chunks
 * - Detect end of speech (timeout-based for now)
 * - Provide recorded audio as a single FloatArray
 *
 * This class is pure and does not know about pipeline states.
 */
class RecordingManager(
    private val audioCapture: AudioCapture,
    private val config: PipelineConfig
) {
    private val audioBuffer = mutableListOf<FloatArray>()

    /**
     * Start recording. Audio chunks are collected in buffer.
     * @param onComplete Called when recording is done, with the full audio
     */
    fun startRecording(onComplete: (FloatArray) -> Unit) {
        if (audioCapture.isRecording) {
            Timber.d("AudioCapture is recording, stopping first")
            audioCapture.stop()
        }

        audioBuffer.clear()

        try {
            audioCapture.start { audioChunk ->
                try {
                    audioBuffer.add(audioChunk)

                    // Timeout-based stop (~10 seconds max)
                    val maxBufferSize = config.sampleRate * 10 / config.frameSize
                    if (audioBuffer.size > maxBufferSize) {
                        val speechAudio = audioBuffer.flattenToFloatArray()
                        Timber.d("Recording stopped: max duration reached, audioSize=${speechAudio.size}")
                        audioCapture.stop()
                        onComplete(speechAudio)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in recording callback")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
        }
    }

    fun stop() {
        audioCapture.stop()
    }

    fun isRecording(): Boolean = audioCapture.isRecording

    private fun List<FloatArray>.flattenToFloatArray(): FloatArray {
        val size = sumOf { it.size }
        val result = FloatArray(size)
        var index = 0
        for (array in this) {
            for (value in array) {
                result[index++] = value
            }
        }
        return result
    }
}
