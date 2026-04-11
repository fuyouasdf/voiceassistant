package com.voiceassistant.core.intent

import android.content.SharedPreferences
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.PlaylistRepository
import com.voiceassistant.domain.usecase.HandleChatUseCase
import com.voiceassistant.domain.usecase.HandleDeviceUseCase
import com.voiceassistant.domain.usecase.HandleMusicUseCase
import com.voiceassistant.domain.usecase.HandleQueryUseCase
import com.voiceassistant.domain.usecase.HandleVolumeUseCase
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Executes parsed intents by delegating to domain use cases.
 *
 * This class is the seam between core (parsing) and domain (business logic).
 * It handles local playback using MusicPlayer and Jellyfin session operations via use cases.
 *
 * Responsibilities:
 * - Manage local playback with MusicPlayer
 * - Delegate Jellyfin session operations to domain use cases
 * - Handle session ID management via SharedPreferences
 */
class IntentExecutor @Inject constructor(
    private val handleMusicUseCase: HandleMusicUseCase,
    private val handleVolumeUseCase: HandleVolumeUseCase,
    private val handleDeviceUseCase: HandleDeviceUseCase,
    private val handleQueryUseCase: HandleQueryUseCase,
    private val handleChatUseCase: HandleChatUseCase,
    private val musicRepository: MusicRepository?,
    private val sharedPreferences: SharedPreferences,
    private val musicPlayer: MusicPlayer,
    private val playlistRepository: PlaylistRepository?
) {
    companion object {
        private const val PREF_LAST_SESSION_ID = "jellyfin_selected_device_id"
        private const val LOCAL_DEVICE_SESSION_ID = "__local_device_session__"
    }

    // Play queue for next/previous functionality
    private val playQueue = mutableListOf<Song>()
    private var currentIndex: Int = -1

    /**
     * Execute the given intent and return response text.
     */
    suspend fun execute(intent: Intent): String {
        Timber.d("Executing intent: $intent")

        val sessionId = getSavedSessionId()

        return when (intent.type) {
            IntentType.MUSIC -> executeMusic(intent, sessionId)
            IntentType.VOLUME -> executeVolume(intent, sessionId)
            IntentType.DEVICE -> executeDevice(intent, sessionId)
            IntentType.QUERY -> handleQueryUseCase.execute(intent)
            IntentType.CHAT -> handleChatUseCase.execute(intent)
            IntentType.UNKNOWN -> "没听懂，请再说一遍"
        }
    }

    private suspend fun executeMusic(intent: Intent, sessionId: String?): String {
        if (sessionId == null) {
            return "请先在 Jellyfin 页面选择播放设备"
        }

        return when (intent.action) {
            "play" -> handlePlay(intent, sessionId)
            "pause" -> handlePause(sessionId)
            "resume" -> handleResume(sessionId)
            "next" -> handleNext(sessionId)
            "previous" -> handlePrevious(sessionId)
            "stop" -> handleStop(sessionId)
            else -> "音乐操作"
        }
    }

    private suspend fun handlePlay(intent: Intent, sessionId: String): String {
        val repo = musicRepository ?: return "音乐服务未配置"

        val query = intent.query ?: ""
        if (query.isEmpty()) {
            return handlePlayRandom(sessionId)
        }

        return repo.searchSongs(query).fold(
            onSuccess = { songs ->
                if (songs.isEmpty()) {
                    "没找到关于「$query」的歌曲"
                } else {
                    // Update play queue with search results
                    playQueue.clear()
                    playQueue.addAll(songs)
                    currentIndex = 0

                    val song = songs.first()
                    playSong(song, sessionId)
                }
            },
            onFailure = { "搜索歌曲失败，请稍后重试" }
        )
    }

    private suspend fun handlePlayRandom(sessionId: String): String {
        val playlistRepo = playlistRepository ?: return "播放列表服务未配置"

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

        return playSong(song, sessionId)
    }

    private suspend fun playSong(song: Song, sessionId: String): String {
        return if (isLocalSession(sessionId)) {
            val streamUrl = song.url ?: return "播放失败：无法获取本地播放地址"
            val item = MusicItem(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                streamUrl = streamUrl,
                coverUrl = song.coverUrl
            )
            musicPlayer.play(item)
            "好的，正在播放 ${song.title} - ${song.artist ?: "未知艺术家"}"
        } else {
            // Use HandleMusicUseCase for Jellyfin session playback
            handleMusicUseCase.playSong(song, sessionId)
        }
    }

    private suspend fun handlePause(sessionId: String): String {
        return if (isLocalSession(sessionId)) {
            musicPlayer.pause()
            "已暂停播放"
        } else {
            handleMusicUseCase.execute(
                Intent(IntentType.MUSIC, action = "pause"),
                sessionId
            )
        }
    }

    private suspend fun handleResume(sessionId: String): String {
        return if (isLocalSession(sessionId)) {
            musicPlayer.resume()
            "继续播放"
        } else {
            handleMusicUseCase.execute(
                Intent(IntentType.MUSIC, action = "resume"),
                sessionId
            )
        }
    }

    private suspend fun handleNext(sessionId: String): String {
        return if (isLocalSession(sessionId)) {
            musicPlayer.playNext()
            "正在播放下一首"
        } else {
            handleMusicUseCase.execute(
                Intent(IntentType.MUSIC, action = "next"),
                sessionId
            )
        }
    }

    private suspend fun handlePrevious(sessionId: String): String {
        return if (isLocalSession(sessionId)) {
            musicPlayer.playPrevious()
            "正在播放上一首"
        } else {
            handleMusicUseCase.execute(
                Intent(IntentType.MUSIC, action = "previous"),
                sessionId
            )
        }
    }

    private suspend fun handleStop(sessionId: String): String {
        return if (isLocalSession(sessionId)) {
            musicPlayer.stop()
            "已停止播放"
        } else {
            handleMusicUseCase.execute(
                Intent(IntentType.MUSIC, action = "stop"),
                sessionId
            )
        }
    }

    private suspend fun executeVolume(intent: Intent, sessionId: String?): String {
        return handleVolumeUseCase.execute(intent, sessionId, isLocalSession(sessionId ?: ""))
    }

    private suspend fun executeDevice(intent: Intent, sessionId: String?): String {
        if (sessionId == null) {
            return when (intent.action) {
                "on" -> "设备未连接，无法打开"
                "off" -> "设备未连接，无法关闭"
                "toggle" -> "设备未连接，无法切换状态"
                else -> "设备操作失败"
            }
        }

        if (isLocalSession(sessionId)) {
            return handleLocalDevice(intent)
        }

        return handleDeviceUseCase.execute(intent, sessionId)
    }

    private fun handleLocalDevice(intent: Intent): String {
        return when (intent.action) {
            "on" -> {
                musicPlayer.resume()
                "已继续本机播放"
            }
            "off" -> {
                musicPlayer.stop()
                "已停止本机播放"
            }
            "toggle" -> {
                if (musicPlayer.getState().isPlaying) {
                    musicPlayer.pause()
                    "已暂停本机播放"
                } else {
                    musicPlayer.resume()
                    "已继续本机播放"
                }
            }
            else -> "设备操作"
        }
    }

    private fun getSavedSessionId(): String? {
        return sharedPreferences.getString(PREF_LAST_SESSION_ID, null)?.takeIf { it.isNotBlank() }
    }

    private fun isLocalSession(sessionId: String): Boolean {
        return sessionId == LOCAL_DEVICE_SESSION_ID
    }
}
