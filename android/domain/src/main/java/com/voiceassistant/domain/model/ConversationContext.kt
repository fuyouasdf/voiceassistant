package com.voiceassistant.domain.model

/**
 * Represents a single message in a conversation
 */
data class ConversationMessage(
    val text: String,
    val isUser: Boolean
)

/**
 * Provides conversation context for LLM interactions.
 * Contains conversation history to enable contextual responses.
 */
data class ConversationContext(
    val history: List<ConversationMessage> = emptyList()
) {
    /**
     * Formats the conversation history as "User: xxx\nAssistant: xxx\n" style string
     */
    fun toFormattedString(): String {
        return history.joinToString("\n") { msg ->
            val role = if (msg.isUser) "User" else "Assistant"
            "$role: ${msg.text}"
        }
    }
}