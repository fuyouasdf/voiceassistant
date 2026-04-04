package com.voiceassistant.core.pipeline

import timber.log.Timber

/**
 * Result of wake word detection after applying per-keyword thresholds and cooldown
 */
data class WakeWordTriggered(
    val keyword: String,
    val confidence: Float,
    val response: String
)

/**
 * Configuration for a single wake word
 */
data class WakeWordConfig(
    val keyword: String,
    val threshold: Float,       // Per-keyword detection threshold
    val response: String,       // Response phrase when this keyword is triggered
    val cooldownMs: Long = 3000 // Cooldown time after trigger
)

/**
 * Wake word detector with per-keyword thresholds and cooldown mechanism.
 *
 * Wraps KWS results and applies:
 * - Per-keyword threshold filtering
 * - Cooldown timer to prevent repeated triggers
 * - Confidence filtering
 */
class WakeWordDetector(
    defaultThreshold: Float = 0.5f,
    private val defaultCooldownMs: Long = 3000L
) {
    private val keywordConfigs = mutableMapOf<String, WakeWordConfig>()
    private var lastTriggeredTime = 0L
    private var lastTriggeredKeyword = ""
    private var currentDefaultThreshold = defaultThreshold

    /**
     * Add or update a wake word configuration.
     */
    fun setKeyword(config: WakeWordConfig) {
        keywordConfigs[config.keyword] = config
        Timber.d("WakeWordDetector: registered keyword '${config.keyword}' with threshold=${config.threshold}, cooldown=${config.cooldownMs}ms")
    }

    /**
     * Remove a wake word.
     */
    fun removeKeyword(keyword: String) {
        keywordConfigs.remove(keyword)
        Timber.d("WakeWordDetector: removed keyword '$keyword'")
    }

    /**
     * Clear all configured keywords.
     */
    fun clearKeywords() {
        keywordConfigs.clear()
    }

    /**
     * Process a KWS result and apply per-keyword thresholds and cooldown.
     *
     * @param kwsResult The raw result from KWS
     * @return WakeWordTriggered if detection should proceed, null if should be skipped
     */
    fun process(kwsResult: com.voiceassistant.core.sherpa.KWSResult): WakeWordTriggered? {
        if (!kwsResult.detected) {
            return null
        }

        val keyword = kwsResult.keyword
        val confidence = kwsResult.confidence

        // Get config for this keyword, or use defaults
        val config = keywordConfigs[keyword]
        val threshold = config?.threshold ?: currentDefaultThreshold
        val cooldownMs = config?.cooldownMs ?: defaultCooldownMs
        val response = config?.response ?: "我在"

        // Check cooldown
        val now = System.currentTimeMillis()
        if (keyword == lastTriggeredKeyword && now - lastTriggeredTime < cooldownMs) {
            Timber.d("WakeWordDetector: keyword '$keyword' in cooldown (${now - lastTriggeredTime}ms since last trigger)")
            return null
        }

        // Check confidence against threshold
        if (confidence < threshold) {
            Timber.d("WakeWordDetector: keyword '$keyword' confidence $confidence < threshold $threshold")
            return null
        }

        // Triggered!
        lastTriggeredTime = now
        lastTriggeredKeyword = keyword
        Timber.d("WakeWordDetector: TRIGGERED keyword='$keyword', confidence=$confidence, response='$response'")

        return WakeWordTriggered(
            keyword = keyword,
            confidence = confidence,
            response = response
        )
    }

    /**
     * Load default wake words into the detector.
     */
    fun loadDefaults(wakeWords: List<WakeWord>) {
        clearKeywords()
        wakeWords.forEach { ww ->
            setKeyword(
                WakeWordConfig(
                    keyword = ww.keyword,
                    threshold = currentDefaultThreshold,
                    response = ww.response,
                    cooldownMs = defaultCooldownMs
                )
            )
        }
        Timber.d("WakeWordDetector: loaded ${wakeWords.size} default wake words")
    }

    /**
     * Update default threshold used by keywords that don't have a custom value.
     */
    fun setDefaultThreshold(threshold: Float) {
        currentDefaultThreshold = threshold.coerceIn(0f, 1f)
        Timber.d("WakeWordDetector: default threshold updated to $currentDefaultThreshold")
    }

    fun getDefaultThreshold(): Float = currentDefaultThreshold

    /**
     * Get number of registered keywords.
     */
    fun getKeywordCount(): Int = keywordConfigs.size
}
