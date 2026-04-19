package com.voiceassistant.core.skill

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.skill.Skill
import com.voiceassistant.domain.skill.SkillContext
import com.voiceassistant.domain.skill.SkillResult
import com.voiceassistant.domain.usecase.HandleVolumeUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for handling volume control commands.
 *
 * Handles:
 * - Set volume to specific level
 * - Increase/decrease volume
 * - Mute/unmute
 */
@Singleton
class VolumeSkill @Inject constructor() : Skill {

    override val name: String = "volume"

    override val keywords: List<String> = listOf(
        "音量", "声音", "大声", "小声", "静音"
    )

    override val priority: Int = 10

    private var lastKnownVolume: Int = 50

    override suspend fun handle(input: String, rawInput: String, context: SkillContext): SkillResult {
        val (action, value) = parseVolumeIntent(input)
            ?: return SkillResult.NotHandled

        val sessionId = context.savedSessionId

        return when (action) {
            "set" -> handleSet(value ?: 50, sessionId, context)
            "up" -> handleUp(value ?: 10, sessionId, context)
            "down" -> handleDown(value ?: 10, sessionId, context)
            "mute" -> handleMute(sessionId, context)
            else -> SkillResult.NotHandled
        }
    }

    private fun parseVolumeIntent(text: String): Pair<String, Int?>? {
        val value = extractNumber(text)

        return when {
            text.contains("调到") || text.contains("设为") -> {
                "set" to (value ?: 50)
            }
            text.contains("大") || text.contains("高") || text.contains("加") -> {
                "up" to (value ?: 10)
            }
            text.contains("小") || text.contains("低") || text.contains("减") -> {
                "down" to (value ?: 10)
            }
            text.contains("静音") -> "mute" to null
            else -> null
        }
    }

    private fun extractNumber(text: String): Int? {
        val regex = Regex("\\d+")
        return regex.find(text)?.value?.toIntOrNull()
    }

    private suspend fun handleSet(value: Int, sessionId: String?, context: SkillContext): SkillResult {
        val volume = value.coerceIn(0, 100)

        if (sessionId == null || context.isLocalSession(sessionId)) {
            return SkillResult.Success("本机播放暂不支持语音调节音量")
        }

        val volumeUseCase = context.handleVolumeUseCase
        if (volumeUseCase == null) {
            return SkillResult.Success("音量控制服务未配置")
        }

        val intent = Intent(IntentType.VOLUME, action = "set", value = volume)
        val result = volumeUseCase.execute(intent, sessionId, false)
        return SkillResult.Success(result)
    }

    private suspend fun handleUp(value: Int, sessionId: String?, context: SkillContext): SkillResult {
        if (sessionId == null || context.isLocalSession(sessionId)) {
            return SkillResult.Success("本机播放暂不支持语音调节音量")
        }

        val volumeUseCase = context.handleVolumeUseCase
        if (volumeUseCase == null) {
            return SkillResult.Success("音量控制服务未配置")
        }

        val intent = Intent(IntentType.VOLUME, action = "up", value = value)
        val result = volumeUseCase.execute(intent, sessionId, false)
        return SkillResult.Success(result)
    }

    private suspend fun handleDown(value: Int, sessionId: String?, context: SkillContext): SkillResult {
        if (sessionId == null || context.isLocalSession(sessionId)) {
            return SkillResult.Success("本机播放暂不支持语音调节音量")
        }

        val volumeUseCase = context.handleVolumeUseCase
        if (volumeUseCase == null) {
            return SkillResult.Success("音量控制服务未配置")
        }

        val intent = Intent(IntentType.VOLUME, action = "down", value = value)
        val result = volumeUseCase.execute(intent, sessionId, false)
        return SkillResult.Success(result)
    }

    private suspend fun handleMute(sessionId: String?, context: SkillContext): SkillResult {
        if (sessionId == null || context.isLocalSession(sessionId)) {
            return SkillResult.Success("本机播放暂不支持语音调节音量")
        }

        val volumeUseCase = context.handleVolumeUseCase
        if (volumeUseCase == null) {
            return SkillResult.Success("音量控制服务未配置")
        }

        val intent = Intent(IntentType.VOLUME, action = "mute")
        val result = volumeUseCase.execute(intent, sessionId, false)
        return SkillResult.Success(result)
    }
}