package com.voiceassistant.core.audio


/**
 * Audio preprocessor for KWS input.
 *
 * Features:
 * - Gain control (AGC-lite): Auto-adjusts gain based on audio energy
 * - Noise gate: Skips audio chunks below energy threshold
 *
 * The preprocessor operates on raw FloatArray audio and can return:
 * - Processed FloatArray: Audio ready for KWS
 * - null: Audio was below noise threshold and should be skipped
 */
class AudioPreprocessor(
    private val targetLevel: Float = 0.5f,
    private val noiseThreshold: Float = 0f,  // 禁用噪声门限，KWS模型自带噪声过滤
    private val minGain: Float = 0.5f,
    private val maxGain: Float = 3.0f,
    private val gainSmoothing: Float = 0.1f
) {
    private var currentGain = 1.0f

    /**
     * Process an audio chunk.
     *
     * @param audio Raw audio samples (typically 512 samples = 32ms at 16kHz)
     * @return Processed audio if above noise threshold, null if should be skipped
     */
    fun process(audio: FloatArray): FloatArray? {
        if (audio.isEmpty()) return null

        // Calculate current energy
        val energy = audio.map { it * it }.average().toFloat()

        // Noise gate: skip if below threshold (disabled when threshold = 0)
        if (noiseThreshold > 0f && energy < noiseThreshold) {
            return null
        }

        // Auto gain control (AGC-lite)
        if (energy > 0) {
            val desiredGain = targetLevel / energy
            val clampedGain = desiredGain.coerceIn(minGain, maxGain)
            // Exponential moving average for smooth transitions
            currentGain = currentGain * (1 - gainSmoothing) + clampedGain * gainSmoothing
        }

        // Apply gain and clip to [-1, 1]
        val processed = FloatArray(audio.size)
        for (i in audio.indices) {
            processed[i] = (audio[i] * currentGain).coerceIn(-1f, 1f)
        }

        return processed
    }

    /**
     * Reset the preprocessor state.
     * Call this when switching audio sources or after a wake word detection.
     */
    fun reset() {
        currentGain = 1.0f
    }

    /**
     * Get current gain value (for debugging).
     */
    fun getCurrentGain(): Float = currentGain
}
