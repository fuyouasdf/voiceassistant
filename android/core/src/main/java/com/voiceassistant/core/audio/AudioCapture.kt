package com.voiceassistant.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Audio capture with API 23+ compatibility
 */
class AudioCapture(
    private val sampleRate: Int = 16000,
    private val bufferSize: Int = 320
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun start(onAudioChunk: (FloatArray) -> Unit) {
        stop() // Stop any existing recording

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = createAudioRecord(minBuffer)

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()

        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize)

            while (isActive) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0

                if (read > 0) {
                    // Convert ShortArray to FloatArray
                    val floatBuffer = FloatArray(read) { i ->
                        buffer[i] / 32768.0f
                    }
                    onAudioChunk(floatBuffer)
                }
            }
        }
    }
    
    fun stop() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
        } catch (e: IllegalStateException) {
            Timber.w(e, "AudioRecord already stopped")
        } finally {
            audioRecord?.release()
            audioRecord = null
        }
    }
    
    private fun createAudioRecord(minBuffer: Int): AudioRecord? {
        return try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .build()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create AudioRecord")
            null
        }
    }
}
