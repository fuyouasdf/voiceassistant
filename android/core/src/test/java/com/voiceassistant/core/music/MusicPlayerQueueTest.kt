package com.voiceassistant.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for MusicPlayer queue operations.
 * Tests moveQueueItem and removeFromQueue logic.
 */
class MusicPlayerQueueTest {

    private lateinit var musicPlayer: MusicPlayer

    private fun createMusicItem(id: String, title: String): MusicItem {
        return MusicItem(
            id = id,
            title = title,
            artist = "Test Artist",
            album = "Test Album",
            duration = 180,
            streamUrl = "http://example.com/stream/$id"
        )
    }

    @Before
    fun setup() {
        // Create MusicPlayer with minimal dependencies
        musicPlayer = MusicPlayer(
            context = mock(),
            playbackReporter = NoOpPlaybackReporter()
        )
    }

    // ==================== moveQueueItem Tests ====================

    @Test
    fun `moveQueueItem - same from and to returns true without changes`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0)

        val result = musicPlayer.moveQueueItem(1, 1)

        assertTrue(result)
        // State should remain unchanged
        val state = musicPlayer.state.value
        assertEquals(3, state.playlist.size)
    }

    @Test
    fun `moveQueueItem - out of range indices return false`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)

        assertFalse(musicPlayer.moveQueueItem(-1, 0))
        assertFalse(musicPlayer.moveQueueItem(0, 5))
        assertFalse(musicPlayer.moveQueueItem(10, 0))
    }

    @Test
    fun `moveQueueItem - move item forward in playlist`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0)

        // Move song 1 (index 0) to position 2 (between song 2 and song 3)
        val result = musicPlayer.moveQueueItem(0, 2)

        assertTrue(result)
        val state = musicPlayer.state.value
        assertEquals(3, state.playlist.size)
        // Order should be: Song 2, Song 3, Song 1
        assertEquals("2", state.playlist[0].id)
        assertEquals("3", state.playlist[1].id)
        assertEquals("1", state.playlist[2].id)
    }

    @Test
    fun `moveQueueItem - move item backward in playlist`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0)

        // Move song 3 (index 2) to position 0 (before song 1)
        val result = musicPlayer.moveQueueItem(2, 0)

        assertTrue(result)
        val state = musicPlayer.state.value
        // Order should be: Song 3, Song 1, Song 2
        assertEquals("3", state.playlist[0].id)
        assertEquals("1", state.playlist[1].id)
        assertEquals("2", state.playlist[2].id)
    }

    @Test
    fun `moveQueueItem - current playing song stays playing after reorder`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 1) // Start playing Song 2

        // Move Song 1 (index 0) to the end
        val result = musicPlayer.moveQueueItem(0, 2)

        assertTrue(result)
        val state = musicPlayer.state.value
        // Song 2 should still be the current song
        assertEquals("2", state.currentSongId)
        // Current index should be updated to follow Song 2
        assertEquals("2", state.playlist[state.currentIndex].id)
    }

    // ==================== removeFromQueue Tests ====================

    @Test
    fun `removeFromQueue - out of range index returns false`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)

        assertFalse(musicPlayer.removeFromQueue(-1))
        assertFalse(musicPlayer.removeFromQueue(10))
    }

    @Test
    fun `removeFromQueue - remove non-current song`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0) // Playing Song 1

        val result = musicPlayer.removeFromQueue(1) // Remove Song 2

        assertTrue(result)
        val state = musicPlayer.state.value
        assertEquals(2, state.playlist.size)
        assertEquals("1", state.playlist[0].id)
        assertEquals("3", state.playlist[1].id)
        // Should still be playing Song 1
        assertEquals("1", state.currentSongId)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `removeFromQueue - remove current song switches to next`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0) // Playing Song 1

        val result = musicPlayer.removeFromQueue(0) // Remove current Song 1

        assertTrue(result)
        val state = musicPlayer.state.value
        assertEquals(2, state.playlist.size)
        // Should now be playing Song 2 (which was at index 1, now at index 0)
        assertEquals("2", state.currentSongId)
        assertEquals("2", state.playlist[state.currentIndex].id)
    }

    @Test
    fun `removeFromQueue - remove last song when playing first`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0) // Playing Song 1

        val result = musicPlayer.removeFromQueue(1) // Remove Song 2

        assertTrue(result)
        val state = musicPlayer.state.value
        assertEquals(1, state.playlist.size)
        assertEquals("1", state.playlist[0].id)
        // Should still be playing Song 1 at index 0
        assertEquals("1", state.currentSongId)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `removeFromQueue - remove current last song switches to previous`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 1) // Playing Song 2 (last)

        val result = musicPlayer.removeFromQueue(1) // Remove current Song 2

        assertTrue(result)
        val state = musicPlayer.state.value
        assertEquals(1, state.playlist.size)
        // Should switch to Song 1
        assertEquals("1", state.currentSongId)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `removeFromQueue - empty queue stops playback`() {
        val items = listOf(
            createMusicItem("1", "Song 1")
        )
        musicPlayer.playPlaylist(items, 0)

        val result = musicPlayer.removeFromQueue(0)

        assertTrue(result)
        val state = musicPlayer.state.value
        assertTrue(state.playlist.isEmpty())
        assertNull(state.currentSongId)
        assertEquals(-1, state.currentIndex)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `removeFromQueue - remove all remaining songs`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)

        musicPlayer.removeFromQueue(0) // Remove Song 1
        val result = musicPlayer.removeFromQueue(0) // Remove Song 2 (now at index 0)

        assertTrue(result)
        val state = musicPlayer.state.value
        assertTrue(state.playlist.isEmpty())
        assertNull(state.currentSongId)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `removeFromQueue - remove song in middle updates indices correctly`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3"),
            createMusicItem("4", "Song 4")
        )
        musicPlayer.playPlaylist(items, 0) // Playing Song 1

        musicPlayer.removeFromQueue(1) // Remove Song 2

        val state = musicPlayer.state.value
        // Original indices: 0=Song1, 1=Song2, 2=Song3, 3=Song4
        // After removing index 1: 0=Song1, 1=Song3, 2=Song4
        assertEquals("1", state.playlist[0].id)
        assertEquals("3", state.playlist[1].id)
        assertEquals("4", state.playlist[2].id)
        // Current index should shift down since we removed something before current
        assertEquals(0, state.currentIndex)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `moveQueueItem - empty playlist returns false`() {
        assertFalse(musicPlayer.moveQueueItem(0, 1))
    }

    @Test
    fun `removeFromQueue - empty playlist returns false`() {
        assertFalse(musicPlayer.removeFromQueue(0))
    }

    @Test
    fun `moveQueueItem - single item playlist returns true (no change)`() {
        val items = listOf(createMusicItem("1", "Song 1"))
        musicPlayer.playPlaylist(items, 0)

        val result = musicPlayer.moveQueueItem(0, 0)

        assertTrue(result)
        val state = musicPlayer.state.value
        assertEquals(1, state.playlist.size)
        assertEquals("1", state.playlist[0].id)
    }

    @Test
    fun `removeFromQueue - remove from single item playlist empties queue`() {
        val items = listOf(createMusicItem("1", "Song 1"))
        musicPlayer.playPlaylist(items, 0)

        val result = musicPlayer.removeFromQueue(0)

        assertTrue(result)
        val state = musicPlayer.state.value
        assertTrue(state.playlist.isEmpty())
    }
}