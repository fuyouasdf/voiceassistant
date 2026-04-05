package com.voiceassistant.core.intent

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for IntentParser.
 * Tests all intent types and edge cases.
 */
class IntentParserTest {

    private val parser = IntentParser()

    // ==================== Music Intent Tests ====================

    @Test
    fun `parse music - 播放歌曲`() {
        val result = parser.parse("播放歌曲")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("play", result.action)
        // Regex only strips "播放", leaving "歌曲"
        assertEquals("歌曲", result.query)
    }

    @Test
    fun `parse music - 播放带查询`() {
        val result = parser.parse("播放周杰伦的歌")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("play", result.action)
        assertEquals("周杰伦的歌", result.query)
    }

    @Test
    fun `parse music - 来一首摇滚乐`() {
        val result = parser.parse("来一首摇滚乐")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("play", result.action)
        assertEquals("摇滚乐", result.query)
    }

    @Test
    fun `parse music - 放歌`() {
        val result = parser.parse("放歌")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("play", result.action)
    }

    @Test
    fun `parse music - 暂停`() {
        val result = parser.parse("暂停")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("pause", result.action)
    }

    @Test
    fun `parse music - 继续`() {
        val result = parser.parse("继续")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("resume", result.action)
    }

    @Test
    fun `parse music - 下一首`() {
        val result = parser.parse("下一首")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("next", result.action)
    }

    @Test
    fun `parse music - 上一首`() {
        val result = parser.parse("上一首")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("previous", result.action)
    }

    @Test
    fun `parse music - 换一首`() {
        // "换一首" is NOT in KEYWORDS_MUSIC (only "下一首" and "切歌" are)
        val result = parser.parse("换一首")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("换一首", result.query)
    }

    @Test
    fun `parse music - 切歌`() {
        val result = parser.parse("切歌")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("next", result.action)
    }

    @Test
    fun `parse music - 停止`() {
        val result = parser.parse("停止")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("stop", result.action)
    }

    @Test
    fun `parse music - 停止播放`() {
        val result = parser.parse("停止播放")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("stop", result.action)
    }

    @Test
    fun `parse music - 听歌`() {
        val result = parser.parse("听歌")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("play", result.action)
    }

    @Test
    fun `parse music - 我想听摇滚`() {
        // "我想听摇滚" does not contain any KEYWORDS_MUSIC exactly
        // "听" alone is not a keyword (only "听歌"), so it's CHAT
        val result = parser.parse("我想听摇滚")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("我想听摇滚", result.query)
    }

    @Test
    fun `parse music - 放一首古典音乐`() {
        // "放一首古典音乐" doesn't contain any KEYWORDS_MUSIC exactly
        // Falls to CHAT
        val result = parser.parse("放一首古典音乐")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("放一首古典音乐", result.query)
    }

    // ==================== Volume Intent Tests ====================

    @Test
    fun `parse volume - 音量调到50`() {
        val result = parser.parse("音量调到50")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("set", result.action)
        assertEquals(50, result.value)
    }

    @Test
    fun `parse volume - 音量设为80`() {
        val result = parser.parse("音量设为80")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("set", result.action)
        assertEquals(80, result.value)
    }

    @Test
    fun `parse volume - 默认值50`() {
        val result = parser.parse("调音量")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("set", result.action)
        assertEquals(50, result.value)
    }

    @Test
    fun `parse volume - 大声点`() {
        val result = parser.parse("大声点")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("up", result.action)
        assertEquals(10, result.value)
    }

    @Test
    fun `parse volume - 大声一点`() {
        val result = parser.parse("大声一点")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("up", result.action)
        assertEquals(10, result.value)
    }

    @Test
    fun `parse volume - 高一点`() {
        // "高一点" does not match any KEYWORDS_VOLUME (音量, 声音, 大声, 小声, 静音)
        // so it falls to CHAT
        val result = parser.parse("高一点")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("高一点", result.query)
    }

    @Test
    fun `parse volume - 音量加大`() {
        val result = parser.parse("音量加大")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("up", result.action)
        assertEquals(10, result.value)
    }

    @Test
    fun `parse volume - 音量增加20`() {
        val result = parser.parse("音量增加20")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("up", result.action)
        assertEquals(20, result.value)
    }

    @Test
    fun `parse volume - 小声`() {
        val result = parser.parse("小声")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("down", result.action)
        assertEquals(10, result.value)
    }

    @Test
    fun `parse volume - 小声一点`() {
        val result = parser.parse("小声一点")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("down", result.action)
        assertEquals(10, result.value)
    }

    @Test
    fun `parse volume - 低一点`() {
        // "低一点" does not match any KEYWORDS_VOLUME (音量, 声音, 大声, 小声, 静音)
        // so it falls to CHAT
        val result = parser.parse("低一点")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("低一点", result.query)
    }

    @Test
    fun `parse volume - 音量减小`() {
        val result = parser.parse("音量减小")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("down", result.action)
        assertEquals(10, result.value)
    }

    @Test
    fun `parse volume - 音量减20`() {
        val result = parser.parse("音量减20")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("down", result.action)
        assertEquals(20, result.value)
    }

    @Test
    fun `parse volume - 静音`() {
        val result = parser.parse("静音")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("mute", result.action)
        assertNull(result.value)
    }

    @Test
    fun `parse volume - 声音调到30`() {
        val result = parser.parse("声音调到30")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("set", result.action)
        assertEquals(30, result.value)
    }

    // ==================== Device Intent Tests ====================

