package com.voiceassistant.app

import android.app.Application
import android.content.Intent
import android.os.Build
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
        
        // Start voice service
        startVoiceService()
    }
    
    private fun startVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Timber.d("VoiceAssistantService started")
    }
}
