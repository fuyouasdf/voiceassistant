package com.voiceassistant.core.sherpa

/**
 * Interface for Voice Activity Detection (VAD) engine
 */
interface SherpaVAD {

    /**
     * Initialize the VAD engine with model
     * @param modelPath Path to the model directory
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean

    /**
     * Process audio chunk and detect speech
     * @param audio Audio samples (16kHz, float)
     * @return true if speech detected
     */
    fun process(audio: FloatArray): Boolean

    /**
     * Reset VAD state
     */
    fun reset()

    /**
     * Release resources
     */
    fun release()
}
