package com.voiceassistant.domain.model

/**
 * Intent types for voice commands
 */
enum class IntentType {
    MUSIC,      // Play/pause music
    VOLUME,     // Volume control
    DEVICE,     // Device control
    QUERY,      // Information query (requires LLM)
    CHAT,       // General chat (requires LLM)
    UNKNOWN     // Unknown intent
}

/**
 * Parsed voice command intent.
 * This is a domain model - pure data, no side effects.
 */
data class Intent(
    val type: IntentType,
    val action: String? = null,
    val query: String? = null,
    val artist: String? = null,  // 歌手名
    val value: Int? = null,
    val song: Song? = null
)
