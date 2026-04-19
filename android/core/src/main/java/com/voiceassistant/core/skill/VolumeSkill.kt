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
            // 调到XX%、设为XX%、音量到XX% 等都是设置绝对音量
            text.contains("音量到") || text.contains("音量设为") || text.contains("音量调到") -> {
                "set" to (value ?: 50)
            }
            // 调大、调高、增加 - 相对增加
            (text.contains("调") && text.contains("大")) || text.contains("高") || text.contains("加") -> {
                "up" to (value ?: 10)
            }
            // 调小、调低、减少 - 相对减少
            (text.contains("调") && text.contains("小")) || text.contains("低") || text.contains("减") -> {
                "down" to (value ?: 10)
            }
            text.contains("静音") -> "mute" to null
            else -> null
        }
    }

    private fun extractNumber(text: String): Int? {
        // 先尝试阿拉伯数字
        val digitRegex = Regex("\\d+")
        digitRegex.find(text)?.value?.toIntOrNull()?.let { return it }

        // 尝试解析中文数字 "百分之X"
        val chinesePercentRegex = Regex("百分之([一二三四五六七八九十百]+)")
        chinesePercentRegex.find(text)?.let { match ->
            return chineseToNumber(match.groupValues[1])
        }

        // 尝试解析纯中文数字
        val chineseRegex = Regex("[一二三四五六七八九十百]+")
        chineseRegex.find(text)?.let { match ->
            return chineseToNumber(match.value)
        }

        return null
    }

    /**
     * 中文数字转阿拉伯数字
     */
    private fun chineseToNumber(chinese: String): Int {
        val map = mapOf(
            "零" to 0, "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10
        )
        return when {
            chinese.contains("百") -> {
                val parts = chinese.split("百")
                val hundred = map[parts.getOrNull(0)] ?: 1
                val tens = if (parts.size > 1) map[parts[1].replace("十", "")] ?: 0 else 0
                val ones = if (parts.size > 1 && parts[1].contains("十")) 10 else 0
                hundred * 100 + tens * 10 + ones
            }
            chinese.contains("十") -> {
                val parts = chinese.split("十")
                val tens = if (parts[0].isEmpty()) 1 else map[parts[0]] ?: 1
                val ones = if (parts.size > 1 && parts[1].isNotEmpty()) map[parts[1]] ?: 0 else 0
                tens * 10 + ones
            }
            else -> map[chinese] ?: 0
        }
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