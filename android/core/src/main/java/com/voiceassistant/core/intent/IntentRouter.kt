package com.voiceassistant.core.intent

import com.voiceassistant.domain.model.Intent as DomainIntent
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.model.IntentType as DomainIntentType
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.LLMRouteMode
import com.voiceassistant.domain.usecase.HandleChatUseCase
import java.time.LocalDateTime
import timber.log.Timber
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
    val value: Int? = null,
    val song: Song? = null
)

/**
 * Interface for conversation context that provides contextual information
 * to enable context-aware chat responses.
 */
interface ConversationContext {
    /**
     * Build a context string that can be prepended to chat messages
     * to provide conversation history and state information.
     *
     * @return A string containing context information, or empty string if no context available
     */
    suspend fun buildContextString(): String

    /**
     * Get the number of context items currently stored.
     * Useful for debugging and logging purposes.
     *
     * @return The count of context items available
     */
    fun getContextCount(): Int
}

/**
 * Routes voice commands to appropriate handlers
 *
 * This class handles intent parsing and LLM routing.
 * Actual execution is delegated to IntentExecutor (domain layer).
 *
 * @param intentExecutor Executes intents via domain use cases
 * @param llmRepository For chat functionality
 * @param handleChatUseCase For handling chat intents
 * @param conversationContext Provides conversation history for context-aware responses
 */
