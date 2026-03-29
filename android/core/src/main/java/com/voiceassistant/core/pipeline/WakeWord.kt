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