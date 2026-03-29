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
     * Reload keywords from a new keywords file path.
     * Thread-safe: acquires lock, releases old KeywordSpotter, creates new one.
     * @param keywordsFilePath Absolute path to the new keywords.txt
     * @param threshold Optional new threshold. If null, keeps current threshold.
     * @return true if reload successful
     */
    fun reloadKeywords(keywordsFilePath: String, threshold: Float? = null): Boolean

    /**
     * Release resources
     */
    fun release()
}
