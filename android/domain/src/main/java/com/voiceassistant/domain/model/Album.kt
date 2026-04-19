package com.voiceassistant.domain.model

/**
 * Domain model for an album
 */
data class Album(
    val id: String,
    val name: String,
    val artist: String?,
    val imageTag: String? = null
)