package com.voiceassistant.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for LyricsParser.
 * Tests JSON lyrics and LRC lyrics parsing.
 */
class LyricsParserTest {

    // ==================== JSON Lyrics Tests ====================

    @Test
    fun `parseJsonLyrics - with Lyrics array key`() {
        val json = """
            {
                "Lyrics": [
                    {"Text": "第一行歌词", "Start": 0},
                    {"Text": "第二行歌词", "Start": 5000},
                    {"Text": "第三行歌词", "Start": 10000}
                ]
            }
        """.trimIndent()

        val result = LyricsParser.parseJsonLyrics(json)

        assertNotNull(result)
        assertEquals(3, result.lines.size)
        assertEquals("第一行歌词", result.lines[0].text)
        assertEquals(0L, result.lines[0].startMs)
        assertEquals("第二行歌词", result.lines[1].text)
        assertEquals(5000L, result.lines[1].startMs)
        assertEquals("第三行歌词", result.lines[2].text)
        assertEquals(10000L, result.lines[2].startMs)
    }

    @Test
    fun `parseJsonLyrics - with lowercase lyrics array key`() {
        val json = """
            {
                "lyrics": [
                    {"text": "第一行歌词", "start": 0},
                    {"text": "第二行歌词", "start": 5000}
                ]
            }
        """.trimIndent()

        val result = LyricsParser.parseJsonLyrics(json)

        assertNotNull(result)
        assertEquals(2, result.lines.size)
        assertEquals("第一行歌词", result.lines[0].text)
        assertEquals("第二行歌词", result.lines[1].text)
    }

    @Test
    fun `parseJsonLyrics - with StartMs key`() {
        val json = """
            {
                "Lyrics": [
                    {"Text": "开始", "StartMs": 0},
                    {"Text": "中间", "StartMs": 3000},
                    {"Text": "结束", "StartMs": 6000}
                ]
            }
        """.trimIndent()

        val result = LyricsParser.parseJsonLyrics(json)

        assertNotNull(result)
        assertEquals(3, result.lines.size)
        assertEquals(0L, result.lines[0].startMs)
        assertEquals(3000L, result.lines[1].startMs)
        assertEquals(6000L, result.lines[2].startMs)
    }

    @Test
    fun `parseJsonLyrics - with ticks format ( Start >= 10_000_000L )`() {
        val json = """
            {
                "Lyrics": [
                    {"Text": "第一行", "Start": 50000000},
                    {"Text": "第二行", "Start": 55000000}
                ]
            }
        """.trimIndent()

        val result = LyricsParser.parseJsonLyrics(json)

        assertNotNull(result)
        assertEquals(2, result.lines.size)
        // 50000000 / 10000 = 5000ms
        assertEquals(5000L, result.lines[0].startMs)
        // 55000000 / 10000 = 5500ms
        assertEquals(5500L, result.lines[1].startMs)
    }

    @Test
    fun `parseJsonLyrics - empty array returns null`() {
        val json = """{"Lyrics": []}"""

        val result = LyricsParser.parseJsonLyrics(json)

        assertNull(result)
    }

    @Test
    fun `parseJsonLyrics - missing Lyrics key returns null`() {
        val json = """{"songs": []}"""

        val result = LyricsParser.parseJsonLyrics(json)

        assertNull(result)
    }

    @Test
    fun `parseJsonLyrics - blank text is filtered out`() {
        val json = """
            {
                "Lyrics": [
                    {"Text": "有效歌词", "Start": 0},
                    {"Text": "   ", "Start": 1000},
                    {"Text": "", "Start": 2000},
                    {"Text": "另一个有效", "Start": 3000}
                ]
            }
        """.trimIndent()

        val result = LyricsParser.parseJsonLyrics(json)

        assertNotNull(result)
        assertEquals(2, result.lines.size)
        assertEquals("有效歌词", result.lines[0].text)
        assertEquals("另一个有效", result.lines[1].text)
    }

    @Test
    fun `parseJsonLyrics - results are sorted by startMs`() {
        val json = """
            {
                "Lyrics": [
                    {"Text": "最后", "Start": 30000},
                    {"Text": "第一", "Start": 0},
                    {"Text": "中间", "Start": 15000}
                ]
            }
        """.trimIndent()

        val result = LyricsParser.parseJsonLyrics(json)

        assertNotNull(result)
        assertEquals(3, result.lines.size)
        assertEquals("第一", result.lines[0].text)
        assertEquals("中间", result.lines[1].text)
        assertEquals("最后", result.lines[2].text)
    }

    @Test
    fun `parseJsonLyrics - direct array format`() {
        val json = """
            [
                {"Text": "第一行", "Start": 0},
                {"Text": "第二行", "Start": 5000}
            ]
        """.trimIndent()

        val result = LyricsParser.parseJsonLyrics(json)

        assertNotNull(result)
        assertEquals(2, result.lines.size)
    }

    @Test
    fun `parseJsonLyrics - invalid json returns null`() {
        val invalidJson = """{ invalid json }"""

        val result = LyricsParser.parseJsonLyrics(invalidJson)

        assertNull(result)
    }

    // ==================== LRC Lyrics Tests ====================

    @Test
    fun `parseLrcLyrics - basic format`() {
        val lrc = """
            [00:00]第一行歌词
            [00:05]第二行歌词
            [00:10]第三行歌词
        """.trimIndent()

        val result = LyricsParser.parseLrcLyrics(lrc)

        assertNotNull(result)
        assertEquals(3, result.lines.size)
        assertEquals("第一行歌词", result.lines[0].text)
        assertEquals(0L, result.lines[0].startMs)
        assertEquals("第二行歌词", result.lines[1].text)
        assertEquals(5000L, result.lines[1].startMs)
        assertEquals("第三行歌词", result.lines[2].text)
        assertEquals(10000L, result.lines[2].startMs)
    }

