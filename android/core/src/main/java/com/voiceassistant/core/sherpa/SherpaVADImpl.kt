package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber

class SherpaVADImpl(private val context: Context) : SherpaVAD {

    private var vad: Vad? = null
    private var isInitialized = false
    private val windowSize = 512

    override fun initialize(modelPath: String): Boolean {
        return try {
            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "silero_vad.onnx",
                    threshold = 0.3F,  // Lower threshold for better sensitivity
                    minSilenceDuration = 0.5F,  // Longer silence to avoid cutting off
                    minSpeechDuration = 0.25F,
                    windowSize = windowSize,
                ),
                sampleRate = 16000,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )


            vad = Vad(
                assetManager = context.assets,
                config = config
            )
            isInitialized = true
            Timber.d("VAD initialized with windowSize=$windowSize")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize VAD")
            false
        }
    }

    override fun process(audio: FloatArray): Boolean {
        return try {
            val v = vad ?: return false
            // VAD requires exactly windowSize samples per call
            // AudioCapture provides 320 samples (20ms), we need to buffer them
            v.acceptWaveform(audio)
            val isSpeech = v.isSpeechDetected()
            Timber.v("VAD process: ${audio.size} samples, isSpeech=$isSpeech")
            isSpeech
        } catch (e: Exception) {
            Timber.e(e, "VAD process error")
            false
        }
    }

    override fun reset() {
        vad?.clear()
        Timber.d("VAD reset")
    }

    override fun release() {
        vad?.release()
        vad = null
        isInitialized = false
        Timber.d("VAD released")
    }
}