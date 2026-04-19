package com.voiceassistant.domain.repository

/**
 * Interface for providing chat context that enables contextual LLM responses.
 *
 * Implementations fetch historical messages and build formatted context strings
 * to prepend to chat messages for context-aware responses.
 */
interface ChatContextProvider {
    /**
     * Build a context string from historical messages.
     * The context string is prepended to chat messages to provide
     * conversation history for contextual responses.
     *
     * @return A formatted context string, or empty string if no context available
     */
    suspend fun buildContextString(): String

    /**
     * Get the number of context items currently stored.
     *
     * @return The count of context items available
     */
    fun getContextCount(): Int
}