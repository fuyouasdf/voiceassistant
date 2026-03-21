package com.voiceassistant.core.intent

import timber.log.Timber

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
    val value: Int? = null
)

/**
 * Routes voice commands to appropriate handlers
 */
class IntentRouter {
    
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
     */
    suspend fun handle(text: String): String {
        val intent = parse(text)
        return handleIntent(intent)
    }

    /**
     * Handle the intent and return response
     */
    private fun handleIntent(intent: Intent): String {
        Timber.d("Handling intent: $intent")
        
        return when (intent.type) {
            IntentType.MUSIC -> handleMusic(intent)
            IntentType.VOLUME -> handleVolume(intent)
            IntentType.DEVICE -> handleDevice(intent)
            IntentType.QUERY -> "需要联网才能查询"
            IntentType.CHAT -> "需要联网才能聊天"
            IntentType.UNKNOWN -> "没听懂，请再说一遍"
        }
    }
    
    private fun isMusicIntent(text: String): Boolean {
        val keywords = listOf("播放", "暂停", "继续", "下一首", "上一首", "来一首", "放歌", "听歌")
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
            text.contains("下一首") || text.contains("换一首") -> Intent(IntentType.MUSIC, action = "next")
            text.contains("上一首") -> Intent(IntentType.MUSIC, action = "previous")
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
    
    private fun handleMusic(intent: Intent): String {
        return when (intent.action) {
            "play" -> "正在搜索: ${intent.query}"
            "pause" -> "已暂停"
            "resume" -> "继续播放"
            "next" -> "下一首"
            "previous" -> "上一首"
            else -> "音乐操作"
        }
    }
    
    private fun handleVolume(intent: Intent): String {
        return when (intent.action) {
            "set" -> "音量调到 ${intent.value}"
            "up" -> "音量增大 ${intent.value}"
            "down" -> "音量减小 ${intent.value}"
            "mute" -> "已静音"
            else -> "音量操作"
        }
    }
    
    private fun handleDevice(intent: Intent): String {
        return when (intent.action) {
            "on" -> "已打开"
            "off" -> "已关闭"
            "toggle" -> "已切换"
            else -> "设备操作"
        }
    }
}
