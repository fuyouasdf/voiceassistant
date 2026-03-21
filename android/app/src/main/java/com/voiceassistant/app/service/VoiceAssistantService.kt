package com.voiceassistant.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.voiceassistant.app.R
import com.voiceassistant.app.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class VoiceAssistantService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "voice_assistant_channel"
        const val ACTION_STOP = "com.voiceassistant.app.ACTION_STOP_SERVICE"

        @Volatile
        var isServiceRunning = false
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("=== VoiceAssistantService onCreate START ===")
        isServiceRunning = true

        try {
            // Step 1: Wake lock
            acquireWakeLock()
            Timber.d("Wake lock acquired")

            // Step 2: Show notification
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Timber.d("Foreground started with notification")

            // Step 3: Let MainActivity handle the voice pipeline
            // The activity will observe service state and manage pipeline
            Timber.d("Service ready, MainActivity will manage voice pipeline")

        } catch (e: Exception) {
            Timber.e(e, "ERROR in onCreate!")
        }

        Timber.d("=== VoiceAssistantService onCreate END ===")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("VoiceAssistantService onStartCommand")

        if (intent?.action == ACTION_STOP) {
            Timber.d("Stop action received")
            isServiceRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Timber.d("=== VoiceAssistantService onDestroy ===")
        isServiceRunning = false
        try {
            releaseWakeLock()
        } catch (e: Exception) {
            Timber.e(e, "Error in onDestroy")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VoiceAssistant::WakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error releasing wake lock")
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音助手")
            .setContentText("正在运行...")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_mic, "停止", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "语音助手服务",
                    NotificationManager.IMPORTANCE_MAX
                ).apply {
                    description = "保持语音助手后台运行"
                    setShowBadge(true)
                    enableLights(true)
                }

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Timber.e(e, "Error creating notification channel")
            }
        }
    }
}

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("ServiceRestartReceiver: ${intent.action}")
    }
}