package com.voiceassistant.app.ui.music

import com.voiceassistant.core.music.PlaybackReporter
import com.voiceassistant.data.remote.JellyfinClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jellyfin 播放进度上报实现
 */
@Singleton
class JellyfinPlaybackReporter @Inject constructor(
    private val jellyfinClient: JellyfinClient
) : PlaybackReporter {

    override suspend fun reportPlaybackStart(itemId: String, positionTicks: Long, isPaused: Boolean) {
        try {
            jellyfinClient.reportPlaybackStart(
                itemId = itemId,
                positionTicks = positionTicks,
                isPaused = isPaused
            )
        } catch (e: Exception) {
            Timber.e(e, "reportPlaybackStart failed")
        }
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionTicks: Long, playSessionId: String?, mediaSourceId: String?) {
        try {
            jellyfinClient.reportPlaybackProgress(
                itemId = itemId,
                positionTicks = positionTicks,
                playSessionId = playSessionId,
                mediaSourceId = mediaSourceId
            )
        } catch (e: Exception) {
            Timber.e(e, "reportPlaybackProgress failed")
        }
    }

    override suspend fun reportPlaybackStopped(itemId: String, positionTicks: Long) {
        try {
            jellyfinClient.reportPlaybackStopped(
                itemId = itemId,
                positionTicks = positionTicks
            )
        } catch (e: Exception) {
            Timber.e(e, "reportPlaybackStopped failed")
        }
    }

    override suspend fun markAsFavorite(itemId: String): Boolean {
        return try {
            jellyfinClient.markAsFavorite(itemId).getOrDefault(false)
        } catch (e: Exception) {
            Timber.e(e, "markAsFavorite failed")
            false
        }
    }

    override suspend fun removeFromFavorites(itemId: String): Boolean {
        return try {
            jellyfinClient.removeFromFavorites(itemId).getOrDefault(false)
        } catch (e: Exception) {
            Timber.e(e, "removeFromFavorites failed")
            false
        }
    }

    override suspend fun isFavorite(itemId: String): Boolean {
        return try {
            jellyfinClient.isFavorite(itemId)
        } catch (e: Exception) {
            Timber.e(e, "isFavorite failed")
            false
        }
    }
}