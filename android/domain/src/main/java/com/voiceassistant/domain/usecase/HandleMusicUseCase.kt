package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.PlayerRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for handling music-related voice commands.
 *
 * Business logic for:
 * - play: search song and prepare stream URL
 * - pause/resume/stop/next/previous: playback control
 */
class HandleMusicUseCase @Inject constructor(
    private val musicRepository: MusicRepository?,
    private val playerRepository: PlayerRepository?
) {

    suspend fun execute(intent: Intent): String {
        val repo = musicRepository ?: return "音乐服务未配置，请在设置中配置 Jellyfin"

        return when (intent.action) {
            "play" -> handlePlay(intent, repo)
            "pause" -> handlePause()
            "resume" -> handleResume()
            "next" -> "正在播放下一首"
            "previous" -> "正在播放上一首"
            "stop" -> handleStop()
            else -> "音乐操作"
        }
    }

    private suspend fun handlePlay(intent: Intent, repo: MusicRepository): String {
        val query = intent.query ?: ""
        if (query.isEmpty()) {
            return "请告诉我你想听什么歌曲"
        }

        val player = playerRepository ?: return "播放器未配置"

        return try {
            val result = repo.searchSongs(query)
            result.fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) {
                        "没找到关于「$query」的歌曲"
                    } else {
                        val song = songs.first()
                        val streamUrl = repo.getStreamUrl(song.id)
                        val playResult = player.play(streamUrl, song.title, song.artist ?: "未知艺术家")
                        if (playResult.isSuccess) {
                            Timber.d("Playing: ${song.title} - streamUrl: $streamUrl")
                            "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
                        } else {
                            val error = playResult.exceptionOrNull()?.message ?: "播放失败"
                            Timber.e("Play failed: $error")
                            "播放失败：$error"
                        }
                    }
                },
                onFailure = { "搜索歌曲失败，请稍后重试" }
            )
        } catch (e: Exception) {
            Timber.e(e, "Music search failed")
            "搜索歌曲失败，请稍后重试"
        }
    }

    private suspend fun handlePause(): String {
        val player = playerRepository ?: return "播放器未配置"
        return try {
            val result = player.pause()
            if (result.isSuccess) "已暂停播放" else "暂停失败"
        } catch (e: Exception) {
            Timber.e(e, "Pause failed")
            "暂停失败"
        }
    }

    private suspend fun handleResume(): String {
        val player = playerRepository ?: return "播放器未配置"
        return try {
            val result = player.resume()
            if (result.isSuccess) "继续播放" else "继续播放失败"
        } catch (e: Exception) {
            Timber.e(e, "Resume failed")
            "继续播放失败"
        }
    }

    private suspend fun handleStop(): String {
        val player = playerRepository ?: return "播放器未配置"
        return try {
            val result = player.stop()
            if (result.isSuccess) "已停止播放" else "停止失败"
        } catch (e: Exception) {
            Timber.e(e, "Stop failed")
            "停止失败"
        }
    }
}