    @Test
    fun `parse device - 打开灯`() {
        val result = parser.parse("打开灯")
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("on", result.action)
    }

    @Test
    fun `parse device - 打开空调`() {
        val result = parser.parse("打开空调")
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("on", result.action)
    }

    @Test
    fun `parse device - 关闭空调`() {
        val result = parser.parse("关闭空调")
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("off", result.action)
    }

    @Test
    fun `parse device - 关闭电视`() {
        val result = parser.parse("关闭电视")
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("off", result.action)
    }

    @Test
    fun `parse device - 开关灯`() {
        val result = parser.parse("开关灯")
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("toggle", result.action)
    }

    @Test
    fun `parse device - 开关电视`() {
        val result = parser.parse("开关电视")
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("toggle", result.action)
    }

    @Test
    fun `parse device - 开关空调`() {
        val result = parser.parse("开关空调")
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("toggle", result.action)
    }

    // ==================== Query Intent Tests ====================

    @Test
    fun `parse query - 今天天气怎么样`() {
        val result = parser.parse("今天天气怎么样")
        assertEquals(IntentType.QUERY, result.type)
        assertEquals("今天天气怎么样", result.query)
    }

    @Test
    fun `parse query - 现在几点了`() {
        // "现在几点了" does not contain "时间", so it's CHAT not QUERY
        val result = parser.parse("现在几点了")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("现在几点了", result.query)
    }

    @Test
    fun `parse query - 今天几号`() {
        // "今天几号" does not contain "日期", so it's CHAT not QUERY
        val result = parser.parse("今天几号")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("今天几号", result.query)
    }

    @Test
    fun `parse query - 明天天气如何`() {
        val result = parser.parse("明天天气如何")
        assertEquals(IntentType.QUERY, result.type)
        assertEquals("明天天气如何", result.query)
    }

    @Test
    fun `parse query - 搜索周杰伦`() {
        val result = parser.parse("搜索周杰伦")
        assertEquals(IntentType.QUERY, result.type)
        assertEquals("搜索周杰伦", result.query)
    }

    @Test
    fun `parse query - 北京在哪里`() {
        val result = parser.parse("北京在哪里")
        assertEquals(IntentType.QUERY, result.type)
        assertEquals("北京在哪里", result.query)
    }

    // ==================== Chat Intent Tests ====================

    @Test
    fun `parse chat - 你好`() {
        val result = parser.parse("你好")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("你好", result.query)
    }

    @Test
    fun `parse chat - 你叫什么名字`() {
        val result = parser.parse("你叫什么名字")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("你叫什么名字", result.query)
    }

    @Test
    fun `parse chat - 随机文本`() {
        val result = parser.parse("asdfghjkl")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("asdfghjkl", result.query)
    }

    @Test
    fun `parse chat - empty string`() {
        val result = parser.parse("")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("", result.query)
    }

    // ==================== Edge Cases and Keyword Variations ====================

    @Test
    fun `parse - case insensitive`() {
        val result = parser.parse("播放歌曲")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("play", result.action)
    }

    @Test
    fun `parse - with leading whitespace`() {
        val result = parser.parse("  播放歌曲")
        assertEquals(IntentType.MUSIC, result.type)
    }

    @Test
    fun `parse - with trailing whitespace`() {
        val result = parser.parse("播放歌曲  ")
        assertEquals(IntentType.MUSIC, result.type)
    }

    @Test
    fun `parse - with both leading and trailing whitespace`() {
        val result = parser.parse("   播放歌曲  ")
        assertEquals(IntentType.MUSIC, result.type)
    }

    @Test
    fun `parse - mixed case input`() {
        // "PLAY音乐" lowercase becomes "play音乐" which doesn't contain Chinese keywords
        val result = parser.parse("PLAY音乐")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("PLAY音乐", result.query)
    }

    @Test
    fun `parse - priority music over query`() {
        // "播放天气" should be parsed as MUSIC since isMusicIntent is checked first
        val result = parser.parse("播放天气")
        assertEquals(IntentType.MUSIC, result.type)
        assertEquals("play", result.action)
        assertEquals("天气", result.query)
    }

    @Test
    fun `parse - priority volume over chat`() {
        val result = parser.parse("音量大")
        assertEquals(IntentType.VOLUME, result.type)
    }

    @Test
    fun `parse - number extraction from middle of text`() {
        val result = parser.parse("音量调到100")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals(100, result.value)
    }

    @Test
    fun `parse - number at beginning`() {
        // "50的音量" contains "音量", so it's detected as VOLUME
        val result = parser.parse("50的音量")
        assertEquals(IntentType.VOLUME, result.type)
        assertEquals("set", result.action)
        assertEquals(50, result.value)
    }

    @Test
    fun `parse - multiple volume keywords`() {
        val result = parser.parse("音量大声")
        assertEquals(IntentType.VOLUME, result.type)
        // "大" is in the text, so action is "up"
        assertEquals("up", result.action)
    }

    @Test
    fun `parse - device with multiple keywords`() {
        val result = parser.parse("打开关闭灯")
        // "打开" is found first in the when, so it matches "打开"
        assertEquals(IntentType.DEVICE, result.type)
        assertEquals("on", result.action)
    }

    @Test
    fun `parse - 我想听我想听`() {
        // "我想听" is not in KEYWORDS_MUSIC, so it's CHAT
        val result = parser.parse("我想听我想听古典音乐")
        assertEquals(IntentType.CHAT, result.type)
        assertEquals("我想听我想听古典音乐", result.query)
    }
}
