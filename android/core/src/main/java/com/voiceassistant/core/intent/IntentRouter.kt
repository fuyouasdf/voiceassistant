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

package com.voiceassistant.core.intent

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.LLMRouteDecision
import com.voiceassistant.domain.repository.LLMRouteMode
import com.voiceassistant.domain.skill.SkillContext
import com.voiceassistant.domain.skill.SkillRegistry
import com.voiceassistant.domain.skill.SkillResult
import com.voiceassistant.domain.usecase.HandleChatUseCase
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Intent types for voice commands
 */
enum class IntentType {
    MUSIC,      // Play/pause music
    VOLUME,     // Volume control
    DEVICE,     // Device control
    QUERY,      // Information query (requires LLM)
    CHAT,       // General chat (requires LLM)
    UNKNOWN     // Unknown intent
}

/**
 * Intent data class
 */
data class Intent(
    val type: IntentType,
    val action: String? = null,
    val query: String? = null,
    val artist: String? = null,  // 歌手名
    val value: Int? = null
)

/**
 * Routes voice commands to appropriate skills via SkillRegistry.
 *
 * This class handles intent parsing and skill routing.
 * Actual execution is delegated to skills registered in SkillRegistry.
 *
 * @param skillRegistry Registry holding all registered skills
 * @param skillContextFactory Factory for creating skill context
 * @param llmRepository For chat functionality
 */
