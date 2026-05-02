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