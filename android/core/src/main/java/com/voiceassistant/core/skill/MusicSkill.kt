package com.voiceassistant.core.skill

import com.voiceassistant.core.intent.ChineseMatcher
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.PlaylistRepository
import com.voiceassistant.domain.skill.Skill
import com.voiceassistant.domain.skill.SkillContext
import com.voiceassistant.domain.skill.SkillResult
import com.voiceassistant.domain.usecase.HandleMusicUseCase
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for handling music playback commands.
 *
 * Handles:
 * - Play specific song or random music
 * - Pause, resume, stop
 * - Next, previous track
 */
@Singleton
class MusicSkill @Inject constructor(
    private val musicPlayer: MusicPlayer
) : Skill {

    override val name: String = "music"

    override val keywords: List<String> = listOf(
        "播放", "暂停", "继续", "停止", "下一首", "上一首", "来一首", "放歌", "听歌", "切歌"
    )

    override val priority: Int = 10

    // Play queue for next/previous functionality
    private val playQueue = mutableListOf<Song>()
    private var currentIndex: Int = -1

    override suspend fun handle(input: String, rawInput: String, context: SkillContext): SkillResult {
        val action = parseAction(input) ?: return SkillResult.NotHandled

        return when (action) {
            "stop" -> handleStop(context)
            "pause" -> handlePause(context)
            "resume" -> handleResume(context)
            "next" -> handleNext(context)
            "previous" -> handlePrevious(context)
            "play" -> handlePlay(input, context)
            else -> SkillResult.NotHandled
        }
    }

    private fun parseAction(input: String): String? {
        return when {
            input.contains("停止") -> "stop"
            input.contains("暂停") -> "pause"
            input.contains("继续") -> "resume"
            input.contains("下一首") || input.contains("换一首") || input.contains("切歌") -> "next"
            input.contains("上一首") -> "previous"
            input.contains("播放") || input.contains("来一首") || input.contains("放歌") -> "play"
            else -> null
        }
    }

    private fun parseArtistAndSong(text: String): Triple<String?, String?, String> {
        val cleanText = text.replace(Regex("(播放|来一首|放一首|放歌|听|我想听)"), "").trim()

        val dePattern = Regex("^(.+?)的([^的]+)$")
        val match = dePattern.find(cleanText)

        if (match != null) {
            val potentialArtist = match.groupValues[1].trim()
            val potentialSong = match.groupValues[2].trim()

            val invalidArtists = listOf("音乐", "歌曲", "这首", "那首", "歌", "专辑", "歌手")
            if (potentialArtist !in invalidArtists && potentialSong.isNotEmpty() && potentialArtist.isNotEmpty()) {
                Timber.d("MusicSkill: artist='$potentialArtist', song='$potentialSong'")
                return Triple(potentialArtist, potentialSong, potentialSong)
            }
        }

        Timber.d("MusicSkill: no artist found, song='$cleanText'")
        return Triple(null, cleanText, cleanText)
    }

    private suspend fun handleStop(context: SkillContext): SkillResult {
        val sessionId = context.savedSessionId
        return if (sessionId != null && context.isLocalSession(sessionId)) {
            musicPlayer.stop()
            SkillResult.Success("已停止播放")
        } else if (sessionId != null) {
            val musicUseCase = context.handleMusicUseCase ?: return SkillResult.Success("音乐服务未配置")
            val result = musicUseCase.execute(Intent(IntentType.MUSIC, action = "stop"), sessionId)
            SkillResult.Success(result)
        } else {
            musicPlayer.stop()
            SkillResult.Success("已停止播放")
        }
    }

    private suspend fun handlePause(context: SkillContext): SkillResult {
        val sessionId = context.savedSessionId
        return if (sessionId != null && context.isLocalSession(sessionId)) {
            musicPlayer.pause()
            SkillResult.Success("已暂停播放")
        } else if (sessionId != null) {
            val musicUseCase = context.handleMusicUseCase ?: return SkillResult.Success("音乐服务未配置")
            val result = musicUseCase.execute(Intent(IntentType.MUSIC, action = "pause"), sessionId)
            SkillResult.Success(result)
        } else {
            musicPlayer.pause()
            SkillResult.Success("已暂停播放")
        }
    }

    private suspend fun handleResume(context: SkillContext): SkillResult {
        val sessionId = context.savedSessionId
        return if (sessionId != null && context.isLocalSession(sessionId)) {
            musicPlayer.resume()
            SkillResult.Success("继续播放")
        } else if (sessionId != null) {
            val musicUseCase = context.handleMusicUseCase ?: return SkillResult.Success("音乐服务未配置")
            val result = musicUseCase.execute(Intent(IntentType.MUSIC, action = "resume"), sessionId)
            SkillResult.Success(result)
        } else {
            musicPlayer.resume()
            SkillResult.Success("继续播放")
        }
    }

    private suspend fun handleNext(context: SkillContext): SkillResult {
        val sessionId = context.savedSessionId
        return if (sessionId != null && context.isLocalSession(sessionId)) {
            musicPlayer.playNext()
            SkillResult.Success("正在播放下一首")
        } else if (sessionId != null) {
            val musicUseCase = context.handleMusicUseCase ?: return SkillResult.Success("音乐服务未配置")
            val result = musicUseCase.execute(Intent(IntentType.MUSIC, action = "next"), sessionId)
            SkillResult.Success(result)
        } else {
            musicPlayer.playNext()
            SkillResult.Success("正在播放下一首")
        }
    }

    private suspend fun handlePrevious(context: SkillContext): SkillResult {
        val sessionId = context.savedSessionId
        return if (sessionId != null && context.isLocalSession(sessionId)) {
            musicPlayer.playPrevious()
            SkillResult.Success("正在播放上一首")
        } else if (sessionId != null) {
            val musicUseCase = context.handleMusicUseCase ?: return SkillResult.Success("音乐服务未配置")
            val result = musicUseCase.execute(Intent(IntentType.MUSIC, action = "previous"), sessionId)
            SkillResult.Success(result)
        } else {
            musicPlayer.playPrevious()
            SkillResult.Success("正在播放上一首")
        }
    }

    private suspend fun handlePlay(input: String, context: SkillContext): SkillResult {
        val sessionId = context.savedSessionId
        if (sessionId == null) {
            return SkillResult.Success("请先在 Jellyfin 页面选择播放设备")
        }

        val repo = context.musicRepository
        if (repo == null) {
            return SkillResult.Success("音乐服务未配置，请在设置中配置 Jellyfin")
        }

        val (artist, songName, fallbackQuery) = parseArtistAndSong(input)

        if (songName.isNullOrEmpty()) {
            return handlePlayRandom(context, sessionId)
        }

        return searchAndPlay(repo, songName!!, artist, fallbackQuery, sessionId, context)
    }

    private suspend fun searchAndPlay(
        repo: MusicRepository,
        songName: String,
        artist: String?,
        fallbackQuery: String,
        sessionId: String,
        context: SkillContext
    ): SkillResult {
        var songs = repo.searchSongs(songName).fold(
            onSuccess = { it },
            onFailure = { emptyList() }
        )

        Timber.d("MusicSkill: songName='$songName', artist='$artist', found=${songs.size}")

        if (!artist.isNullOrEmpty()) {
            songs = filterByArtist(songs, artist)
            if (songs.isEmpty()) {
                songs = repo.searchSongs(songName).fold(
                    onSuccess = { it },
                    onFailure = { emptyList() }
                )
            }
        }

        if (songs.isEmpty() && fallbackQuery != songName) {
            Timber.d("MusicSkill: no results for '$songName', trying fallback '$fallbackQuery'")
            songs = repo.searchSongs(fallbackQuery).fold(
                onSuccess = { it },
                onFailure = { emptyList() }
            )
            if (songs.isNotEmpty() && !artist.isNullOrEmpty()) {
                songs = filterByArtist(songs, artist)
            }
        }

        if (songs.isEmpty()) {
            Timber.d("MusicSkill: no exact results, trying fuzzy match")
            val fuzzyMatch = fuzzySearchAndFilter(repo, songName, artist)
            if (fuzzyMatch != null) {
                songs = listOf(fuzzyMatch)
                Timber.d("MusicSkill: fuzzy match found: ${fuzzyMatch.title}")
            }
        }

        if (songs.isEmpty()) {
            return SkillResult.Success("没找到「${if (artist.isNullOrEmpty()) songName else "$artist - $songName"}」的歌曲")
        }

        playQueue.clear()
        playQueue.addAll(songs)
        currentIndex = 0

        val song = songs.first()
        val playResult = playSong(song, sessionId, context)

        return SkillResult.Success("现在播放：${song.title} - ${song.artist ?: "未知艺术家"}")
    }

    private fun filterByArtist(songs: List<Song>, artist: String): List<Song> {
        val matchedSongs = songs.filter { song ->
            val songArtist = song.artist ?: ""
            val songAlbumArtist = song.album ?: ""
            songArtist.contains(artist, ignoreCase = true) ||
                songAlbumArtist.contains(artist, ignoreCase = true)
        }
        Timber.d("MusicSkill: artist='$artist', matched=${matchedSongs.size} of ${songs.size}")
        return matchedSongs
    }

    private suspend fun fuzzySearchAndFilter(repo: MusicRepository, songName: String, artist: String?): Song? {
        Timber.d("MusicSkill fuzzySearchAndFilter: songName='$songName', artist='$artist'")

        val searchQueries = buildList {
            add(ChineseMatcher.toPinyinInitial(songName))
            if (songName.length >= 2) {
                add(songName.take(2))
            }
            if (songName.length >= 1) {
                add(songName.first().toString())
            }
        }.filter { it.isNotEmpty() && it.length >= 1 }

        Timber.d("MusicSkill fuzzySearchAndFilter: searchQueries=$searchQueries")

        val allCandidates = mutableSetOf<Song>()
        for (query in searchQueries) {
            if (allCandidates.isNotEmpty()) break

            val results = repo.searchSongs(query).fold(
                onSuccess = { it },
                onFailure = { emptyList() }
            )
            allCandidates.addAll(results)
            Timber.d("MusicSkill fuzzySearchAndFilter: query='$query', found=${results.size}, total=${allCandidates.size}")
        }

        if (allCandidates.isEmpty()) {
            Timber.d("MusicSkill fuzzySearchAndFilter: no candidates found")
            return null
        }

        val candidatesList = allCandidates.toList()
        val scoredCandidates = candidatesList.map { song ->
            val titleScore = ChineseMatcher.fuzzyScore(songName, song.title)
            val artistScore = if (!artist.isNullOrEmpty()) {
                val songArtist = song.artist ?: ""
                ChineseMatcher.fuzzyScore(artist, songArtist)
            } else 0.0
            val combinedScore = titleScore * 0.7 + artistScore * 0.3
            Triple(song, titleScore, combinedScore)
        }.filter { it.second >= 0.3 }

        if (scoredCandidates.isEmpty()) {
            Timber.d("MusicSkill fuzzySearchAndFilter: no candidates passed threshold")
            return null
        }

        val bestMatch = scoredCandidates.maxByOrNull { it.third }
        Timber.d("MusicSkill fuzzySearchAndFilter: best match=${bestMatch?.first?.title}, score=${bestMatch?.third}")
        return bestMatch?.first
    }

    private suspend fun handlePlayRandom(context: SkillContext, sessionId: String): SkillResult {
        val playlistRepo = context.playlistRepository ?: return SkillResult.Success("播放列表服务未配置")

        val playlists = playlistRepo.getAllPlaylists().first()
        if (playlists.isEmpty()) {
            return SkillResult.Success("没有可用的播放列表")
        }
        val playlist = playlists.first()

        val songs = playlistRepo.getPlaylistSongs(playlist)
        if (songs.isEmpty()) {
            return SkillResult.Success("播放列表为空，请先添加歌曲")
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

        playQueue.clear()
        playQueue.add(song)
        currentIndex = 0

        val playResult = playSong(song, sessionId, context)
        return SkillResult.Success(playResult)
    }

    private suspend fun playSong(song: Song, sessionId: String, context: SkillContext): String {
        return if (context.isLocalSession(sessionId)) {
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
            "现在播放：${song.title} - ${song.artist ?: "未知艺术家"}"
        } else {
            val musicUseCase = context.handleMusicUseCase ?: return "音乐服务未配置"
            musicUseCase.playSong(song, sessionId)
        }
    }
}