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

package com.voiceassistant.core.skill

import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.skill.Skill
import com.voiceassistant.domain.skill.SkillContext
import com.voiceassistant.domain.skill.SkillResult
import com.voiceassistant.domain.usecase.HandleDeviceUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for handling device control commands.
 *
 * Handles:
 * - Turn device on (resume playback)
 * - Turn device off (stop playback)
 * - Toggle device state
 */
@Singleton
class DeviceSkill @Inject constructor(
    private val musicPlayer: MusicPlayer
) : Skill {

    override val name: String = "device"

    override val keywords: List<String> = listOf(
        "打开", "关闭", "开关"
    )

    override val priority: Int = 10

    override suspend fun handle(input: String, rawInput: String, context: SkillContext): SkillResult {
        val action = parseAction(input) ?: return SkillResult.NotHandled

        val sessionId = context.savedSessionId

        return when (action) {
            "on" -> handleOn(sessionId, context)
            "off" -> handleOff(sessionId, context)
            "toggle" -> handleToggle(sessionId, context)
            else -> SkillResult.NotHandled
        }
    }

    private fun parseAction(input: String): String? {
        return when {
            input.contains("打开") -> "on"
            input.contains("关闭") -> "off"
            input.contains("开关") -> "toggle"
            else -> null
        }
    }

    private suspend fun handleOn(sessionId: String?, context: SkillContext): SkillResult {
        if (sessionId == null) {
            return SkillResult.Success("设备未连接，无法打开")
        }

        if (context.isLocalSession(sessionId)) {
            return handleLocalDevice("on")
        }

        val deviceUseCase = context.handleDeviceUseCase
        if (deviceUseCase == null) {
            return SkillResult.Success("设备控制服务未配置")
        }

        val intent = Intent(IntentType.DEVICE, action = "on")
        val result = deviceUseCase.execute(intent, sessionId)
        return SkillResult.Success(result)
    }

    private suspend fun handleOff(sessionId: String?, context: SkillContext): SkillResult {
        if (sessionId == null) {
            return SkillResult.Success("设备未连接，无法关闭")
        }

        if (context.isLocalSession(sessionId)) {
            return handleLocalDevice("off")
        }

        val deviceUseCase = context.handleDeviceUseCase
        if (deviceUseCase == null) {
            return SkillResult.Success("设备控制服务未配置")
        }

        val intent = Intent(IntentType.DEVICE, action = "off")
        val result = deviceUseCase.execute(intent, sessionId)
        return SkillResult.Success(result)
    }

    private suspend fun handleToggle(sessionId: String?, context: SkillContext): SkillResult {
        if (sessionId == null) {
            return SkillResult.Success("设备未连接，无法切换状态")
        }

        if (context.isLocalSession(sessionId)) {
            return handleLocalDevice("toggle")
        }

        val deviceUseCase = context.handleDeviceUseCase
        if (deviceUseCase == null) {
            return SkillResult.Success("设备控制服务未配置")
        }

        val intent = Intent(IntentType.DEVICE, action = "toggle")
        val result = deviceUseCase.execute(intent, sessionId)
        return SkillResult.Success(result)
    }

    private fun handleLocalDevice(action: String): SkillResult {
        return when (action) {
            "on" -> {
                musicPlayer.resume()
                SkillResult.Success("已继续本机播放")
            }
            "off" -> {
                musicPlayer.stop()
                SkillResult.Success("已停止本机播放")
            }
            "toggle" -> {
                if (musicPlayer.getState().isPlaying) {
                    musicPlayer.pause()
                    SkillResult.Success("已暂停本机播放")
                } else {
                    musicPlayer.resume()
                    SkillResult.Success("已继续本机播放")
                }
            }
            else -> SkillResult.Success("设备操作")
        }
    }
}