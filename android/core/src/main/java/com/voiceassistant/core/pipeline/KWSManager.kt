package com.voiceassistant.core.pipeline

import com.voiceassistant.core.audio.AudioCapture
import com.voiceassistant.core.sherpa.SherpaKWS
import timber.log.Timber

/**
 * Manages Keyword Spotting (wake word) detection lifecycle.
 *
 * Responsibilities:
 * - Initialize KWS model
 * - Start continuous wake word monitoring
 * - Detect wake word and signal to pipeline
 * - Clean up on wake word trigger
 *
 * This class does NOT manage pipeline states - it only handles KWS-specific logic.
 */
class KWSManager(
    private val kws: SherpaKWS,
    private val audioCapture: AudioCapture,
    private val modelPath: String
) {
    private var isInitialized = false

    /**
     * Initialize KWS model. Non-blocking - failures are logged but not thrown.
     */
    fun initialize() {
        if (isInitialized) return

        try {
            val result = kws.initialize("$modelPath/kws")
            Timber.d("KWS initialized: $result")
            isInitialized = true
        } catch (e: Exception) {
            // KWS failure is non-fatal - app can work without wake word
            Timber.e(e, "KWS initialization failed - continuing without wake word")
        }
    }

    fun isInitialized(): Boolean = isInitialized

    /**
     * Start listening for wake word.
     * @param onWakeWordDetected Called when wake word is detected
     */
    fun startListening(onWakeWordDetected: () -> Unit) {
        if (!isInitialized) {
            Timber.w("Cannot start KWS - not initialized")
            return
        }

        if (audioCapture.isRecording) {
            audioCapture.stop()
        }

        var wakeWordTriggered = false

        try {
            audioCapture.start { audioChunk ->
                try {
                    if (wakeWordTriggered) return@start

                    if (kws.process(audioChunk)) {
                        wakeWordTriggered = true
                        Timber.d("Wake word detected!")
                        audioCapture.stop()
                        onWakeWordDetected()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in KWS audio processing")
                }
            }
            Timber.d("KWS listening started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start KWS listening")
        }
    }

    fun stop() {
        audioCapture.stop()
    }
}
