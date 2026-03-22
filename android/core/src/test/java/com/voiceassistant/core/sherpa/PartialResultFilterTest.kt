package com.voiceassistant.core.sherpa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PartialResultFilter.
 * Tests the partial result filtering logic to ensure callbacks only trigger
 * when recognition results actually grow.
 */
class PartialResultFilterTest {

    private lateinit var filter: PartialResultFilter

    @Before
    fun setup() {
        filter = PartialResultFilter()
    }

    // ==================== Growth Detection Tests ====================

    @Test
    fun `shouldNotify - empty text should not notify`() {
        assertFalse(filter.shouldNotify(""))
    }

    @Test
    fun `shouldNotify - first non-empty text should notify`() {
        assertTrue(filter.shouldNotify("你"))
    }

    @Test
    fun `shouldNotify - growing text should notify`() {
        assertTrue(filter.shouldNotify("你"))
        assertTrue(filter.shouldNotify("你好"))
        assertTrue(filter.shouldNotify("你好啊"))
    }

    @Test
    fun `shouldNotify - same length text should not notify`() {
        assertTrue(filter.shouldNotify("你好"))
        // Same length, different text - should not notify
        assertFalse(filter.shouldNotify("你们"))
    }

    @Test
    fun `shouldNotify - shorter text should not notify`() {
        assertTrue(filter.shouldNotify("你好啊"))
        // Shorter text - should not notify
        assertFalse(filter.shouldNotify("你"))
    }

    @Test
    fun `shouldNotify - regression should not notify`() {
        // Simulate ASR returning same result multiple times
        filter.reset()
        assertTrue(filter.shouldNotify("你好啊")) // first time - should notify
        assertFalse(filter.shouldNotify("你好啊")) // same - should not notify
        assertFalse(filter.shouldNotify("你好")) // shorter - should not notify
        assertFalse(filter.shouldNotify("你")) // shorter - should not notify
    }

    // ==================== Reset Tests ====================

    @Test
    fun `reset - should allow new growth cycle`() {
        assertTrue(filter.shouldNotify("你好"))
        assertTrue(filter.shouldNotify("你好啊"))
        assertFalse(filter.shouldNotify("你好啊"))

        filter.reset()

        // After reset, new growth should notify again
        assertTrue(filter.shouldNotify("再见"))
    }

    @Test
    fun `reset - empty text after reset should not notify`() {
        assertTrue(filter.shouldNotify("你好"))

        filter.reset()

        assertFalse(filter.shouldNotify(""))
    }

    // ==================== Real-world Scenario Tests ====================

    @Test
    fun `real scenario - gradual growth like real ASR`() {
        // Simulate real ASR returning: "" -> "你" -> "你好" -> "你好" -> "你好" -> "你好啊"
        assertFalse(filter.shouldNotify("")) // empty
        assertTrue(filter.shouldNotify("你")) // growth
        assertTrue(filter.shouldNotify("你好")) // growth
        assertFalse(filter.shouldNotify("你好")) // same - no notify
        assertFalse(filter.shouldNotify("你好")) // same - no notify
        assertTrue(filter.shouldNotify("你好啊")) // growth
        assertFalse(filter.shouldNotify("你好啊")) // same - no notify
    }

    @Test
    fun `real scenario - repeated same result should not spam`() {
        // ASR often returns the same partial result multiple times
        assertTrue(filter.shouldNotify("播放"))

        // Same result repeated 5 times - should only notify once
        assertFalse(filter.shouldNotify("播放"))
        assertFalse(filter.shouldNotify("播放"))
        assertFalse(filter.shouldNotify("播放"))
        assertFalse(filter.shouldNotify("播放"))
    }

    @Test
    fun `real scenario - text shrinks then grows`() {
        // Once text shrinks, it cannot trigger notify until reset
        // because lastText stores the maximum length encountered
        filter.reset()
        assertTrue(filter.shouldNotify("今天天气")) // length 5
        assertTrue(filter.shouldNotify("今天天气怎么样")) // length 7 - growth
        assertFalse(filter.shouldNotify("今天天气怎么")) // length 6 - shrinkage, no notify
        assertFalse(filter.shouldNotify("今天天气")) // length 5 - shrinkage, no notify
        // Cannot grow back because 7 > 5, so lastText stays at "今天天气怎么样"
        assertFalse(filter.shouldNotify("今天天气怎么样")) // length 7, same as lastText
    }
}
