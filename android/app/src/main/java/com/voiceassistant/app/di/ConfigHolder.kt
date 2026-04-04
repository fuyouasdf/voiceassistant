package com.voiceassistant.app.di

import com.voiceassistant.data.repository.SettingsRepository
import com.voiceassistant.core.pipeline.WakeWord
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holder for configuration values that can be accessed synchronously from OkHttp interceptors
 */
@Singleton
class ConfigHolder @Inject constructor() {
    var settingsRepository: SettingsRepository? = null

    // Cached values updated on app start
    var jellyfinUrl: String = ""
    var jellyfinApiKey: String = ""
    var llmBaseUrl: String = ""
    var llmApiKey: String = ""
    var llmModel: String = "deepseek-chat"
    var ttsEnabled: Boolean = true
    var wakeSensitivity: Float = 0.5f  // 唤醒灵敏度，默认 0.5
    var wakeWords: List<WakeWord> = emptyList()  // 唤醒词列表

    fun reload() {
        settingsRepository?.let { repo ->
            runBlocking {
                jellyfinUrl = repo.getJellyfinUrl()
                jellyfinApiKey = repo.getJellyfinApiKey()
                llmBaseUrl = repo.getLLMBaseUrl().ifEmpty { "https://api.deepseek.com" }
                llmApiKey = repo.getLLMApiKey()
                llmModel = repo.getLLMModel().ifEmpty { "deepseek-chat" }
                ttsEnabled = repo.getTtsEnabled()
                wakeSensitivity = repo.getWakeSensitivity()
                // Parse wake words from stored format: "keyword:response|keyword:response"
                wakeWords = repo.getWakeWords().mapNotNull { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.isNotEmpty()) {
                        WakeWord(
                            keyword = parts[0].trim(),
                            response = parts.getOrNull(1)?.trim() ?: "我在"
                        )
                    } else null
                }
            }
        }
    }
}
