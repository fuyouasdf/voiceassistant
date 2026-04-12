package com.voiceassistant.data.repository

import com.voiceassistant.data.local.ChatMessageDao
import com.voiceassistant.data.local.ChatMessageEntity
import com.voiceassistant.domain.model.ChatMessage
import com.voiceassistant.domain.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MessageRepository using Room database
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) : MessageRepository {

    override suspend fun getLatestMessages(limit: Int): List<ChatMessage> {
        return chatMessageDao.getLatestMessages(limit).map { it.toDomain() }
    }

    override suspend fun saveMessage(text: String, isUser: Boolean): Long {
        val entity = ChatMessageEntity(
            text = text,
            isUser = isUser,
            createdAt = System.currentTimeMillis()
        )
        return chatMessageDao.insertMessage(entity)
    }

    override suspend fun deleteAllMessages() {
        chatMessageDao.deleteAllMessages()
    }

    private fun ChatMessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            text = text,
            isUser = isUser,
            createdAt = createdAt
        )
    }
}