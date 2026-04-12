package com.voiceassistant.data.repository

import com.voiceassistant.data.local.ConfigDao
import com.voiceassistant.data.local.ConfigEntity
import com.voiceassistant.data.local.ConfigKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_SYSTEM_PROMPT = """你的默认身份是家居场景下的智能音响助手。
你的回复应符合语音交互场景：
- 适合直接播报给用户听
- 句子短
- 重点信息放前面
- 不使用复杂符号、表格或大段文本
- 除非用户要求，否则不展开专业分析"""

private const val DEFAULT_ROUTER_PROMPT = """你是语音助手路由器，只做模式判断。
根据用户输入，判断 mode：
- COMMAND: 需要执行设备/音乐/音量控制
- CHAT: 普通问答、闲聊、解释

必须只输出 JSON，不要输出其它内容。
输出格式：
{"mode":"COMMAND|CHAT","reason":"简短中文原因"}"""

private const val DEFAULT_COMMAND_PROMPT = """你是语音助手命令解析器。
把用户输入解析成结构化命令，仅输出 JSON，不要解释。

type 只能是：MUSIC, VOLUME, DEVICE, QUERY, UNKNOWN
action 参考：
- MUSIC: play, pause, resume, next, previous, stop
- VOLUME: set, up, down, mute
- DEVICE: on, off, toggle
- QUERY: ask
- UNKNOWN: null

字段说明：
- query: 播放搜索词或查询内容
- value: 音量值(0-100)或增减值

输出格式：
{"type":"MUSIC|VOLUME|DEVICE|QUERY|UNKNOWN","action":"...","query":"...","value":10}"""

// Default wake words in format "keyword:response"
private val DEFAULT_WAKE_WORDS = listOf(
    "小爱同学:我在",
    "你好问问:我在",
    "小艺小艺:我在",
    "小米小米:我在"
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val configDao: ConfigDao
) : SettingsRepository {

    override suspend fun getString(key: String, default: String): String {
        return configDao.get(key)?.value ?: default
    }

    override suspend fun setString(key: String, value: String) {
        configDao.set(ConfigEntity(key = key, value = value))
    }

    override fun getStringFlow(key: String, default: String): Flow<String> {
        return configDao.getFlow(key).map { it?.value ?: default }
    }

    // Jellyfin
    override suspend fun getJellyfinUrl(): String = getString(ConfigKeys.JELLYFIN_URL, "")
    override suspend fun setJellyfinUrl(url: String) = setString(ConfigKeys.JELLYFIN_URL, url)
    override suspend fun getJellyfinApiKey(): String = getString(ConfigKeys.JELLYFIN_API_KEY, "")
    override suspend fun setJellyfinApiKey(key: String) = setString(ConfigKeys.JELLYFIN_API_KEY, key)

    // DLNA
    override suspend fun getDLNADeviceIp(): String = getString(ConfigKeys.DLNA_DEVICE_IP, "")
    override suspend fun setDLNADeviceIp(ip: String) = setString(ConfigKeys.DLNA_DEVICE_IP, ip)
    override suspend fun getDLNADeviceName(): String = getString(ConfigKeys.DLNA_DEVICE_NAME, "")
    override suspend fun setDLNADeviceName(name: String) = setString(ConfigKeys.DLNA_DEVICE_NAME, name)
    override suspend fun getDLNADeviceUuid(): String = getString(ConfigKeys.DLNA_DEVICE_UUID, "")
    override suspend fun setDLNADeviceUuid(uuid: String) = setString(ConfigKeys.DLNA_DEVICE_UUID, uuid)

    // LLM
    override suspend fun getLLMBaseUrl(): String = getString(ConfigKeys.LLM_BASE_URL, "")
    override suspend fun setLLMBaseUrl(url: String) = setString(ConfigKeys.LLM_BASE_URL, url)
    override suspend fun getLLMApiKey(): String = getString(ConfigKeys.LLM_API_KEY, "")
    override suspend fun setLLMApiKey(key: String) = setString(ConfigKeys.LLM_API_KEY, key)
    override suspend fun getLLMModel(): String = getString(ConfigKeys.LLM_MODEL, "deepseek-chat")
    override suspend fun setLLMModel(model: String) = setString(ConfigKeys.LLM_MODEL, model)
    override suspend fun getLLMSystemPrompt(): String =
        getString(ConfigKeys.LLM_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT).ifBlank { DEFAULT_SYSTEM_PROMPT }
    override suspend fun setLLMSystemPrompt(prompt: String) = setString(ConfigKeys.LLM_SYSTEM_PROMPT, prompt)
    override suspend fun getLLMRouterPrompt(): String =
        getString(ConfigKeys.LLM_ROUTER_PROMPT, DEFAULT_ROUTER_PROMPT).ifBlank { DEFAULT_ROUTER_PROMPT }
    override suspend fun setLLMRouterPrompt(prompt: String) = setString(ConfigKeys.LLM_ROUTER_PROMPT, prompt)
    override suspend fun getLLMCommandPrompt(): String =
        getString(ConfigKeys.LLM_COMMAND_PROMPT, DEFAULT_COMMAND_PROMPT).ifBlank { DEFAULT_COMMAND_PROMPT }
    override suspend fun setLLMCommandPrompt(prompt: String) = setString(ConfigKeys.LLM_COMMAND_PROMPT, prompt)
    override suspend fun getLLMContextCount(): Int = getString(ConfigKeys.LLM_CONTEXT_COUNT, "5").toIntOrNull() ?: 5
    override suspend fun setLLMContextCount(count: Int) = setString(ConfigKeys.LLM_CONTEXT_COUNT, count.toString())

    // Voice Settings
    override suspend fun getWakeSensitivity(): Float = getString(ConfigKeys.WAKE_SENSITIVITY, "0.5").toFloatOrNull() ?: 0.5f
    override suspend fun setWakeSensitivity(sensitivity: Float) = setString(ConfigKeys.WAKE_SENSITIVITY, sensitivity.toString())

    override suspend fun getWakeWords(): List<String> {
        val stored = getString(ConfigKeys.WAKE_WORDS, "")
        if (stored.isEmpty()) return DEFAULT_WAKE_WORDS
        return stored.split("|").filter { it.isNotBlank() }
    }

    override suspend fun setWakeWords(words: List<String>) {
        setString(ConfigKeys.WAKE_WORDS, words.joinToString("|"))
    }

    override suspend fun getTtsSpeed(): Float = getString(ConfigKeys.TTS_SPEED, "1.0").toFloatOrNull() ?: 1.0f
    override suspend fun setTtsSpeed(speed: Float) = setString(ConfigKeys.TTS_SPEED, speed.toString())

    override suspend fun getTtsEnabled(): Boolean = getString(ConfigKeys.TTS_ENABLED, "true").toBooleanStrictOrNull() ?: true
    override suspend fun setTtsEnabled(enabled: Boolean) = setString(ConfigKeys.TTS_ENABLED, enabled.toString())
}

