package com.voiceassistant.core.sherpa

/**
 * Interface for Text-to-Speech (TTS) engine
 */
interface SherpaTTS {

    /**
     * Initialize the TTS engine with model
     * @param modelPath Path to the model directory
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean

    /**
     * Synthesize text to audio
     * @param text Text to synthesize
     * @return Audio samples (float array)
     */
    fun synthesize(text: String): FloatArray

    /**
     * Stop current synthesis
     */
    fun stop()

    /**
     * Release resources
     */
    fun release()
}
