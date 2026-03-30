package com.voiceassistant.core.sherpa

/**
 * Represents the state of speech detection
 */
enum class VadState {
    /** Initial state, waiting for speech */
    IDLE,
    /** Speech has started */
    SPEECH_STARTED,
    /** Speech has ended (silence after speech) */
    SPEECH_ENDED
}

/**
 * Configuration for stateful VAD
 */
data class VadConfig(
    val minSpeechDurationMs: Long = 250,     // Minimum speech duration to confirm speech
    val maxSpeechDurationMs: Long = 30000,   // Maximum speech duration before forcing end
    val minSilenceDurationMs: Long = 500,    // Minimum silence to confirm speech end
    val silenceTimeoutMs: Long = 5000        // Total silence timeout
)

/**
 * Result of a stateful VAD process call
 */
data class StatefulVadResult(
    val state: VadState,
    val speechStarted: Boolean = false,  // True if this is the first frame of speech
    val speechEnded: Boolean = false,    // True if speech has ended
    val isSpeech: Boolean = false        // Current speech detection result
)

/**
 * Callback interface for stateful VAD events
 */
interface VadCallback {
    fun onSpeechStarted()
    fun onSpeechEnded()
    fun onTimeout()
}

/**
 * Interface for stateful Voice Activity Detection.
 * Tracks speech segments over time, not just single-frame detection.
 */
interface StatefulVad {

    /**
     * Initialize the stateful VAD
     * @param modelPath Path to VAD model directory
     * @param config VAD configuration
     * @return true if initialization successful
     */
    fun initialize(modelPath: String, config: VadConfig): Boolean

    /**
     * Process audio chunk and update state.
     * @param audio Audio samples (16kHz, float)
     * @return StatefulVadResult with current state and transition flags
     */
    fun process(audio: FloatArray): StatefulVadResult

    /**
     * Get current VAD state
     */
    fun getState(): VadState

    /**
     * Reset VAD state to IDLE
     */
    fun reset()

    /**
     * Release VAD resources
     */
    fun release()
}
