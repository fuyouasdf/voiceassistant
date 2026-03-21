package com.voiceassistant.core.intent

import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.LLMRepository
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
    val value: Int? = null,
    val song: Song? = null
)

/**
 * Routes voice commands to appropriate handlers
 * @param musicRepository For music playback
 * @param llmRepository For chat functionality
 * @param dlnaController For device control (optional)
 */
class IntentRouter @Inject constructor(
    private val musicRepository: MusicRepository?,
    private val llmRepository: LLMRepository?
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
        val intent = parse(text)
        return handleIntent(intent)
    }

    /**
     * Handle the intent and return response
     */
    private suspend fun handleIntent(intent: Intent): String {
        Timber.d("Handling intent: $intent")

        return when (intent.type) {
            IntentType.MUSIC -> handleMusic(intent)
            IntentType.VOLUME -> handleVolume(intent)
            IntentType.DEVICE -> handleDevice(intent)
            IntentType.QUERY -> handleQuery(intent)
            IntentType.CHAT -> handleChat(intent)
            IntentType.UNKNOWN -> "没听懂，请再说一遍"
        }
    }

    private fun isMusicIntent(text: String): Boolean {
        val keywords = listOf("播放", "暂停", "继续", "下一首", "上一首", "来一首", "放歌", "听歌", "切歌")
        return keywords.any { text.contains(it) }
    }

    private fun parseMusicIntent(text: String): Intent {
        return when {
            text.contains("播放") || text.contains("来一首") || text.contains("放歌") -> {
                val query = text.replace(Regex("(播放|来一首|放一首|放歌|听|我想听)"), "").trim()
                Intent(IntentType.MUSIC, action = "play", query = query)
            }
            text.contains("暂停") -> Intent(IntentType.MUSIC, action = "pause")
            text.contains("继续") -> Intent(IntentType.MUSIC, action = "resume")
            text.contains("下一首") || text.contains("换一首") || text.contains("切歌") -> Intent(IntentType.MUSIC, action = "next")
            text.contains("上一首") -> Intent(IntentType.MUSIC, action = "previous")
            text.contains("停止") -> Intent(IntentType.MUSIC, action = "stop")
            else -> Intent(IntentType.MUSIC, action = "play")
        }
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

    private suspend fun handleMusic(intent: Intent): String {
        val musicRepo = musicRepository
        if (musicRepo == null) {
            return "音乐服务未配置"
        }

        return when (intent.action) {
            "play" -> {
                val query = intent.query ?: ""
                if (query.isEmpty()) {
                    return "请告诉我你想听什么歌曲"
                }

                val result = musicRepo.searchSongs(query)
                result.fold(
                    onSuccess = { songs ->
                        if (songs.isEmpty()) {
                            "没找到关于「$query」的歌曲"
                        } else {
                            val song = songs.first()
                            val streamUrl = musicRepo.getStreamUrl(song.id)
                            // Store stream URL for playback (would need DLNA integration)
                            "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
                        }
                    },
                    onFailure = { "搜索歌曲失败，请稍后重试" }
                )
            }
            "pause" -> "已暂停播放"
            "resume" -> "继续播放"
            "next" -> "正在播放下一首"
            "previous" -> "正在播放上一首"
            "stop" -> "已停止播放"
            else -> "音乐操作"
        }
    }

    private fun handleVolume(intent: Intent): String {
        return when (intent.action) {
            "set" -> "音量调到 ${intent.value}%"
            "up" -> "音量增加 ${intent.value}%"
            "down" -> "音量减少 ${intent.value}%"
            "mute" -> "已静音"
            else -> "音量操作"
        }
    }

    private fun handleDevice(intent: Intent): String {
        return when (intent.action) {
            "on" -> "已打开设备"
            "off" -> "已关闭设备"
            "toggle" -> "已切换设备状态"
            else -> "设备操作"
        }
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
        return llm.chat(intent.query ?: "").getOrElse {
            "查询失败，请稍后重试"
        }
    }

    private suspend fun handleChat(intent: Intent): String {
        val llm = llmRepository
        if (llm == null) {
            return "需要联网才能聊天，请配置 LLM API"
        }

        return llm.chat(intent.query ?: "").getOrElse {
            "抱歉，聊天服务暂时不可用"
        }
    }
}