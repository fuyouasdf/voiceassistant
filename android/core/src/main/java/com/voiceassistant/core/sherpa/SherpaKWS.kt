package com.voiceassistant.core.sherpa

/**
 * Interface for Keyword Spotting (Wake Word) engine
 */
interface SherpaKWS {

    /**
     * Initialize the KWS engine with model
     * @param modelPath Path to the model directory
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean

    /**
     * Process audio chunk and detect wake word
     * @param audio Audio samples (16kHz, float)
     * @return true if wake word detected
     */
    fun process(audio: FloatArray): Boolean

    /**
     * Set detection sensitivity (0.0 - 1.0)
     */
    fun setSensitivity(sensitivity: Float)

    /**
     * Release resources
     */
    fun release()
}
