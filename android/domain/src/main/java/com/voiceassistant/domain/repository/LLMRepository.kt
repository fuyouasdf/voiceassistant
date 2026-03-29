package com.voiceassistant.domain.repository

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
     * Test connection to LLM API via /v1/models endpoint
     * @return Result.success(true) if connected, Result.failure if not
     */
    suspend fun testConnection(): Result<Boolean>
}