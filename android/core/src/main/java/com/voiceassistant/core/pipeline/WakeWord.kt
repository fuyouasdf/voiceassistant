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

package com.voiceassistant.core.pipeline

/**
 * Represents a wake word with its response phrase
 * @param keyword The wake word/phrase (e.g., "小爱", "hey assistant")
 * @param response The response phrase when wake word is detected (default: "我在")
 */
data class WakeWord(
    val keyword: String,
    val response: String = "我在"
) {
    companion object {
        /**
         * Parse a line from keywords.txt format: "keyword:response" or just "keyword"
         */
        fun fromLine(line: String): WakeWord? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            return if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                WakeWord(keyword = parts[0].trim(), response = parts[1].trim())
            } else {
                WakeWord(keyword = trimmed, response = "我在")
            }
        }
    }

    /**
     * Convert to line format for keywords.txt
     */
    fun toLine(): String = "$keyword:$response"
}