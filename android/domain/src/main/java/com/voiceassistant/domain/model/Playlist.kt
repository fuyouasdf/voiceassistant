package com.voiceassistant.domain.model

/**
 * Domain model for a playlist (stores raw song data as JSON string)
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: String = "",      // Comma-separated song IDs (legacy)
    val songData: String = "",     // JSON format song data
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Domain model for a song in a playlist
 */
data class PlaylistSong(
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int,
    val streamUrl: String,
    val coverUrl: String?
)
