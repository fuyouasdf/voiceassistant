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