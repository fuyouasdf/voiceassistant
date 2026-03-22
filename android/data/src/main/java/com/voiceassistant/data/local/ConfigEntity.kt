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
    const val NAVIDROME_URL = "navidrome_url"
    const val NAVIDROME_USERNAME = "navidrome_username"
    const val NAVIDROME_PASSWORD = "navidrome_password"
    
    const val DLNA_DEVICE_IP = "dlna_device_ip"
    const val DLNA_DEVICE_NAME = "dlna_device_name"
    const val DLNA_DEVICE_UUID = "dlna_device_uuid"
    
    const val LLM_BASE_URL = "llm_base_url"
    const val LLM_API_KEY = "llm_api_key"
    const val LLM_MODEL = "llm_model"
    const val LLM_SYSTEM_PROMPT = "llm_system_prompt"
    
    const val OPENCLAW_URL = "openclaw_url"
    const val OPENCLAW_TOKEN = "openclaw_token"
    
    const val WAKE_SENSITIVITY = "wake_sensitivity"
    const val TTS_SPEED = "tts_speed"
}
