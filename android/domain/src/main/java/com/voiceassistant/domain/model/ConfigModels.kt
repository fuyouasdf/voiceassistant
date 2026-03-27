package com.voiceassistant.domain.model

data class JellyfinConfig(
    val baseUrl: String,
    val apiKey: String
)

data class DLNAConfig(
    val deviceIp: String,
    val deviceName: String
)

data class LLMConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

data class VoiceConfig(
    val wakeSensitivity: Float,
    val ttsSpeed: Float
)