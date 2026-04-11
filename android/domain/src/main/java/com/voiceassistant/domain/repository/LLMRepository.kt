package com.voiceassistant.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for LLM API operations
 */
interface LLMRepository {
    /**
     * Send a chat message and get response
     * @param message The user's message
     * @return The LLM's response text
     */
    suspend fun chat(message: String): Result<String>

    /**
     * Stream chat response, emits text deltas as they arrive
     * @param message The user's message
     * @return Flow emitting text deltas, completes when stream ends
     */
    fun chatStream(message: String): Flow<String>

    /**
     * Lightweight heartbeat to check connection - minimal tokens, no system prompt
     */
    suspend fun heartbeat(): Result<Boolean>

    /**
     * Use LLM as router to decide whether a message is normal chat or command execution.
     */
    suspend fun routeIntent(message: String): Result<LLMRouteDecision>

    /**
     * Parse command-style user input into a structured intent.
     */
    suspend fun parseCommandIntent(message: String): Result<LLMParsedIntent>
}

data class LLMRouteDecision(
    val mode: LLMRouteMode,
    val reason: String? = null
)

enum class LLMRouteMode {
    CHAT,
    COMMAND
}

data class LLMParsedIntent(
    val type: String,
    val action: String? = null,
    val query: String? = null,
    val value: Int? = null
)

/**
 * Exception thrown when the configured LLM model does not exist
 */
class ModelNotFoundException(val modelName: String) : Exception("Model not found: $modelName")
