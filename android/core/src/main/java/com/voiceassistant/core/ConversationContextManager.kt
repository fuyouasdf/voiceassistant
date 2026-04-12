package com.voiceassistant.core

import com.voiceassistant.core.intent.ConversationContext
import com.voiceassistant.domain.repository.MessageRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for conversation context
 */
data class ConversationConfig(
    val maxContextCount: Int = 5
)

/**
 * Holder for runtime conversation context config.
 * Allows updating maxContextCount at runtime without recreating the manager.
 */
object ConversationContextHolder {
    @Volatile
    var maxContextCount: Int = 5
}

/**
 * Manages conversation context for LLM interactions.
 *
 * This class retrieves historical messages from the message repository
 * and builds a formatted context string for LLM requests.
 *
 * The context string format:
 * - User messages are prefixed with "用户: "
 * - Assistant messages are prefixed with "助手: "
 * - Messages are ordered chronologically (oldest first)
 */
@Singleton
class ConversationContextManager @Inject constructor(
    private val messageRepository: MessageRepository
) : ConversationContext {

    private var _contextCount: Int = 0

    /**
     * Build a formatted context string from historical messages.
     * Implements ConversationContext.buildContextString()
     */
    override suspend fun buildContextString(): String {
        return try {
            val maxCount = ConversationContextHolder.maxContextCount
            val messages = messageRepository.getLatestMessages(maxCount * 2)
            _contextCount = messages.size
            if (messages.isEmpty()) {
                Timber.d("ConversationContext: No messages found, returning empty context")
                return ""
            }

            // Reverse to get chronological order (oldest first)
            val chronologicalMessages = messages.reversed()

            val contextString = buildString {
                chronologicalMessages.forEach { message ->
                    val prefix = if (message.isUser) "用户: " else "助手: "
                    appendLine("$prefix${message.text}")
                }
            }

            Timber.d("ConversationContext: Built context with ${messages.size} messages")
            contextString.trimEnd()
        } catch (e: Exception) {
            Timber.e(e, "ConversationContext: Failed to build context string")
            ""
        }
    }

    /**
     * Get the number of context items currently stored.
     * Implements ConversationContext.getContextCount()
     */
    override fun getContextCount(): Int = _contextCount

    /**
     * Save a message to the conversation history.
     *
     * @param text The message text
     * @param isUser Whether this is a user message
     * @return The ID of the saved message, or -1 on failure
     */
    suspend fun saveMessage(text: String, isUser: Boolean): Long {
        return try {
            messageRepository.saveMessage(text, isUser)
        } catch (e: Exception) {
            Timber.e(e, "ConversationContext: Failed to save message")
            -1
        }
    }

    /**
     * Clear all conversation history.
     */
    suspend fun clearHistory() {
        try {
            messageRepository.deleteAllMessages()
            Timber.d("ConversationContext: History cleared")
        } catch (e: Exception) {
            Timber.e(e, "ConversationContext: Failed to clear history")
        }
    }

    /**
     * Update the max context count at runtime.
     */
    fun updateMaxContextCount(count: Int) {
        ConversationContextHolder.maxContextCount = count
        Timber.d("ConversationContext: maxContextCount updated to $count")
    }
}