interface SettingsRepository {
    suspend fun getString(key: String, default: String = ""): String
    suspend fun setString(key: String, value: String)
    fun getStringFlow(key: String, default: String = ""): Flow<String>

    // Jellyfin
    suspend fun getJellyfinUrl(): String
    suspend fun setJellyfinUrl(url: String)
    suspend fun getJellyfinApiKey(): String
    suspend fun setJellyfinApiKey(key: String)

    // DLNA
    suspend fun getDLNADeviceIp(): String
    suspend fun setDLNADeviceIp(ip: String)
    suspend fun getDLNADeviceName(): String
    suspend fun setDLNADeviceName(name: String)
    suspend fun getDLNADeviceUuid(): String
    suspend fun setDLNADeviceUuid(uuid: String)

    // LLM
    suspend fun getLLMBaseUrl(): String
    suspend fun setLLMBaseUrl(url: String)
    suspend fun getLLMApiKey(): String
    suspend fun setLLMApiKey(key: String)
    suspend fun getLLMModel(): String
    suspend fun setLLMModel(model: String)
    suspend fun getLLMSystemPrompt(): String
    suspend fun setLLMSystemPrompt(prompt: String)
    suspend fun getLLMRouterPrompt(): String
    suspend fun setLLMRouterPrompt(prompt: String)
    suspend fun getLLMCommandPrompt(): String
    suspend fun setLLMCommandPrompt(prompt: String)
    suspend fun getLLMContextCount(): Int
    suspend fun setLLMContextCount(count: Int)

    // Voice Settings
    suspend fun getWakeSensitivity(): Float
    suspend fun setWakeSensitivity(sensitivity: Float)
    suspend fun getWakeWords(): List<String>
    suspend fun setWakeWords(words: List<String>)
    suspend fun getTtsSpeed(): Float
    suspend fun setTtsSpeed(speed: Float)
    suspend fun getTtsEnabled(): Boolean
    suspend fun setTtsEnabled(enabled: Boolean)
}
