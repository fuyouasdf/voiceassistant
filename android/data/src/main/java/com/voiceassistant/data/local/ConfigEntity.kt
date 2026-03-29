package com.voiceassistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val isEncrypted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

// Config keys
object ConfigKeys {
    const val JELLYFIN_URL = "jellyfin_url"
    const val JELLYFIN_USERNAME = "jellyfin_username"
    const val JELLYFIN_PASSWORD = "jellyfin_password"

    const val LLM_BASE_URL = "llm_base_url"
    const val LLM_API_KEY = "llm_api_key"
    const val LLM_MODEL = "llm_model"
    const val LLM_SYSTEM_PROMPT = "llm_system_prompt"

    const val OPENCLAW_URL = "openclaw_url"
    const val OPENCLAW_TOKEN = "openclaw_token"

    const val WAKE_SENSITIVITY = "wake_sensitivity"
    const val TTS_SPEED = "tts_speed"
    const val TTS_ENABLED = "tts_enabled"
}
