package com.voiceassistant.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
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
            AudioFormat.ENCODING_PCM_FLOAT
        )
        
        audioRecord = createAudioRecord(minBuffer)
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord initialization failed")
            return
        }
        
        audioRecord?.startRecording()
        
        recordingJob = scope.launch {
            val buffer = FloatArray(bufferSize)
            
            while (isActive) {
                val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioRecord?.read(buffer, 0, bufferSize, AudioRecord.READ_BLOCKING) ?: 0
                } else {
                    @Suppress("DEPRECATION")
                    audioRecord?.read(buffer, 0, bufferSize) ?: 0
                }
                
                if (read > 0) {
                    onAudioChunk(buffer.copyOf(read))
                }
            }
        }
    }
    
    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
    private fun createAudioRecord(minBuffer: Int): AudioRecord? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+ use Builder
            try {
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
                Timber.e(e, "Failed to create AudioRecord with Builder")
                null
            }
        } else {
            // API 23 fallback
            @Suppress("DEPRECATION")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
        }
    }
}
