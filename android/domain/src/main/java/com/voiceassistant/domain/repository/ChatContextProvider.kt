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

/**
 * Interface for providing chat context that enables contextual LLM responses.
 *
 * Implementations fetch historical messages and build formatted context strings
 * to prepend to chat messages for context-aware responses.
 */
interface ChatContextProvider {
    /**
     * Build a context string from historical messages.
     * The context string is prepended to chat messages to provide
     * conversation history for contextual responses.
     *
     * @return A formatted context string, or empty string if no context available
     */
    suspend fun buildContextString(): String

    /**
     * Get the number of context items currently stored.
     *
     * @return The count of context items available
     */
    fun getContextCount(): Int
}