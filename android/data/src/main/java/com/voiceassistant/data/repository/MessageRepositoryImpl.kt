/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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