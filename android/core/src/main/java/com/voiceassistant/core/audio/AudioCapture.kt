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
    private val bufferSize: Int = 512
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // 同步锁，防止并发启动/停止
    private val lock = Any()

    // 当前录音状态
    @Volatile
    var isRecording = false
        private set

    fun start(onAudioChunk: (FloatArray) -> Unit) {
        synchronized(lock) {
            // 如果已经在录音，先停止
            if (isRecording) {
                Timber.d("AudioCapture already recording, stopping first")
                stopInternal()
            }

            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            audioRecord = createAudioRecord(minBuffer)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("AudioRecord initialization failed")
                return
            }

            try {
                audioRecord?.startRecording()
                isRecording = true
                Timber.d("AudioCapture started recording")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start AudioRecord recording")
                audioRecord?.release()
                audioRecord = null
                return
            }

            recordingJob = scope.launch {
                val buffer = FloatArray(bufferSize)

                while (isActive && isRecording) {
                    try {
                        val read = audioRecord?.read(buffer, 0, bufferSize, AudioRecord.READ_BLOCKING) ?: 0

                        if (read > 0) {
                            onAudioChunk(buffer.copyOf(read))
                        } else if (read < 0) {
                            Timber.w("AudioRecord read error: $read")
                            break
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading audio")
                        break
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            stopInternal()
        }
    }

    private fun stopInternal() {
        if (!isRecording && audioRecord == null) {
            Timber.d("AudioCapture already stopped")
            return
        }

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            // 只有在 recording 状态下才调用 stop
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
                Timber.d("AudioCapture stopped")
            }
        } catch (e: IllegalStateException) {
            Timber.w("AudioRecord already stopped: ${e.message}")
        } finally {
            try {
                audioRecord?.release()
                Timber.d("AudioCapture released")
            } catch (e: Exception) {
                Timber.w("Error releasing AudioRecord: ${e.message}")
            }
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
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
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