class IntentRouter @Inject constructor(
    private val intentExecutor: IntentExecutor,
    private val llmRepository: LLMRepository?,
    private val handleChatUseCase: HandleChatUseCase,
    private val conversationContext: ConversationContext
) {

    /**
     * Parse text and determine intent
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
     * Handle text input and return response
     * This is a suspend function that handles actual operations
     */
    suspend fun handle(text: String): String {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) {
            return "没听懂，请再说一遍"
        }

        getTimeQueryResponse(normalizedText)?.let { return it }

        // Fast local path for deterministic commands
        val localIntent = parse(normalizedText)
        if (shouldUseLocalIntent(localIntent)) {
            return handleIntent(localIntent)
        }

        val llm = llmRepository
        if (llm == null) {
            return handleIntent(localIntent)
        }

        val routedMode = llm.routeIntent(normalizedText).fold(
            onSuccess = { it.mode },
            onFailure = {
                Timber.w(it, "LLM routing failed, fallback to local intent")
                return@fold null
            }
        )

        if (routedMode == null) {
            return handleIntent(localIntent)
        }

        if (routedMode == LLMRouteMode.CHAT) {
            return handleChatWithContext(normalizedText)
        }

        val llmIntent = llm.parseCommandIntent(normalizedText).fold(
            onSuccess = { toIntent(it, normalizedText) },
            onFailure = {
                Timber.w(it, "LLM command parsing failed, fallback to local intent")
                localIntent
            }
        )

        return if (llmIntent.type == IntentType.UNKNOWN) {
            handleChatWithContext(normalizedText)
        } else {
            handleIntent(llmIntent)
        }
    }

    private fun shouldUseLocalIntent(intent: Intent): Boolean {
        return intent.type == IntentType.MUSIC ||
            intent.type == IntentType.VOLUME ||
            intent.type == IntentType.DEVICE
    }

    private fun toIntent(parsed: com.voiceassistant.domain.repository.LLMParsedIntent, originalText: String): Intent {
        val type = try {
            IntentType.valueOf(parsed.type)
        } catch (_: IllegalArgumentException) {
            IntentType.UNKNOWN
        }

        return when (type) {
            IntentType.MUSIC -> Intent(
                type = IntentType.MUSIC,
                action = parsed.action ?: "play",
                query = parsed.query
            )
            IntentType.VOLUME -> Intent(
                type = IntentType.VOLUME,
                action = parsed.action ?: "set",
                value = parsed.value
            )
            IntentType.DEVICE -> Intent(
                type = IntentType.DEVICE,
                action = parsed.action ?: "toggle"
            )
            IntentType.QUERY -> Intent(
                type = IntentType.QUERY,
                action = parsed.action ?: "ask",
                query = parsed.query ?: originalText
            )
            IntentType.CHAT -> Intent(
                type = IntentType.CHAT,
                query = parsed.query ?: originalText
            )
            IntentType.UNKNOWN -> Intent(
                type = IntentType.UNKNOWN,
                query = parsed.query ?: originalText
            )
        }
    }

    /**
     * Handle the intent and return response
     */
    private suspend fun handleIntent(intent: Intent): String {
        Timber.d("Handling intent: $intent")

        // Delegate MUSIC, VOLUME, DEVICE to IntentExecutor (domain layer)
        // QUERY and CHAT are handled directly here
        return when (intent.type) {
            IntentType.MUSIC, IntentType.VOLUME, IntentType.DEVICE -> {
                val domainIntent = toDomainIntent(intent)
                intentExecutor.execute(domainIntent)
            }
            IntentType.QUERY -> handleQuery(intent)
            IntentType.CHAT -> handleChat(intent)
            IntentType.UNKNOWN -> "没听懂，请再说一遍"
        }
    }

    /**
     * Handle CHAT type intent with conversation context
     */
    private suspend fun handleChat(intent: Intent): String {
        val contextString = conversationContext.buildContextString()
        val contextCount = conversationContext.getContextCount()

        if (contextCount > 0) {
            Timber.d("handleChat: context available ($contextCount items), prepending context")
        }

        // Prepend context to the query for context-aware responses
        val queryWithContext = if (contextString.isNotEmpty()) {
            "$contextString\n\n当前消息: ${intent.query ?: ""}"
        } else {
            intent.query ?: ""
        }

        val chatIntent = Intent(
            type = intent.type,
            action = intent.action,
            query = queryWithContext,
            artist = intent.artist,
            value = intent.value,
            song = intent.song
        )

        return intentExecutor.execute(toDomainIntent(chatIntent))
    }

    /**
     * Helper to handle chat with context from raw text
     */
    private suspend fun handleChatWithContext(text: String): String {
        return handleChat(Intent(IntentType.CHAT, query = text))
    }

    /**
     * Convert core Intent to domain Intent
     */
    private fun toDomainIntent(intent: Intent): com.voiceassistant.domain.model.Intent {
        return com.voiceassistant.domain.model.Intent(
            type = com.voiceassistant.domain.model.IntentType.valueOf(intent.type.name),
            action = intent.action,
            query = intent.query,
            artist = intent.artist,
            value = intent.value,
            song = intent.song
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
                val (artist, songName, rawQuery) = parseArtistAndSong(text)
                Intent(IntentType.MUSIC, action = "play", query = songName, artist = artist)
            }
            else -> Intent(IntentType.MUSIC, action = "play")
        }
    }

    /**
     * 解析歌手和歌曲名
     * 模式: "播放周杰伦的双截棍" → artist="周杰伦", song="双截棍"
     *       "播放双截棍" → artist=null, song="双截棍"
     */
    private fun parseArtistAndSong(text: String): Triple<String?, String?, String> {
        val cleanText = text.replace(Regex("(播放|来一首|放一首|放歌|听|我想听)"), "").trim()

        // 尝试匹配"XXX的YYY"模式（XXX是歌手，YYY是歌名）
        // 但要排除一些特殊情况，如"音乐的"、"歌曲的"等
        val dePattern = Regex("^(.+?)的([^的]+)$")
        val match = dePattern.find(cleanText)

        if (match != null) {
            val potentialArtist = match.groupValues[1].trim()
            val potentialSong = match.groupValues[2].trim()

            // 过滤掉明显不是歌手名的词
            val invalidArtists = listOf("音乐", "歌曲", "这首", "那首", "歌", "专辑", "歌手")
            if (potentialArtist !in invalidArtists && potentialSong.isNotEmpty() && potentialArtist.isNotEmpty()) {
                Timber.d("parseArtistAndSong: artist='$potentialArtist', song='$potentialSong'")
                return Triple(potentialArtist, potentialSong, potentialSong) // 同时返回歌曲名作为原始查询
            }
        }

        // 没有找到"XXX的YYY"模式，直接返回歌曲名
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
            text.contains("调到") || text.contains("设为") -> {
                Intent(IntentType.VOLUME, action = "set", value = value ?: 50)
            }
            text.contains("大") || text.contains("高") || text.contains("加") -> {
                Intent(IntentType.VOLUME, action = "up", value = value ?: 10)
            }
            text.contains("小") || text.contains("低") || text.contains("减") -> {
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
        val regex = Regex("\\d+")
        return regex.find(text)?.value?.toIntOrNull()
    }

    private suspend fun handleQuery(intent: Intent): String {
        val llm = llmRepository
        if (llm == null) {
            // Fallback to simple response when LLM is not configured
            val query = intent.query ?: ""
            return when {
                query.contains("天气") -> "抱歉，我需要联网才能查询天气"
                query.contains("时间") || query.contains("日期") -> "抱歉，我需要联网才能查询时间"
                else -> "需要联网才能回答这个问题"
            }
        }

        // Use LLM to handle the query
        return llm.chat(intent.query ?: "").fold(
            onSuccess = { it },
            onFailure = { "查询失败，请稍后重试" }
        )
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
