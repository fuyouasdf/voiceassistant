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

package com.voiceassistant.core

import com.voiceassistant.domain.repository.ChatContextProvider
import com.voiceassistant.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for conversation context
 */
data class ConversationConfig(
    val maxContextCount: Int = 5,
    val maxTurnsBeforeReset: Int = 10,
    val conversationTimeoutSeconds: Int = 300,
    val summarizationThreshold: Int = 20
)

/**
 * Holder for runtime conversation context config.
 * Allows updating conversation settings at runtime without recreating the manager.
 */
object ConversationContextHolder {
    @Volatile
    var maxContextCount: Int = 5

    @Volatile
    var maxTurnsBeforeReset: Int = 10

    @Volatile
    var conversationTimeoutSeconds: Int = 300

    @Volatile
    var summarizationThreshold: Int = 20

    @Volatile
    var isConversationActive: Boolean = false

    @Volatile
    var lastInteractionTimeMillis: Long = 0L

    /**
     * Update all conversation settings at once
     */
    fun updateSettings(
        maxContextCount: Int = 5,
        maxTurnsBeforeReset: Int = 10,
        conversationTimeoutSeconds: Int = 300,
        summarizationThreshold: Int = 20
    ) {
        this.maxContextCount = maxContextCount
        this.maxTurnsBeforeReset = maxTurnsBeforeReset
        this.conversationTimeoutSeconds = conversationTimeoutSeconds
        this.summarizationThreshold = summarizationThreshold
        Timber.d("ConversationContextHolder: settings updated - maxContextCount=$maxContextCount, maxTurns=$maxTurnsBeforeReset, timeout=${conversationTimeoutSeconds}s, summarizationThreshold=$summarizationThreshold")
    }

    /**
     * Mark that a conversation interaction just happened
     */
    fun recordInteraction() {
        isConversationActive = true
        lastInteractionTimeMillis = System.currentTimeMillis()
    }

    /**
     * Check if conversation has timed out
     */
    fun hasTimedOut(): Boolean {
        if (!isConversationActive) return false
        val elapsed = (System.currentTimeMillis() - lastInteractionTimeMillis) / 1000
        return elapsed > conversationTimeoutSeconds
    }

    /**
     * Reset conversation state
     */
    fun resetConversationState() {
        isConversationActive = false
        lastInteractionTimeMillis = 0L
    }
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
) : ChatContextProvider {

    private var _contextCount: Int = 0
    private var _turnCount: Int = 0
    private var _shouldSummarize: Boolean = false

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timeoutCheckJob: Job? = null

    /**
     * Build a formatted context string from historical messages.
     * Implements ConversationContext.buildContextString()
     */
    override suspend fun buildContextString(): String {
        return try {
            // Check for timeout before building context
            checkAndHandleTimeout()

            val maxCount = ConversationContextHolder.maxContextCount
            val messages = messageRepository.getLatestMessages(maxCount * 2)
            _contextCount = messages.size

            // Calculate turn count (each turn = user + assistant pair)
            _turnCount = messages.count { !it.isUser } // assistant messages = turns completed

            // Check if summarization is needed
            _shouldSummarize = messages.size >= ConversationContextHolder.summarizationThreshold

            if (messages.isEmpty()) {
                Timber.d("ConversationContext: No messages found, returning empty context")
                ConversationContextHolder.resetConversationState()
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

            // Record this interaction
            ConversationContextHolder.recordInteraction()

            // Start timeout checker
            startTimeoutChecker()

            // Check if max turns exceeded and need to reset
            if (_turnCount >= ConversationContextHolder.maxTurnsBeforeReset) {
                Timber.d("ConversationContext: Max turns ($_turnCount) exceeded, context should be reset")
            }

            Timber.d("ConversationContext: Built context with ${messages.size} messages, $_turnCount turns")
            contextString.trimEnd()
        } catch (e: Exception) {
            Timber.e(e, "ConversationContext: Failed to build context string")
            ""
        }
    }

    /**
     * Check if conversation has timed out and handle it
     */
    private suspend fun checkAndHandleTimeout() {
        if (ConversationContextHolder.hasTimedOut()) {
            Timber.d("ConversationContext: Conversation timed out, clearing history")
            clearHistory()
            ConversationContextHolder.resetConversationState()
        }
    }

    /**
     * Start background timeout checker
     */
    private fun startTimeoutChecker() {
        timeoutCheckJob?.cancel()
        timeoutCheckJob = managerScope.launch {
            // Check timeout periodically
            while (ConversationContextHolder.isConversationActive) {
                delay(30000) // Check every 30 seconds
                if (ConversationContextHolder.hasTimedOut()) {
                    Timber.d("ConversationContext: Timeout detected in background")
                    clearHistory()
                    ConversationContextHolder.resetConversationState()
                    break
                }
            }
        }
    }

    /**
     * Get the number of context items currently stored.
     * Implements ConversationContext.getContextCount()
     */
    override fun getContextCount(): Int = _contextCount

    /**
     * Get the current turn count (completed user-assistant exchanges)
     */
    fun getTurnCount(): Int = _turnCount

    /**
     * Check if context should be summarized
     */
    fun shouldSummarize(): Boolean = _shouldSummarize

    /**
     * Check if conversation has exceeded max turns
     */
    fun shouldResetContext(): Boolean = _turnCount >= ConversationContextHolder.maxTurnsBeforeReset

    /**
     * Save a message to the conversation history.
     *
     * @param text The message text
     * @param isUser Whether this is a user message
     * @return The ID of the saved message, or -1 on failure
     */
    suspend fun saveMessage(text: String, isUser: Boolean): Long {
        return try {
            val id = messageRepository.saveMessage(text, isUser)
            // Record interaction after saving
            ConversationContextHolder.recordInteraction()
            id
        } catch (e: Exception) {
            Timber.e(e, "ConversationContext: Failed to save message")
            -1
        }
    }

    /**
     * Clear all conversation history and reset turn counter.
     */
    suspend fun clearHistory() {
        try {
            messageRepository.deleteAllMessages()
            _turnCount = 0
            _shouldSummarize = false
            Timber.d("ConversationContext: History cleared, turn count reset")
        } catch (e: Exception) {
            Timber.e(e, "ConversationContext: Failed to clear history")
        }
    }

    /**
     * Reset conversation state without clearing history.
     * Use this when max turns is exceeded to start fresh.
     */
    fun resetConversationState() {
        _turnCount = 0
        _shouldSummarize = false
        ConversationContextHolder.resetConversationState()
        timeoutCheckJob?.cancel()
        Timber.d("ConversationContext: Conversation state reset")
    }

    /**
     * Update the max context count at runtime.
     */
    fun updateMaxContextCount(count: Int) {
        ConversationContextHolder.maxContextCount = count
        Timber.d("ConversationContext: maxContextCount updated to $count")
    }

    /**
     * Update all conversation settings at runtime.
     */
    fun updateSettings(
        maxContextCount: Int,
        maxTurnsBeforeReset: Int,
        conversationTimeoutSeconds: Int,
        summarizationThreshold: Int
    ) {
        ConversationContextHolder.updateSettings(
            maxContextCount = maxContextCount,
            maxTurnsBeforeReset = maxTurnsBeforeReset,
            conversationTimeoutSeconds = conversationTimeoutSeconds,
            summarizationThreshold = summarizationThreshold
        )
        Timber.d("ConversationContext: All settings updated")
    }
}