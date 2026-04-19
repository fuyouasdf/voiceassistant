package com.voiceassistant.data.repository

import com.voiceassistant.data.remote.JellyfinAlbum
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.JellyfinSong
import com.voiceassistant.data.remote.LyricLine as RemoteLyricLine
import com.voiceassistant.data.remote.LyricsResult as RemoteLyricsResult
import com.voiceassistant.data.remote.StreamInfo as RemoteStreamInfo
import com.voiceassistant.domain.model.Album
import com.voiceassistant.domain.model.LyricLine
import com.voiceassistant.domain.model.LyricsResult
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class MusicRepositoryImpl(
    private val jellyfinClient: JellyfinClient
) : MusicRepository {

    override suspend fun testConnection(): Result<Boolean> {
        return jellyfinClient.testConnection()
    }

    override suspend fun searchSongs(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val remoteSongs = jellyfinClient.searchSongs(query)
            val songs = remoteSongs.map { it.toDomainModel() }
            Result.success(songs)
        } catch (e: Exception) {
            Timber.e(e, "Search failed")
            Result.failure(e)
        }
    }

    override suspend fun searchAlbums(query: String): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val remoteAlbums = jellyfinClient.getAlbums(searchTerm = query)
            val albums = remoteAlbums.map { it.toDomainModel() }
            Result.success(albums)
        } catch (e: Exception) {
            Timber.e(e, "searchAlbums failed")
            Result.failure(e)
        }
    }

    override suspend fun getAlbums(): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val remoteAlbums = jellyfinClient.getAlbums()
            val albums = remoteAlbums.map { it.toDomainModel() }
            Result.success(albums)
        } catch (e: Exception) {
            Timber.e(e, "getAlbums failed")
            Result.failure(e)
        }
    }

    override suspend fun getAlbumSongs(albumId: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val remoteSongs = jellyfinClient.getItems(albumId)
            val songs = remoteSongs.map { it.toDomainModel() }
            Result.success(songs)
        } catch (e: Exception) {
            Timber.e(e, "getAlbumSongs failed for albumId: $albumId")
            Result.failure(e)
        }
    }

    override suspend fun getItem(itemId: String): Result<Song?> = withContext(Dispatchers.IO) {
        try {
            val song = jellyfinClient.getItem(itemId)
            Result.success(song?.toDomainModel())
        } catch (e: Exception) {
            Timber.e(e, "getItem failed for itemId: $itemId")
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(itemId: String): Result<LyricsResult?> = withContext(Dispatchers.IO) {
        try {
            val lyrics = jellyfinClient.getLyrics(itemId)
            Result.success(lyrics?.toDomainModel())
        } catch (e: Exception) {
            Timber.e(e, "getLyrics failed for itemId: $itemId")
            Result.failure(e)
        }
    }

    override suspend fun getStreamInfo(songId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        try {
            val remoteInfo = jellyfinClient.getStreamInfo(songId)
            Result.success(remoteInfo.toDomainModel())
        } catch (e: Exception) {
            Timber.e(e, "getStreamInfo failed for songId: $songId")
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(songId: String): Result<String> {
        return try {
            val url = jellyfinClient.getStreamUrl(songId)
            if (url.isEmpty()) {
                Result.failure(IllegalStateException("Failed to get stream URL for song: $songId"))
            } else {
                Result.success(url)
            }
        } catch (e: Exception) {
            Timber.e(e, "getStreamUrl failed for songId: $songId")
            Result.failure(e)
        }
    }

    override suspend fun refreshStreamUrl(songId: String): Result<String> {
        return try {
            val url = jellyfinClient.refreshStreamUrl(songId)
            if (url.isEmpty()) {
                Result.failure(IllegalStateException("Failed to refresh stream URL for song: $songId"))
            } else {
                Result.success(url)
            }
        } catch (e: Exception) {
            Timber.e(e, "refreshStreamUrl failed for songId: $songId")
            Result.failure(e)
        }
    }

    override suspend fun playItem(sessionId: String, itemId: String): Result<Boolean> {
        return jellyfinClient.playItem(sessionId, itemId)
    }

    override suspend fun pause(sessionId: String): Result<Boolean> {
        return jellyfinClient.pause(sessionId)
    }

    override suspend fun unpause(sessionId: String): Result<Boolean> {
        return jellyfinClient.unpause(sessionId)
    }

    override suspend fun stop(sessionId: String): Result<Boolean> {
        return jellyfinClient.stop(sessionId)
    }

    override suspend fun nextTrack(sessionId: String): Result<Boolean> {
        return jellyfinClient.nextTrack(sessionId)
    }

    override suspend fun previousTrack(sessionId: String): Result<Boolean> {
        return jellyfinClient.previousTrack(sessionId)
    }

    override suspend fun setVolume(sessionId: String, volume: Int): Result<Boolean> {
        return jellyfinClient.setVolume(sessionId, volume)
    }

    private fun JellyfinSong.toDomainModel(): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration
        )
    }

    private fun JellyfinAlbum.toDomainModel(): Album {
        return Album(
            id = id,
            name = name,
            artist = artist,
            imageTag = imageTag
        )
    }

    private fun RemoteLyricsResult.toDomainModel(): LyricsResult {
        return LyricsResult(
            lines = lines.map { LyricLine(text = it.text, startMs = it.startMs) },
            rawText = rawText
        )
    }

    private fun RemoteStreamInfo.toDomainModel(): StreamInfo {
        return StreamInfo(
            url = url,
            playSessionId = playSessionId,
            mediaSourceId = mediaSourceId,
            playMethod = playMethod?.name,
            container = container,
            isTranscoding = isTranscoding
        )
    }
}
