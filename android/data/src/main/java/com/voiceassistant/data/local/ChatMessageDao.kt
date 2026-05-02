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

package com.voiceassistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query(
        """
        SELECT * FROM chat_messages
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestMessages(limit: Int): List<ChatMessageEntity>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE createdAt < :beforeCreatedAt
           OR (createdAt = :beforeCreatedAt AND id < :beforeId)
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getMessagesBefore(
        beforeCreatedAt: Long,
        beforeId: Long,
        limit: Int
    ): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}
