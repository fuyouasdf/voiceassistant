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
