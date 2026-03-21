package com.voiceassistant.app

import android.app.Application
import android.content.Intent
import android.os.Build
import com.voiceassistant.app.service.VoiceAssistantService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class VoiceAssistantApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("VoiceAssistantApp started")
    }
}