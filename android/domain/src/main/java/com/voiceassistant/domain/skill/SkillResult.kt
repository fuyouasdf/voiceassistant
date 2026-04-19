package com.voiceassistant.domain.skill

/**
 * Result returned by a skill after handling input.
 */
sealed class SkillResult {
    /**
     * Skill handled the input and produced a response.
     */
    data class Success(
        val response: String,
        val consumed: Boolean = true
    ) : SkillResult()

    /**
     * Skill did not handle the input (e.g., keywords didn't match).
     * The input should be passed to other skills.
     */
    data object NotHandled : SkillResult()

    /**
     * Skill encountered an error.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : SkillResult()
}