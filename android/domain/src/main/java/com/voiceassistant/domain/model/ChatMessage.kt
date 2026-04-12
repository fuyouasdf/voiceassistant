package com.voiceassistant.domain.model

/**
 * Domain model for chat messages
 */
data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)