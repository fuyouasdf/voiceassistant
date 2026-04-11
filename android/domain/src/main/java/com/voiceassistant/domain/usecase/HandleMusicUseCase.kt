package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.MusicRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for handling music-related voice commands via Jellyfin Session API.
 *
 * This use case handles Jellyfin session operations (remote playback).
 * Local playback is handled by IntentExecutor using MusicPlayer.
 *
 * Business logic for:
 * - pause/resume/stop/next/previous: Jellyfin session control
 */
class HandleMusicUseCase @Inject constructor(
    private val musicRepository: MusicRepository?
) {
    /**
     * Execute a music intent on a Jellyfin session.
     * @param intent The music intent
     * @param sessionId The Jellyfin session ID
     * @return Response message
     */
    suspend fun execute(intent: Intent, sessionId: String): String {
        val repo = musicRepository ?: return "音乐服务未配置，请在设置中配置 Jellyfin"

        return when (intent.action) {
            "pause" -> handlePause(repo, sessionId)
            "resume" -> handleResume(repo, sessionId)
            "next" -> handleNext(repo, sessionId)
            "previous" -> handlePrevious(repo, sessionId)
            "stop" -> handleStop(repo, sessionId)
            else -> "音乐操作"
        }
    }

    /**
     * Play a song on Jellyfin session.
     * @param song The song to play
     * @param sessionId The Jellyfin session ID
     * @return Response message
     */
    suspend fun playSong(song: Song, sessionId: String): String {
        val repo = musicRepository ?: return "音乐服务未配置"

        Timber.d("playSong: using Jellyfin Session API with sessionId=$sessionId, songId=${song.id}")
        val result = repo.playItem(sessionId, song.id)
        return if (result.isSuccess) {
            "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
        } else {
            val error = result.exceptionOrNull()?.message ?: "播放失败"
            Timber.e("playSong failed by Session API: $error")
            "播放失败：$error"
        }
    }

    private suspend fun handlePause(repo: MusicRepository, sessionId: String): String {
        val result = repo.pause(sessionId)
        return if (result.isSuccess) "已暂停播放" else "暂停失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
    }

    private suspend fun handleResume(repo: MusicRepository, sessionId: String): String {
        val result = repo.unpause(sessionId)
        return if (result.isSuccess) "继续播放" else "继续播放失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
    }

    private suspend fun handleNext(repo: MusicRepository, sessionId: String): String {
        val result = repo.nextTrack(sessionId)
        return if (result.isSuccess) "正在播放下一首" else "切换下一首失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
    }

    private suspend fun handlePrevious(repo: MusicRepository, sessionId: String): String {
        val result = repo.previousTrack(sessionId)
        return if (result.isSuccess) "正在播放上一首" else "切换上一首失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
    }

    private suspend fun handleStop(repo: MusicRepository, sessionId: String): String {
        val result = repo.stop(sessionId)
        return if (result.isSuccess) "已停止播放" else "停止失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
    }
}
