package com.voiceassistant.app

import android.app.Application
import android.content.Intent
import android.os.Build
import com.voiceassistant.app.di.ConfigHolder
import com.voiceassistant.app.service.VoiceAssistantService
import com.voiceassistant.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VoiceAssistantApp : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var configHolder: ConfigHolder

    override fun onCreate() {
        super.onCreate()

        // Initialize logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Load config from database to ConfigHolder for runtime use
        configHolder.settingsRepository = settingsRepository
        configHolder.reload()

        Timber.d("VoiceAssistantApp started, config loaded: navidrome=${configHolder.navidromeUrl}, llm=${configHolder.llmBaseUrl}")
    }
}