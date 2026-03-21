package com.voiceassistant.core.pipeline

/**
 * Voice pipeline states
 */
enum class PipelineState {
    /** Idle, waiting for wake word */
    IDLE,
    
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
    val timestamp: Long = System.currentTimeMillis()
) {
    fun durationMs(): Long = System.currentTimeMillis() - timestamp
}
