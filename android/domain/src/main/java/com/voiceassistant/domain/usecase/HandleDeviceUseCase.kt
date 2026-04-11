package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.repository.MusicRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for handling device control commands via Jellyfin Session API.
 *
 * This use case handles Jellyfin session control (remote device).
 * Local device control is handled by IntentExecutor using MusicPlayer.
 */
class HandleDeviceUseCase @Inject constructor(
    private val musicRepository: MusicRepository?
) {
    /**
     * Execute a device intent on a Jellyfin session.
     * @param intent The device intent
     * @param sessionId The Jellyfin session ID
     * @return Response message
     */
    suspend fun execute(intent: Intent, sessionId: String): String {
        val repo = musicRepository ?: return "设备操作失败"

        return when (intent.action) {
            "on" -> handleOn(repo, sessionId)
            "off" -> handleOff(repo, sessionId)
            "toggle" -> handleToggle(repo, sessionId)
            else -> "设备操作"
        }
    }

    private suspend fun handleOn(repo: MusicRepository, sessionId: String): String {
        val result = repo.unpause(sessionId)
        return if (result.isSuccess) {
            "已打开设备并继续播放"
        } else {
            "打开设备失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    private suspend fun handleOff(repo: MusicRepository, sessionId: String): String {
        val result = repo.stop(sessionId)
        return if (result.isSuccess) {
            "已关闭设备"
        } else {
            "关闭设备失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    private suspend fun handleToggle(repo: MusicRepository, sessionId: String): String {
        // Try pause first, if fails try resume
        val pauseResult = repo.pause(sessionId)
        if (pauseResult.isSuccess) {
            return "已暂停播放"
        }

        val resumeResult = repo.unpause(sessionId)
        return if (resumeResult.isSuccess) {
            "已继续播放"
        } else {
            "切换状态失败：${resumeResult.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }
}
