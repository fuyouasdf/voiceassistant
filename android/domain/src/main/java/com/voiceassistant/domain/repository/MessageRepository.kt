package com.voiceassistant.domain.repository

import com.voiceassistant.domain.model.ChatMessage

/**
 * Repository interface for chat message operations
 */
interface MessageRepository {
    /**
     * Get the latest messages up to the specified limit
     */
    suspend fun getLatestMessages(limit: Int): List<ChatMessage>

    /**
     * Save a chat message
     */
    suspend fun saveMessage(text: String, isUser: Boolean): Long

    /**
     * Delete all messages
     */
    suspend fun deleteAllMessages()
}