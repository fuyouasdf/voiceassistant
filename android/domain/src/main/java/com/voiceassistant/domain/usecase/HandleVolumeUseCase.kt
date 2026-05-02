/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.repository.MusicRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for handling volume control commands.
 */
class HandleVolumeUseCase @Inject constructor(
    private val musicRepository: MusicRepository?
) {
    private var lastKnownVolume: Int = 50

    suspend fun execute(intent: Intent, sessionId: String?, isLocalSession: Boolean): String {
        if (musicRepository == null) {
            return "音乐服务未配置"
        }

        if (sessionId == null) {
            return "请先在 Jellyfin 页面选择播放设备"
        }

        if (isLocalSession) {
            return "本机播放暂不支持语音调节 Jellyfin 设备音量"
        }

        return when (intent.action) {
            "set" -> handleSet(intent, sessionId, musicRepository)
            "up" -> handleUp(intent, sessionId, musicRepository)
            "down" -> handleDown(intent, sessionId, musicRepository)
            "mute" -> handleMute(sessionId, musicRepository)
            else -> "音量操作"
        }
    }

    private suspend fun handleSet(intent: Intent, sessionId: String, repo: MusicRepository): String {
        val volume = (intent.value ?: 50).coerceIn(0, 100)
        val result = repo.setVolume(sessionId, volume)
        return if (result.isSuccess) {
            lastKnownVolume = volume
            "音量已调到 $volume%"
        } else {
            "音量调节失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    private suspend fun handleUp(intent: Intent, sessionId: String, repo: MusicRepository): String {
        val increment = (intent.value ?: 10).coerceIn(0, 100)
        // 先获取设备当前音量
        val currentVolume = repo.getVolume(sessionId).getOrElse { lastKnownVolume }
        val newVolume = (currentVolume + increment).coerceAtMost(100)
        val result = repo.setVolume(sessionId, newVolume)
        return if (result.isSuccess) {
            lastKnownVolume = newVolume
            "音量已增加 $increment%，当前音量 $newVolume%"
        } else {
            "音量调节失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    private suspend fun handleDown(intent: Intent, sessionId: String, repo: MusicRepository): String {
        val decrement = (intent.value ?: 10).coerceIn(0, 100)
        // 先获取设备当前音量
        val currentVolume = repo.getVolume(sessionId).getOrElse { lastKnownVolume }
        val newVolume = (currentVolume - decrement).coerceAtLeast(0)
        val result = repo.setVolume(sessionId, newVolume)
        return if (result.isSuccess) {
            lastKnownVolume = newVolume
            "音量已减少 $decrement%，当前音量 $newVolume%"
        } else {
            "音量调节失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    private suspend fun handleMute(sessionId: String, repo: MusicRepository): String {
        val result = repo.setVolume(sessionId, 0)
        return if (result.isSuccess) {
            lastKnownVolume = 0
            "已静音"
        } else {
            "静音失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }
}
