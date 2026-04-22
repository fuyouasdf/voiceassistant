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
            text.contains("播放") || text.contains("来一首") || text.contains("放歌") || text.contains("听") -> {
                val query = extractMusicQuery(text)
                Intent(IntentType.MUSIC, action = "play", query = query.ifEmpty { null })
            }
            else -> Intent(IntentType.MUSIC, action = "play")
        }
    }

    /**
     * 提取音乐查询词
     * 移除音乐意图前缀，保留歌曲/歌手名
     */
    private fun extractMusicQuery(text: String): String {
        // 按长度降序排列，确保"我想听"优先于"听"
        val prefixes = listOf("播放", "来一首", "放一首", "放歌", "我想听", "我想播放", "我想来一首", "听").sortedByDescending { it.length }
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                return text.removePrefix(prefix).trim()
            }
        }
        // 前缀不在开头，尝试从头匹配
        for (prefix in prefixes) {
            val index = text.indexOf(prefix)
            if (index == 0) {
                return text.removePrefix(prefix).trim()
            }
        }
        return text
    }

    private fun parseVolumeIntent(text: String): Intent {
        val value = extractNumber(text)
        return when {
            // 调到XX%、设为XX%、音量到XX% 等都是设置绝对音量
            text.contains("音量到") || text.contains("音量设为") || text.contains("音量调到") -> Intent(IntentType.VOLUME, action = "set", value = value ?: 50)
            // 调大、调高、增加 - 相对增加
            (text.contains("调") && text.contains("大")) || text.contains("高") || text.contains("加") -> Intent(IntentType.VOLUME, action = "up", value = value ?: 10)
            // 调小、调低、减少 - 相对减少
            (text.contains("调") && text.contains("小")) || text.contains("低") || text.contains("减") -> Intent(IntentType.VOLUME, action = "down", value = value ?: 10)
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

    private fun extractNumber(text: String): Int? {
        // 先尝试阿拉伯数字
        NUMBER_REGEX.find(text)?.value?.toIntOrNull()?.let { return it }

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

    companion object {
        private val KEYWORDS_MUSIC = listOf("播放", "暂停", "继续", "停止", "下一首", "上一首", "来一首", "放歌", "听歌", "切歌", "换一首", "听")
        private val KEYWORDS_VOLUME = listOf("音量", "声音", "大声", "小声", "静音", "高", "低")
        private val KEYWORDS_DEVICE = listOf("打开", "关闭", "开关")
        private val KEYWORDS_QUERY = listOf("天气", "时间", "日期", "查询", "搜索", "是什么", "在哪里", "几点", "几号")

        private val NUMBER_REGEX = Regex("\\d+")
    }
}
