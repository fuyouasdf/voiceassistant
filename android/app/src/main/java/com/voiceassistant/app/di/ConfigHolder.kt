package com.voiceassistant.app.di

import com.voiceassistant.data.repository.SettingsRepository
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
    var jellyfinUsername: String = ""
    var jellyfinPassword: String = ""
    var llmBaseUrl: String = "http://192.168.31.244:20034"
    var llmApiKey: String = "sk-lm-gqABXq7E:Gi6jm1G0BCyrVhhHcTEl"
    var llmModel: String = "unsloth/Qwen3.5-35B-A3B-no"
    var ttsEnabled: Boolean = true

    // Callback to notify when credentials change (used by JellyfinClient)
    var onJellyfinCredentialsChanged: ((String, String) -> Unit)? = null

    fun reload() {
        settingsRepository?.let { repo ->
            runBlocking {
                val oldUsername = jellyfinUsername
                val oldPassword = jellyfinPassword

                jellyfinUrl = repo.getJellyfinUrl()
                jellyfinUsername = repo.getJellyfinUsername()
                jellyfinPassword = repo.getJellyfinPassword()
                llmBaseUrl = repo.getLLMBaseUrl().ifEmpty { "https://api.deepseek.com" }
                llmApiKey = repo.getLLMApiKey()
                llmModel = repo.getLLMModel().ifEmpty { "deepseek-chat" }
                ttsEnabled = repo.getTtsEnabled()

                // 如果 Jellyfin 凭据发生变化，通知 JellyfinClient 更新
                if (jellyfinUsername != oldUsername || jellyfinPassword != oldPassword) {
                    onJellyfinCredentialsChanged?.invoke(jellyfinUsername, jellyfinPassword)
                }
            }
        }
    }
}