class IntentRouter @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val skillContext: SkillContext,
    private val llmRepository: LLMRepository?,
    private val handleChatUseCase: HandleChatUseCase
) {

    /**
     * Parse text and determine intent type (for logging/routing decisions).
     * This is used for LLM routing decisions but actual handling is via skills.
     */
    fun parse(text: String): Intent {
        val normalized = text.lowercase().trim()

        return when {
            // Music intents
            isMusicIntent(normalized) -> parseMusicIntent(normalized)
            // Volume intents
            isVolumeIntent(normalized) -> parseVolumeIntent(normalized)
            // Device intents
            isDeviceIntent(normalized) -> parseDeviceIntent(normalized)
            // Query intents (weather, time, etc.)
            isQueryIntent(normalized) -> Intent(IntentType.QUERY, query = text)
            // Default to chat
            else -> Intent(IntentType.CHAT, query = text)
        }
    }

    /**
     * Handle text input and return response.
     * This is a suspend function that handles actual operations.
     *
     * Flow:
     * 1. Fast local path for time queries
     * 2. Try skill registry for local intents (MUSIC, VOLUME, DEVICE)
     * 3. If LLM available, use it for routing/parsing
     * 4. Fallback to ChatSkill
     */
    suspend fun handle(text: String): String {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) {
            return "没听懂，请再说一遍"
        }

        // Fast local path for time queries (no skill needed)
        getTimeQueryResponse(normalizedText)?.let { return it }

        // Try skill registry first for local intents
        val skillResult = skillRegistry.dispatch(normalizedText.lowercase(), normalizedText, skillContext)

        if (skillResult is SkillResult.Success) {
            return skillResult.response
        }

        // If skill returned NotHandled, try LLM routing
        if (skillResult is SkillResult.NotHandled || skillResult is SkillResult.Error) {
            val llm = llmRepository
            if (llm != null) {
                val startTime = System.currentTimeMillis()
                Timber.d("IntentRouter: calling routeIntent...")
                val routingResult = llm.routeIntent(normalizedText).fold(
                    onSuccess = { it },
                    onFailure = {
                        Timber.w(it, "LLM routing failed")
                        null
                    }
                )
                Timber.d("IntentRouter: routeIntent took ${System.currentTimeMillis() - startTime}ms")

                if (routingResult != null) {
                    return when (routingResult.mode) {
                        LLMRouteMode.CHAT -> handleChatWithContext(normalizedText)
                        LLMRouteMode.COMMAND -> handleAsCommand(normalizedText, routingResult)
                    }
                }
            }

            // No LLM or routing failed, try ChatSkill directly
            if (skillResult is SkillResult.NotHandled) {
                return handleChatWithContext(normalizedText)
            }

            if (skillResult is SkillResult.Error) {
                return skillResult.message
            }
        }

        // Final fallback
        return handleChatWithContext(normalizedText)
    }

    /**
     * Handle text as a command parsed by LLM.
     */
    private suspend fun handleAsCommand(text: String, routingResult: LLMRouteDecision): String {
        val llmIntent = llmRepository?.parseCommandIntent(text)?.fold(
            onSuccess = { it },
            onFailure = {
                Timber.w(it, "LLM command parsing failed")
                null
            }
        ) ?: return handleChatWithContext(text)

        val type = try {
            IntentType.valueOf(llmIntent.type)
        } catch (_: IllegalArgumentException) {
            IntentType.UNKNOWN
        }

        val intent = when (type) {
            IntentType.MUSIC -> Intent(
                type = IntentType.MUSIC,
                action = llmIntent.action ?: "play",
                query = llmIntent.query
            )
            IntentType.VOLUME -> Intent(
                type = IntentType.VOLUME,
                action = llmIntent.action ?: "set",
                value = llmIntent.value
            )
            IntentType.DEVICE -> Intent(
                type = IntentType.DEVICE,
                action = llmIntent.action ?: "toggle"
            )
            IntentType.QUERY -> Intent(
                type = IntentType.QUERY,
                action = llmIntent.action ?: "ask",
                query = llmIntent.query ?: text
            )
            IntentType.CHAT -> Intent(
                type = IntentType.CHAT,
                query = llmIntent.query ?: text
            )
            IntentType.UNKNOWN -> Intent(
                type = IntentType.UNKNOWN,
                query = llmIntent.query ?: text
            )
        }

        if (intent.type == IntentType.UNKNOWN) {
            return handleChatWithContext(text)
        }

        // Dispatch to skill registry with the parsed intent
        return dispatchToSkill(intent, text)
    }

    /**
     * Dispatch an intent to the appropriate skill.
     */
    private suspend fun dispatchToSkill(intent: Intent, rawText: String): String {
        val skillName = when (intent.type) {
            IntentType.MUSIC -> "music"
            IntentType.VOLUME -> "volume"
            IntentType.DEVICE -> "device"
            IntentType.QUERY -> "query"
            IntentType.CHAT -> "chat"
            IntentType.UNKNOWN -> return handleChatWithContext(rawText)
        }

        val skill = skillRegistry.getSkill(skillName)
        if (skill != null) {
            val result = skill.handle(rawText.lowercase(), rawText, skillContext)
            if (result is SkillResult.Success) {
                return result.response
            }
        }

        // Skill not found or failed, fallback to chat
        return handleChatWithContext(rawText)
    }

    /**
     * Handle CHAT type intent with conversation context.
     */
    private suspend fun handleChatWithContext(text: String): String {
        return handleChat(Intent(IntentType.CHAT, query = text))
    }

    /**
     * Handle chat intent using HandleChatUseCase.
     */
    private suspend fun handleChat(intent: Intent): String {
        return handleChatUseCase.execute(
            Intent(
                type = com.voiceassistant.domain.model.IntentType.CHAT,
                query = intent.query
            )
        )
    }

    /**
     * Handle query using LLM.
     */
    private suspend fun handleQueryWithLLM(text: String): String {
        val llm = llmRepository
        if (llm == null) {
            return "需要联网才能回答这个问题"
        }

        return llm.chat(text).fold(
            onSuccess = { it },
            onFailure = { "查询失败，请稍后重试" }
        )
    }

    private fun isMusicIntent(text: String): Boolean {
        val keywords = listOf("播放", "暂停", "继续", "停止", "下一首", "上一首", "来一首", "放歌", "听歌", "切歌")
        return keywords.any { text.contains(it) }
    }

    private fun parseMusicIntent(text: String): Intent {
        return when {
            text.contains("停止") -> Intent(IntentType.MUSIC, action = "stop")
            text.contains("暂停") -> Intent(IntentType.MUSIC, action = "pause")
            text.contains("继续") -> Intent(IntentType.MUSIC, action = "resume")
            text.contains("下一首") || text.contains("换一首") || text.contains("切歌") -> Intent(IntentType.MUSIC, action = "next")
            text.contains("上一首") -> Intent(IntentType.MUSIC, action = "previous")
            text.contains("播放") || text.contains("来一首") || text.contains("放歌") -> {
                val (artist, songName, _) = parseArtistAndSong(text)
                Intent(IntentType.MUSIC, action = "play", query = songName, artist = artist)
            }
            else -> Intent(IntentType.MUSIC, action = "play")
        }
    }

    /**
     * Parse artist and song name from text.
     * Pattern: "播放周杰伦的双截棍" → artist="周杰伦", song="双截棍"
     *          "播放双截棍" → artist=null, song="双截棍"
     */
    private fun parseArtistAndSong(text: String): Triple<String?, String?, String> {
        val cleanText = text.replace(Regex("(播放|来一首|放一首|放歌|听|我想听)"), "").trim()

        val dePattern = Regex("^(.+?)的([^的]+)$")
        val match = dePattern.find(cleanText)

        if (match != null) {
            val potentialArtist = match.groupValues[1].trim()
            val potentialSong = match.groupValues[2].trim()

            val invalidArtists = listOf("音乐", "歌曲", "这首", "那首", "歌", "专辑", "歌手")
            if (potentialArtist !in invalidArtists && potentialSong.isNotEmpty() && potentialArtist.isNotEmpty()) {
                Timber.d("parseArtistAndSong: artist='$potentialArtist', song='$potentialSong'")
                return Triple(potentialArtist, potentialSong, potentialSong)
            }
        }

        Timber.d("parseArtistAndSong: no artist found, song='$cleanText'")
        return Triple(null, cleanText, cleanText)
    }

    private fun isVolumeIntent(text: String): Boolean {
        val keywords = listOf("音量", "声音", "大声", "小声", "静音")
        return keywords.any { text.contains(it) }
    }

    private fun parseVolumeIntent(text: String): Intent {
        val value = extractNumber(text)

        return when {
            // 调到XX%、设为XX%、音量到XX% 等都是设置绝对音量
            text.contains("音量到") || text.contains("音量设为") || text.contains("音量调到") -> {
                Intent(IntentType.VOLUME, action = "set", value = value ?: 50)
            }
            // 调大、调高、增加 - 相对增加
            (text.contains("调") && text.contains("大")) || text.contains("高") || text.contains("加") -> {
                Intent(IntentType.VOLUME, action = "up", value = value ?: 10)
            }
            // 调小、调低、减少 - 相对减少
            (text.contains("调") && text.contains("小")) || text.contains("低") || text.contains("减") -> {
                Intent(IntentType.VOLUME, action = "down", value = value ?: 10)
            }
            text.contains("静音") -> Intent(IntentType.VOLUME, action = "mute")
            else -> Intent(IntentType.VOLUME, action = "set", value = 50)
        }
    }

    private fun isDeviceIntent(text: String): Boolean {
        val keywords = listOf("打开", "关闭", "开关")
        return keywords.any { text.contains(it) }
    }

    private fun parseDeviceIntent(text: String): Intent {
        return when {
            text.contains("打开") -> Intent(IntentType.DEVICE, action = "on")
            text.contains("关闭") -> Intent(IntentType.DEVICE, action = "off")
            else -> Intent(IntentType.DEVICE, action = "toggle")
        }
    }

    private fun isQueryIntent(text: String): Boolean {
        val keywords = listOf("天气", "时间", "日期", "查询", "搜索", "是什么", "在哪里")
        return keywords.any { text.contains(it) }
    }

    private fun extractNumber(text: String): Int? {
        // 先尝试阿拉伯数字
        val digitRegex = Regex("\\d+")
        digitRegex.find(text)?.value?.toIntOrNull()?.let { return it }

        // 尝试解析中文数字 "百分之X" 或 "百分之X%"
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

    private fun getTimeQueryResponse(rawText: String): String? {
        val text = rawText.lowercase().trim()
        if (text.isEmpty()) return null

        val now = LocalDateTime.now()

        if (text.contains("几点") || text.contains("几时") || text.contains("当前时间") ||
            (text.contains("现在") && text.contains("时间"))) {
            return "现在是${now.hour}点${now.minute}分"
        }

        if (text.contains("星期几") || text.contains("周几")) {
            return "今天是${weekdayToChinese(now.dayOfWeek.value)}"
        }

        val askDate = text.contains("几号") || text.contains("几月几号") || text.contains("日期")
        if (!askDate) return null

        val offset = when {
            text.contains("前天") -> -2L
            text.contains("昨天") -> -1L
            text.contains("明天") -> 1L
            text.contains("后天") -> 2L
            else -> 0L
        }
        val date = now.toLocalDate().plusDays(offset)
        return "${date.monthValue}月${date.dayOfMonth}号，${weekdayToChinese(date.dayOfWeek.value)}"
    }

    private fun weekdayToChinese(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            7 -> "星期日"
            else -> "未知"
        }
    }
}