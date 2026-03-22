package com.voiceassistant.core.pipeline

/**
 * Voice pipeline states
 */
enum class PipelineState {
    /** Idle, waiting for wake word */
    IDLE,

    /** Wake word detected, showing feedback */
    WAKEWORD_DETECTED,

    /** Wake word detected, waiting for speech */
    LISTENING,

    /** Recording speech */
    RECORDING,

    /** Recognizing speech to text */
    RECOGNIZING,

    /** Processing intent and LLM */
    THINKING,

    /** Speaking response */
    SPEAKING
}

/**
 * State with timestamp for tracking duration
 */
data class StateInfo(
    val state: PipelineState,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""  // Optional message, e.g., recognized text
) {
    fun durationMs(): Long = System.currentTimeMillis() - timestamp
}
