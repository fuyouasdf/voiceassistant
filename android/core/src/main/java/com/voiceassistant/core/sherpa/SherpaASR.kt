package com.voiceassistant.core.sherpa

/**
 * Interface for Automatic Speech Recognition (ASR) engine
 */
interface SherpaASR {

    /**
     * Initialize the ASR engine with model
     * @param modelPath Path to the model directory
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean

    /**
     * Recognize speech from audio
     * @param audio Audio samples (16kHz, float)
     * @return Recognized text
     */
    suspend fun recognize(audio: FloatArray): String

    /**
     * Reset the recognizer state
     */
    fun reset()

    /**
     * Release resources
     */
    fun release()
}
