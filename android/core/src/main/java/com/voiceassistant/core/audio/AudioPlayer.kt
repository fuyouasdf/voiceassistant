package com.voiceassistant.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Audio player for playing TTS audio
 */
class AudioPlayer {

    companion object {
        private const val SAMPLE_RATE = 16000
    }

    private var audioTrack: AudioTrack? = null

    /**
     * Play audio samples and call onComplete when done
     */
    fun play(samples: FloatArray, onComplete: () -> Unit) {
        if (samples.isEmpty()) {
            Timber.w("AudioPlayer: no samples to play")
            onComplete()
            return
        }

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        // Play in background
        Thread {
            try {
                // Write in chunks to allow streaming
                val chunkSize = 4096
                var offset = 0

                while (offset < samples.size) {
                    val length = minOf(chunkSize, samples.size - offset)
                    val chunk = samples.copyOfRange(offset, offset + length)

                    // Convert Float to ByteArray (Float = 4 bytes)
                    val byteBuffer = ByteArray(length * 4)
                    for (i in chunk.indices) {
                        val bits = java.lang.Float.floatToIntBits(chunk[i])
                        byteBuffer[i * 4] = (bits and 0xFF).toByte()
                        byteBuffer[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
                        byteBuffer[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
                        byteBuffer[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
                    }

                    audioTrack?.write(byteBuffer, 0, byteBuffer.size)
                    offset += length
                }

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null

                // Call completion on main thread
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "Error playing audio")
                audioTrack?.release()
                audioTrack = null
                onComplete()
            }
        }.start()
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
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Timber.w(e, "Error stopping audio")
        } finally {
            audioTrack = null
        }
    }

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
}