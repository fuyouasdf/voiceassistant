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