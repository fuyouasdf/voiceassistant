package com.voiceassistant.data.repository

import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.JellyfinSong
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.MusicRepository
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

    override suspend fun getStreamUrl(songId: String): String {
        return jellyfinClient.getStreamUrl(songId)
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
}
