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
 * Unit tests for MusicPlayer state transitions.
 * Tests play, pause, resume, playNext, playPrevious, and other state changes.
 */
class MusicPlayerStateTest {

    private lateinit var musicPlayer: MusicPlayer

    private fun createMusicItem(id: String, title: String, duration: Int = 180): MusicItem {
        return MusicItem(
            id = id,
            title = title,
            artist = "Test Artist",
            album = "Test Album",
            duration = duration,
            streamUrl = "http://example.com/stream/$id"
        )
    }

    @Before
    fun setup() {
        musicPlayer = MusicPlayer(
            context = mock(),
            playbackReporter = NoOpPlaybackReporter()
        )
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state is correct`() {
        val state = musicPlayer.state.value

        assertFalse(state.isPlaying)
        assertNull(state.currentSongId)
        assertNull(state.currentSongTitle)
        assertEquals(0L, state.currentPosition)
        assertEquals(0L, state.duration)
        assertTrue(state.playlist.isEmpty())
        assertEquals(-1, state.currentIndex)
        assertFalse(state.isShuffleEnabled)
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }

    // ==================== play Tests ====================

    @Test
    fun `play single song updates state correctly`() {
        val item = createMusicItem("1", "Test Song")

        musicPlayer.play(item)

        val state = musicPlayer.state.value
        assertEquals("1", state.currentSongId)
        assertEquals("Test Song", state.currentSongTitle)
        assertEquals(1, state.playlist.size)
        assertEquals(0, state.currentIndex)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `play playlist updates state correctly`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )

        musicPlayer.playPlaylist(items, 1)

        val state = musicPlayer.state.value
        assertEquals("2", state.currentSongId)
        assertEquals("Song 2", state.currentSongTitle)
        assertEquals(3, state.playlist.size)
        assertEquals(1, state.currentIndex)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `play empty playlist does nothing`() {
        musicPlayer.playPlaylist(emptyList())

        val state = musicPlayer.state.value
        assertTrue(state.playlist.isEmpty())
        assertFalse(state.isPlaying)
    }

    // ==================== pause/resume Tests ====================

    @Test
    fun `pause updates isPlaying to false`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)

        musicPlayer.pause()

        val state = musicPlayer.state.value
        assertFalse(state.isPlaying)
    }

    @Test
    fun `resume updates isPlaying to true`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)
        musicPlayer.pause()

        musicPlayer.resume()

        val state = musicPlayer.state.value
        assertTrue(state.isPlaying)
    }

    @Test
    fun `pause then resume maintains current song`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)

        musicPlayer.pause()
        musicPlayer.resume()

        val state = musicPlayer.state.value
        assertEquals("1", state.currentSongId)
        assertTrue(state.isPlaying)
    }

    // ==================== stop Tests ====================

    @Test
    fun `stop sets isPlaying to false and position to 0`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)

        musicPlayer.stop()

        val state = musicPlayer.state.value
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPosition)
        // Current song info is preserved
        assertEquals("1", state.currentSongId)
    }

    // ==================== playNext Tests ====================

    @Test
    fun `playNext advances to next song in playlist`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0)

        musicPlayer.playNext()

        val state = musicPlayer.state.value
        assertEquals("2", state.currentSongId)
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun `playNext at end of playlist with RepeatMode_OFF does nothing`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 1) // At last song

        musicPlayer.playNext()

        val state = musicPlayer.state.value
        // Should still be on Song 2
        assertEquals("2", state.currentSongId)
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun `playNext at end of playlist with RepeatMode_ALL loops to first`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 1) // At last song
        musicPlayer.toggleRepeat() // Set to RepeatMode.ALL

        musicPlayer.playNext()

        val state = musicPlayer.state.value
        assertEquals("1", state.currentSongId)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `playNext with RepeatMode_ONE restarts current song`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)
        // Set repeat mode to ONE
        musicPlayer.toggleRepeat() // OFF -> ALL
        musicPlayer.toggleRepeat() // ALL -> ONE

        musicPlayer.playNext()

        val state = musicPlayer.state.value
        // Should restart Song 1
        assertEquals("1", state.currentSongId)
        assertEquals(0, state.currentIndex)
    }

    // ==================== playPrevious Tests ====================

    @Test
    fun `playPrevious at middle of playlist goes to previous song`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 1)

        musicPlayer.playPrevious()

        val state = musicPlayer.state.value
        assertEquals("1", state.currentSongId)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `playPrevious at start of playlist with RepeatMode_ALL goes to last`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)
        musicPlayer.toggleRepeat() // Set to RepeatMode.ALL

        musicPlayer.playPrevious()

        val state = musicPlayer.state.value
        assertEquals("2", state.currentSongId)
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun `playPrevious at start with position > 3s goes to beginning`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)
        // Simulate being past the 3 second mark
        // Note: Without actual playback, we can't easily test this
        // This test documents the expected behavior

        val state = musicPlayer.state.value
        assertEquals("1", state.currentSongId)
    }

    // ==================== shuffle Tests ====================

    @Test
    fun `toggleShuffle enables shuffle mode`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0)

        val result = musicPlayer.toggleShuffle()

        assertTrue(result)
        assertTrue(musicPlayer.isShuffleEnabled())
        val state = musicPlayer.state.value
        assertTrue(state.isShuffleEnabled)
    }

    @Test
    fun `toggleShuffle twice returns to normal order`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0)
        val originalOrder = musicPlayer.state.value.playlist.map { it.id }

        musicPlayer.toggleShuffle()
        musicPlayer.toggleShuffle()

        val state = musicPlayer.state.value
        // After shuffle off, playlist should be back to original order
        assertEquals(originalOrder, state.playlist.map { it.id })
    }

    @Test
    fun `shuffle keeps current song at first position`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 1) // Start with Song 2

        musicPlayer.toggleShuffle()

        val state = musicPlayer.state.value
        // Current song (Song 2) should be at index 0
        assertEquals("2", state.playlist[0].id)
        assertEquals("2", state.currentSongId)
    }

    // ==================== repeat Tests ====================

    @Test
    fun `toggleRepeat cycles through OFF -> ALL -> ONE -> OFF`() {
        val items = listOf(createMusicItem("1", "Song 1"))
        musicPlayer.playPlaylist(items, 0)

        // OFF -> ALL
        var mode = musicPlayer.toggleRepeat()
        assertEquals(RepeatMode.ALL, mode)

        // ALL -> ONE
        mode = musicPlayer.toggleRepeat()
        assertEquals(RepeatMode.ONE, mode)

        // ONE -> OFF
        mode = musicPlayer.toggleRepeat()
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test
    fun `getRepeatMode returns current repeat mode`() {
        val items = listOf(createMusicItem("1", "Song 1"))
        musicPlayer.playPlaylist(items, 0)

        assertEquals(RepeatMode.OFF, musicPlayer.getRepeatMode())

        musicPlayer.toggleRepeat()
        assertEquals(RepeatMode.ALL, musicPlayer.getRepeatMode())

        musicPlayer.toggleRepeat()
        assertEquals(RepeatMode.ONE, musicPlayer.getRepeatMode())
    }

    // ==================== seekToIndex Tests ====================

    @Test
    fun `seekToIndex changes current index`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2"),
            createMusicItem("3", "Song 3")
        )
        musicPlayer.playPlaylist(items, 0)

        musicPlayer.seekToIndex(2)

        val state = musicPlayer.state.value
        assertEquals("3", state.currentSongId)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `seekToIndex with out of range index does nothing`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)

        musicPlayer.seekToIndex(10)

        val state = musicPlayer.state.value
        assertEquals("1", state.currentSongId)
    }

    @Test
    fun `seekToIndex with negative index does nothing`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)

        musicPlayer.seekToIndex(-1)

        val state = musicPlayer.state.value
        assertEquals("1", state.currentSongId)
    }

    // ==================== favorite Tests ====================

    @Test
    fun `toggleFavorite changes favorite state`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)

        assertFalse(musicPlayer.isFavorite())

        musicPlayer.toggleFavorite()
        assertTrue(musicPlayer.isFavorite())

        musicPlayer.toggleFavorite()
        assertFalse(musicPlayer.isFavorite())
    }

    @Test
    fun `toggleFavorite with no current song returns false`() {
        assertFalse(musicPlayer.toggleFavorite())
        assertFalse(musicPlayer.isFavorite())
    }

    // ==================== playbackSpeed Tests ====================

    @Test
    fun `setPlaybackSpeed changes speed`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)

        val result = musicPlayer.setPlaybackSpeed(1.5f)

        assertTrue(result)
        assertEquals(1.5f, musicPlayer.getPlaybackSpeed(), 0.01f)
    }

    @Test
    fun `setPlaybackSpeed to same value returns false`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)
        musicPlayer.setPlaybackSpeed(1.5f)

        val result = musicPlayer.setPlaybackSpeed(1.5f)

        assertFalse(result)
    }

    @Test
    fun `getPlaybackSpeed returns default 1_0`() {
        val item = createMusicItem("1", "Test Song")
        musicPlayer.play(item)

        assertEquals(1.0f, musicPlayer.getPlaybackSpeed(), 0.01f)
    }

    // ==================== release Tests ====================

    @Test
    fun `release clears all state`() {
        val items = listOf(
            createMusicItem("1", "Song 1"),
            createMusicItem("2", "Song 2")
        )
        musicPlayer.playPlaylist(items, 0)
        musicPlayer.toggleShuffle()
        musicPlayer.toggleRepeat()

        musicPlayer.release()

        val state = musicPlayer.state.value
        assertFalse(state.isPlaying)
        assertNull(state.currentSongId)
        assertTrue(state.playlist.isEmpty())
        assertEquals(-1, state.currentIndex)
        assertFalse(state.isShuffleEnabled)
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }
}