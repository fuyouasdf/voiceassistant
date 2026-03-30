package com.voiceassistant.core.intent

import android.content.SharedPreferences
import com.voiceassistant.domain.model.Intent as DomainIntent
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.model.IntentType as DomainIntentType
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.PlayerRepository
import com.voiceassistant.domain.repository.PlaylistRepository
import com.voiceassistant.domain.usecase.HandleChatUseCase
import kotlinx.coroutines.flow.first
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
 * @param musicRepository For music playback (includes playItem for Jellyfin Session API)
 * @param llmRepository For chat functionality
 * @param playerRepository For DLNA playback
 * @param sharedPreferences For storing last used session ID
 */
class IntentRouter @Inject constructor(
    private val musicRepository: MusicRepository?,
    private val llmRepository: LLMRepository?,
    private val playerRepository: PlayerRepository?,
    private val playlistRepository: PlaylistRepository?,
    private val sharedPreferences: SharedPreferences,
    private val handleChatUseCase: HandleChatUseCase
) {
    companion object {
        private const val PREF_LAST_SESSION_ID = "jellyfin_selected_device_id"
    }

    // Play queue for next/previous functionality
    private val playQueue = mutableListOf<Song>()
    private var currentIndex: Int = -1

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

        val player = playerRepository

        return when (intent.action) {
            "play" -> {
                val query = intent.query ?: ""
                if (query.isEmpty()) {
                    return playRandomFromPlaylist(musicRepo, player)
                }

                val result = musicRepo.searchSongs(query)
                result.fold(
                    onSuccess = { songs ->
                        if (songs.isEmpty()) {
                            "没找到关于「$query」的歌曲"
                        } else {
                            // Update play queue with search results
                            playQueue.clear()
                            playQueue.addAll(songs)
                            currentIndex = 0

                            val song = songs.first()
                            playSong(musicRepo, player, song)
                        }
                    },
                    onFailure = { "搜索歌曲失败，请稍后重试" }
                )
            }
            "pause" -> {
                if (player != null) {
                    val result = player.pause()
                    if (result.isSuccess) "已暂停播放" else "暂停失败"
                } else "已暂停播放"
            }
            "resume" -> {
                if (player != null) {
                    val result = player.resume()
                    if (result.isSuccess) "继续播放" else "继续播放失败"
                } else "继续播放"
            }
            "next" -> {
                if (playQueue.isEmpty()) {
                    "没有可播放的歌曲列表，请先选择要播放的歌曲"
                } else if (currentIndex >= playQueue.lastIndex) {
                    "已经是最后一首了"
                } else {
                    currentIndex++
                    val song = playQueue[currentIndex]
                    playSong(musicRepo, player, song)
                }
            }
            "previous" -> {
                if (playQueue.isEmpty()) {
                    "没有可播放的歌曲列表，请先选择要播放的歌曲"
                } else if (currentIndex <= 0) {
                    "已经是第一首了"
                } else {
                    currentIndex--
                    val song = playQueue[currentIndex]
                    playSong(musicRepo, player, song)
                }
            }
            "stop" -> {
                if (player != null) {
                    val result = player.stop()
                    if (result.isSuccess) "已停止播放" else "停止失败"
                } else "已停止播放"
            }
            else -> "音乐操作"
        }
    }

    /**
     * Play a song using the provided repositories
     */
    private suspend fun playSong(
        musicRepo: MusicRepository,
        player: PlayerRepository?,
        song: Song
    ): String {
        return try {
            val streamUrl = musicRepo.getStreamUrl(song.id)
            if (player != null) {
                val playResult = player.play(streamUrl, song.title, song.artist ?: "未知艺术家")
                if (playResult.isSuccess) {
                    "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
                } else {
                    val error = playResult.exceptionOrNull()?.message ?: "播放失败"
                    Timber.e("Play failed: $error")
                    "播放失败：$error"
                }
            } else {
                // No player available, just report success
                "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
            }
        } catch (e: Exception) {
            Timber.e(e, "playSong failed")
            "播放失败：${e.message ?: "未知错误"}"
        }
    }

    /**
     * Play a random song from the local playlist
     */
    private suspend fun playRandomFromPlaylist(
        musicRepo: MusicRepository?,
        player: PlayerRepository?
    ): String {
        if (musicRepo == null) {
            return "音乐服务未配置"
        }

        val playlistRepo = playlistRepository
        if (playlistRepo == null) {
            return "播放列表服务未配置"
        }

        // Get the first playlist
        val playlists = playlistRepo.getAllPlaylists().first()
        if (playlists.isEmpty()) {
            return "没有可用的播放列表"
        }
        val playlist = playlists.first()

        val songs = playlistRepo.getPlaylistSongs(playlist)
        if (songs.isEmpty()) {
            return "播放列表为空，请先添加歌曲"
        }

        val randomSong = songs.random()

        val song = Song(
            id = randomSong.songId,
            title = randomSong.title,
            artist = randomSong.artist,
            album = randomSong.album,
            duration = randomSong.duration,
            url = randomSong.streamUrl,
            coverUrl = randomSong.coverUrl
        )

        // Update play queue
        playQueue.clear()
        playQueue.add(song)
        currentIndex = 0

        // 优先使用 Jellyfin Session API 播放
        val sessionId = sharedPreferences.getString(PREF_LAST_SESSION_ID, null)
        if (sessionId != null) {
            Timber.d("playRandomFromPlaylist: trying Jellyfin Session API with sessionId=$sessionId")
            val sessionResult = musicRepo.playItem(sessionId, randomSong.songId)
            if (sessionResult.isSuccess) {
                Timber.d("playRandomFromPlaylist: Jellyfin Session API success")
                return "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
            } else {
                Timber.w("playRandomFromPlaylist: Jellyfin Session API failed: ${sessionResult.exceptionOrNull()?.message}")
            }
        } else {
            Timber.d("playRandomFromPlaylist: no saved session ID found")
        }

        // 回退到 DLNA 直接控制
        val streamUrl = if (randomSong.streamUrl.isNotEmpty()) {
            randomSong.streamUrl
        } else {
            Timber.d("playRandomFromPlaylist: streamUrl is empty, fetching from musicRepo")
            musicRepo.getStreamUrl(randomSong.songId)
        }

        if (streamUrl.isEmpty()) {
            Timber.e("playRandomFromPlaylist: failed to get streamUrl for songId=${randomSong.songId}")
            return "获取播放链接失败，请检查网络或歌曲是否可用"
        }

        return playSongWithUrl(musicRepo, player, song, streamUrl)
    }

    /**
     * Play a song with a pre-fetched stream URL
     */
    private suspend fun playSongWithUrl(
        musicRepo: MusicRepository,
        player: PlayerRepository?,
        song: Song,
        streamUrl: String
    ): String {
        return try {
            if (player != null) {
                val playResult = player.play(streamUrl, song.title, song.artist ?: "未知艺术家")
                if (playResult.isSuccess) {
                    "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
                } else {
                    val error = playResult.exceptionOrNull()?.message ?: "播放失败"
                    Timber.e("Play failed: $error")
                    "播放失败：$error"
                }
            } else {
                "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
            }
        } catch (e: Exception) {
            Timber.e(e, "playSongWithUrl failed")
            "播放失败：${e.message ?: "未知错误"}"
        }
    }

    private suspend fun handleVolume(intent: Intent): String {
        val player = playerRepository

        // Get current volume if player is available, otherwise default to 50
        val currentVolume = 50 // We'll use intent.value for absolute, or default increment

        return when (intent.action) {
            "set" -> {
                val volume = intent.value ?: 50
                if (player != null && player.isPlayerAvailable()) {
                    val result = player.setVolume(volume)
                    if (result.isSuccess) {
                        "音量已调到 $volume%"
                    } else {
                        "音量调节失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                } else {
                    "音量调到 $volume% (播放器未连接)"
                }
            }
            "up" -> {
                val increment = intent.value ?: 10
                val newVolume = (currentVolume + increment).coerceAtMost(100)
                if (player != null && player.isPlayerAvailable()) {
                    val result = player.setVolume(newVolume)
                    if (result.isSuccess) {
                        "音量已增加 $increment%，当前音量 $newVolume%"
                    } else {
                        "音量调节失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                } else {
                    "音量增加 $increment%"
                }
            }
            "down" -> {
                val decrement = intent.value ?: 10
                val newVolume = (currentVolume - decrement).coerceAtLeast(0)
                if (player != null && player.isPlayerAvailable()) {
                    val result = player.setVolume(newVolume)
                    if (result.isSuccess) {
                        "音量已减少 $decrement%，当前音量 $newVolume%"
                    } else {
                        "音量调节失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                } else {
                    "音量减少 $decrement%"
                }
            }
            "mute" -> {
                if (player != null && player.isPlayerAvailable()) {
                    val result = player.setVolume(0)
                    if (result.isSuccess) {
                        "已静音"
                    } else {
                        "静音失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                } else {
                    "已静音"
                }
            }
            else -> "音量操作"
        }
    }

    private suspend fun handleDevice(intent: Intent): String {
        val player = playerRepository

        if (player == null || !player.isPlayerAvailable()) {
            return when (intent.action) {
                "on" -> "设备未连接，无法打开"
                "off" -> "设备未连接，无法关闭"
                "toggle" -> "设备未连接，无法切换状态"
                else -> "设备操作失败"
            }
        }

        return when (intent.action) {
            "on" -> {
                // "打开设备" - try to resume playback
                val result = player.resume()
                if (result.isSuccess) {
                    "已打开设备并继续播放"
                } else {
                    "打开设备失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                }
            }
            "off" -> {
                // "关闭设备" - stop playback
                val result = player.stop()
                if (result.isSuccess) {
                    "已关闭设备"
                } else {
                    "关闭设备失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                }
            }
            "toggle" -> {
                // "切换设备状态" - toggle between play and pause
                // Check if currently playing by trying to pause
                val pauseResult = player.pause()
                if (pauseResult.isSuccess) {
                    "已暂停播放"
                } else {
                    // Not playing, try to resume
                    val resumeResult = player.resume()
                    if (resumeResult.isSuccess) {
                        "已继续播放"
                    } else {
                        "切换状态失败：${resumeResult.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                }
            }
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
        return llm.chat(intent.query ?: "").fold(
            onSuccess = { it },
            onFailure = { "查询失败，请稍后重试" }
        )
    }

    private suspend fun handleChat(intent: Intent): String {
        val domainIntent = DomainIntent(
            type = DomainIntentType.valueOf(intent.type.name),
            action = intent.action,
            query = intent.query,
            value = intent.value,
            song = intent.song
        )
        return handleChatUseCase.execute(domainIntent)
    }
}