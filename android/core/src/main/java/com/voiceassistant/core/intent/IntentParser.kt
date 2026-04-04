package com.voiceassistant.core.intent

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType

/**
 * Pure intent parser - no side effects, no dependencies.
 * Input: text string
 * Output: Intent data class
 *
 * This separation enables:
 * - Easy unit testing (no mocks needed)
 * - Predictable behavior (same input → same output)
 * - Independent evolution of parsing logic
 */
class IntentParser {

    fun parse(text: String): Intent {
        val normalized = text.lowercase().trim()

        return when {
            isMusicIntent(normalized) -> parseMusicIntent(normalized)
            isVolumeIntent(normalized) -> parseVolumeIntent(normalized)
            isDeviceIntent(normalized) -> parseDeviceIntent(normalized)
            isQueryIntent(normalized) -> Intent(IntentType.QUERY, query = text)
            else -> Intent(IntentType.CHAT, query = text)
        }
    }

    // ==================== Intent Detection ====================

    private fun isMusicIntent(text: String): Boolean =
        KEYWORDS_MUSIC.any { text.contains(it) }

    private fun isVolumeIntent(text: String): Boolean =
        KEYWORDS_VOLUME.any { text.contains(it) }

    private fun isDeviceIntent(text: String): Boolean =
        KEYWORDS_DEVICE.any { text.contains(it) }

    private fun isQueryIntent(text: String): Boolean =
        KEYWORDS_QUERY.any { text.contains(it) }

    // ==================== Intent Parsing ====================

    private fun parseMusicIntent(text: String): Intent {
        return when {
            text.contains("停止") -> Intent(IntentType.MUSIC, action = "stop")
            text.contains("暂停") -> Intent(IntentType.MUSIC, action = "pause")
            text.contains("继续") -> Intent(IntentType.MUSIC, action = "resume")
            text.contains("下一首") || text.contains("换一首") || text.contains("切歌") -> Intent(IntentType.MUSIC, action = "next")
            text.contains("上一首") -> Intent(IntentType.MUSIC, action = "previous")
            text.contains("播放") || text.contains("来一首") || text.contains("放歌") -> {
                val query = text.replace(MUSIC_QUERY_REGEX, "").trim()
                Intent(IntentType.MUSIC, action = "play", query = query.ifEmpty { null })
            }
            else -> Intent(IntentType.MUSIC, action = "play")
        }
    }

    private fun parseVolumeIntent(text: String): Intent {
        val value = extractNumber(text)
        return when {
            text.contains("调到") || text.contains("设为") -> Intent(IntentType.VOLUME, action = "set", value = value ?: 50)
            text.contains("大") || text.contains("高") || text.contains("加") -> Intent(IntentType.VOLUME, action = "up", value = value ?: 10)
            text.contains("小") || text.contains("低") || text.contains("减") -> Intent(IntentType.VOLUME, action = "down", value = value ?: 10)
            text.contains("静音") -> Intent(IntentType.VOLUME, action = "mute")
            else -> Intent(IntentType.VOLUME, action = "set", value = 50)
        }
    }

    private fun parseDeviceIntent(text: String): Intent {
        return when {
            text.contains("打开") -> Intent(IntentType.DEVICE, action = "on")
            text.contains("关闭") -> Intent(IntentType.DEVICE, action = "off")
            else -> Intent(IntentType.DEVICE, action = "toggle")
        }
    }

    private fun extractNumber(text: String): Int? =
        NUMBER_REGEX.find(text)?.value?.toIntOrNull()

    companion object {
        private val KEYWORDS_MUSIC = listOf("播放", "暂停", "继续", "下一首", "上一首", "来一首", "放歌", "听歌", "切歌")
        private val KEYWORDS_VOLUME = listOf("音量", "声音", "大声", "小声", "静音")
        private val KEYWORDS_DEVICE = listOf("打开", "关闭", "开关")
        private val KEYWORDS_QUERY = listOf("天气", "时间", "日期", "查询", "搜索", "是什么", "在哪里")

        private val NUMBER_REGEX = Regex("\\d+")
        private val MUSIC_QUERY_REGEX = Regex("(播放|来一首|放一首|放歌|听|我想听)")
    }
}