    @Test
    fun `parseLrcLyrics - with milliseconds`() {
        val lrc = """
            [00:00.000]第一行
            [00:01.500]第二行
            [00:02.123]第三行
        """.trimIndent()

        val result = LyricsParser.parseLrcLyrics(lrc)

        assertNotNull(result)
        assertEquals(3, result.lines.size)
        assertEquals(0L, result.lines[0].startMs)
        assertEquals(1500L, result.lines[1].startMs)
        assertEquals(2123L, result.lines[2].startMs)
    }

    @Test
    fun `parseLrcLyrics - single digit milliseconds`() {
        val lrc = """
            [00:00.0]第一行
            [00:01.5]第二行
            [00:02.12]第三行
        """.trimIndent()

        val result = LyricsParser.parseLrcLyrics(lrc)

        assertNotNull(result)
        assertEquals(3, result.lines.size)
        assertEquals(0L, result.lines[0].startMs)
        assertEquals(1500L, result.lines[1].startMs)
        assertEquals(2120L, result.lines[2].startMs)
    }

    @Test
    fun `parseLrcLyrics - multi-digit minutes`() {
        val lrc = """
            [01:30:00]一小时三十分钟
            [02:15:30]二小时十五分三十秒
        """.trimIndent()

        val result = LyricsParser.parseLrcLyrics(lrc)

        assertNotNull(result)
        assertEquals(2, result.lines.size)
        // 1*60*60*1000 + 30*60*1000 = 5400000ms
        assertEquals(5400000L, result.lines[0].startMs)
        // 2*60*60*1000 + 15*60*1000 + 30*1000 = 8130000ms
        assertEquals(8130000L, result.lines[1].startMs)
    }

    @Test
    fun `parseLrcLyrics - blank text lines are filtered`() {
        val lrc = """
            [00:00]有效歌词
            [00:05]
            [00:10]   </d>
        """.trimIndent()

        val result = LyricsParser.parseLrcLyrics(lrc)

        assertNotNull(result)
        assertEquals(1, result.lines.size)
        assertEquals("有效歌词", result.lines[0].text)
    }

    @Test
    fun `parseLrcLyrics - metadata lines are ignored`() {
        val lrc = """
            [ti:歌曲标题]
            [ar:艺术家]
            [al:专辑名]
            [00:00]第一行歌词
        """.trimIndent()

        val result = LyricsParser.parseLrcLyrics(lrc)

        assertNotNull(result)
        // Metadata lines don't match the time pattern so they're filtered
        assertEquals(1, result.lines.size)
        assertEquals("第一行歌词", result.lines[0].text)
    }

    @Test
    fun `parseLrcLyrics - empty input returns empty list`() {
        val result = LyricsParser.parseLrcLyrics("")

        assertNotNull(result)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun `parseLrcLyrics - whitespace only returns empty list`() {
        val result = LyricsParser.parseLrcLyrics("   \n\t\n  ")

        assertNotNull(result)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun `parseLrcLyrics - preserves rawText`() {
        val lrc = "[00:00]测试"
        val result = LyricsParser.parseLrcLyrics(lrc)

        assertEquals(lrc, result.rawText)
    }

    // ==================== Auto-detect Tests ====================

    @Test
    fun `parse - detects JSON format starting with brace`() {
        val json = """{"Lyrics": [{"Text": "测试", "Start": 0}]}"""

        val result = LyricsParser.parse(json)

        assertNotNull(result)
        assertEquals("测试", result.lines[0].text)
    }

    @Test
    fun `parse - detects JSON format starting with bracket`() {
        val json = """[{"Text": "测试", "Start": 0}]"""

        val result = LyricsParser.parse(json)

        assertNotNull(result)
        assertEquals("测试", result.lines[0].text)
    }

    @Test
    fun `parse - falls back to LRC for plain text`() {
        val lrc = "[00:00]测试歌词"

        val result = LyricsParser.parse(lrc)

        assertNotNull(result)
        assertFalse(result.lines.isEmpty())
        assertEquals("测试歌词", result.lines[0].text)
    }

    @Test
    fun `parse - invalid JSON falls back to LRC`() {
        val text = "[00:00]这是LRC格式"

        val result = LyricsParser.parse(text)

        assertNotNull(result)
        assertEquals("这是LRC格式", result.lines[0].text)
    }

    // ==================== normalizeLyricTime Tests ====================

    @Test
    fun `normalizeLyricTime - ticks format (>= 10_000_000L)`() {
        assertEquals(1000L, LyricsParser.normalizeLyricTime(10_000_000L))
        assertEquals(5000L, LyricsParser.normalizeLyricTime(50_000_000L))
        assertEquals(0L, LyricsParser.normalizeLyricTime(0L))
    }

    @Test
    fun `normalizeLyricTime - milliseconds format (>= 1000L)`() {
        assertEquals(1000L, LyricsParser.normalizeLyricTime(1000L))
        assertEquals(5000L, LyricsParser.normalizeLyricTime(5000L))
    }

    @Test
    fun `normalizeLyricTime - seconds format (< 1000L)`() {
        assertEquals(0L, LyricsParser.normalizeLyricTime(0L))
        assertEquals(1000L, LyricsParser.normalizeLyricTime(1L))
        assertEquals(5000L, LyricsParser.normalizeLyricTime(5L))
    }
}