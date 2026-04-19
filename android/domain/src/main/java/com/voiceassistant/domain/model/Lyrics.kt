package com.voiceassistant.domain.model

/**
 * Domain model for a lyric line
 */
data class LyricLine(
    val text: String,
    val startMs: Long
)

/**
 * Domain model for lyrics result
 */
data class LyricsResult(
    val lines: List<LyricLine>,
    val rawText: String
)