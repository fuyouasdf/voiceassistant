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
    var navidromeUrl: String = ""
    var navidromeUsername: String = ""
    var navidromePassword: String = ""
    var llmBaseUrl: String = "https://api.deepseek.com"
    var llmApiKey: String = ""
    var llmModel: String = "deepseek-chat"

    fun reload() {
        settingsRepository?.let { repo ->
            runBlocking {
                navidromeUrl = repo.getNavidromeUrl()
                navidromeUsername = repo.getNavidromeUsername()
                navidromePassword = repo.getNavidromePassword()
                llmBaseUrl = repo.getLLMBaseUrl().ifEmpty { "https://api.deepseek.com" }
                llmApiKey = repo.getLLMApiKey()
                llmModel = repo.getLLMModel().ifEmpty { "deepseek-chat" }
            }
        }
    }
}