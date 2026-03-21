package com.voiceassistant.domain.model

/**
 * Domain model for a song
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int
)