package com.voiceassistant.core.music

import com.google.gson.JsonParser
import timber.log.Timber

/**
 * Lyric line data
 */
data class LyricLine(
    val text: String,
    val startMs: Long
)

/**
 * Lyrics parsing result
 */
data class LyricsResult(
    val lines: List<LyricLine>,
    val rawText: String
)

/**
 * Lyrics parser for JSON and LRC formats.
 * Extracted from JellyfinClient for testability.
 */
object LyricsParser {

    /**
     * Parse lyrics from raw string (auto-detect format)
     */
    fun parse(raw: String): LyricsResult {
        return if (raw.startsWith("{") || raw.startsWith("[")) {
            parseJsonLyrics(raw) ?: parseLrcLyrics(raw)
        } else {
            parseLrcLyrics(raw)
        }
    }

    /**
     * Parse JSON format lyrics
     */
    fun parseJsonLyrics(raw: String): LyricsResult? {
        return try {
            val root = JsonParser.parseString(raw)
            val lyricsArray = when {
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    when {
                        obj.has("Lyrics") -> obj.getAsJsonArray("Lyrics")
                        obj.has("lyrics") -> obj.getAsJsonArray("lyrics")
                        else -> null
                    }
                }
                root.isJsonArray -> root.asJsonArray
                else -> null
            } ?: return null

            val entries = lyricsArray.mapNotNull { element ->
                val obj = element.asJsonObject
                val text = when {
                    obj.has("Text") -> obj.get("Text")?.asString
                    obj.has("text") -> obj.get("text")?.asString
                    else -> null
                }?.trim().orEmpty()

                if (text.isBlank()) {
                    return@mapNotNull null
                }

                val rawStart = when {
                    obj.has("Start") -> obj.get("Start")?.asLong
                    obj.has("start") -> obj.get("start")?.asLong
                    obj.has("StartMs") -> obj.get("StartMs")?.asLong
                    obj.has("startMs") -> obj.get("startMs")?.asLong
                    else -> null
                } ?: 0L

                LyricLine(
                    text = text,
                    startMs = normalizeLyricTime(rawStart)
                )
            }.sortedBy { it.startMs }

            LyricsResult(
                lines = entries,
                rawText = raw
            )
        } catch (e: Exception) {
            Timber.w(e, "parseJsonLyrics failed")
            null
        }
    }

    /**
     * Parse LRC format lyrics
     */
    fun parseLrcLyrics(raw: String): LyricsResult {
        val regex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)")
        val lines = raw.lineSequence().mapNotNull { line ->
            val match = regex.find(line.trim()) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3].padEnd(3, '0').takeIf { it.isNotBlank() }?.toLongOrNull() ?: 0L
            val text = match.groupValues[4].trim()
            if (text.isBlank()) return@mapNotNull null
            LyricLine(
                text = text,
                startMs = minutes * 60_000 + seconds * 1_000 + fraction
            )
        }.toList()

        return LyricsResult(
            lines = lines,
            rawText = raw
        )
    }

    /**
     * Normalize lyric time to milliseconds
     */
    fun normalizeLyricTime(value: Long): Long {
        return when {
            value >= 10_000_000L -> value / 10_000L
            value >= 1_000L -> value
            else -> value * 1000L
        }
    }
}