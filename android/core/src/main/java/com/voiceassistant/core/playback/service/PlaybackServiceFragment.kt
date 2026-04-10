/*
 * Copyright (c) 2024 Auxio Project
 * PlaybackServiceFragment.kt is part of Auxio.
 * Adapted for Voice Assistant project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.voiceassistant.core.playback.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.voiceassistant.core.R
import com.voiceassistant.core.playback.state.PlaybackStateManager
import com.voiceassistant.core.playback.state.RepeatMode
import timber.log.Timber

/**
 * Manages playback service lifecycle, notification, and system controls.
 */
class PlaybackServiceFragment(
    private val context: Context,
    private val playbackManager: PlaybackStateManager,
    private val exoPlaybackStateHolder: ExoPlaybackStateHolder,
) : PlaybackStateManager.Listener {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "music_playback_channel"
        const val CHANNEL_NAME = "音乐播放"

        const val ACTION_PLAY = "com.voiceassistant.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.voiceassistant.app.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.voiceassistant.app.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.voiceassistant.app.ACTION_NEXT"
        const val ACTION_STOP = "com.voiceassistant.app.ACTION_STOP"

        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService<NotificationManager>()
            ?: throw IllegalStateException("NotificationManager not available")
    }

    private var notificationReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    fun attach() {
        playbackManager.addListener(this)
        exoPlaybackStateHolder.attach()
        createNotificationChannel()
        registerNotificationReceiver()
    }

    fun release() {
        playbackManager.removeListener(this)
        exoPlaybackStateHolder.release()
        unregisterNotificationReceiver()
        dismissNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewPlayback(queue: List<com.voiceassistant.core.music.MusicItem>, index: Int, isShuffled: Boolean) {
        val item = queue.getOrNull(index)
        if (item != null) {
            showNotification(item)
        }
    }

    override fun onIndexMoved(index: Int) {
        val queue = playbackManager.queue
        val item = queue.getOrNull(index)
        if (item != null) {
            showNotification(item)
        }
    }

    override fun onProgressionChanged(progression: com.voiceassistant.core.playback.state.Progression) {
        if (exoPlaybackStateHolder.sessionOngoing) {
            updateNotification()
        }
    }

    override fun onSessionEnded() {
        dismissNotification()
    }

    private fun showNotification(item: com.voiceassistant.core.music.MusicItem) {
        val isPlaying = playbackManager.progression.isPlaying

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_play)
                .setContentTitle(item.title)
                .setContentText(item.artist ?: "未知艺术家")
                .setSubText(item.album ?: "")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setContentIntent(createActivityPendingIntent())

        // Previous button
        builder.addAction(
            R.drawable.ic_notification_previous,
            "上一首",
            createPendingIntent(ACTION_PREVIOUS)
        )

        // Play/Pause button
        val playPauseIcon = if (isPlaying) {
            R.drawable.ic_notification_pause
        } else {
            R.drawable.ic_notification_play
        }
        val playPauseText = if (isPlaying) "暂停" else "播放"
        builder.addAction(
            playPauseIcon,
            playPauseText,
            createPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        )

        // Next button
        builder.addAction(
            R.drawable.ic_notification_next,
            "下一首",
            createPendingIntent(ACTION_NEXT)
        )

        builder.setDeleteIntent(createPendingIntent(ACTION_STOP))

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun updateNotification() {
        val item = playbackManager.currentSong ?: return
        showNotification(item)
    }

    private fun dismissNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PENDING_INTENT_FLAGS)
    }

    private fun createActivityPendingIntent(): PendingIntent {
        val intent = Intent().apply {
            setClassName(
                context.packageName,
                "com.voiceassistant.app.ui.music.NowPlayingActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(context, 1, intent, PENDING_INTENT_FLAGS)
    }

    private fun registerNotificationReceiver() {
        if (isReceiverRegistered) return

        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_PLAY -> playbackManager.playing(true)
                    ACTION_PAUSE -> playbackManager.playing(false)
                    ACTION_PREVIOUS -> playbackManager.prev()
                    ACTION_NEXT -> playbackManager.next()
                    ACTION_STOP -> {
                        playbackManager.endSession()
                        dismissNotification()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
            addAction(ACTION_STOP)
        }

        ContextCompat.registerReceiver(
            context,
            notificationReceiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
    }

    private fun unregisterNotificationReceiver() {
        if (!isReceiverRegistered) return
        try {
            notificationReceiver?.let {
                context.unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error unregistering notification receiver")
        }
        notificationReceiver = null
        isReceiverRegistered = false
    }

    fun handleNotificationAction(intent: Intent) {
        when (intent.action) {
            ACTION_PLAY -> playbackManager.playing(true)
            ACTION_PAUSE -> playbackManager.playing(false)
            ACTION_PREVIOUS -> playbackManager.prev()
            ACTION_NEXT -> playbackManager.next()
            ACTION_STOP -> {
                playbackManager.endSession()
                dismissNotification()
            }
        }
    }
}
