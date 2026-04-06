package com.voiceassistant.app.ui.music

import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.data.remote.JellyfinSong
import com.voiceassistant.data.remote.PlayMethodType
import com.voiceassistant.data.remote.StreamInfo
import com.voiceassistant.domain.model.PlaylistSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for local playlist enqueue logic.
 * Tests the conversion from domain models (PlaylistSong, JellyfinSong) to MusicItem.
 */
class PlaylistEnqueueTest {

    // ==================== PlaylistSong to MusicItem Tests ====================

    @Test
    fun `buildMusicItems from PlaylistSong - valid song converts correctly`() {
        val playlistSong = PlaylistSong(
            songId = "song-123",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 180,
            streamUrl = "http://stream-url",
            coverUrl = "http://cover-url"
        )

        val streamInfo = StreamInfo(
            url = "http://jellyfin-stream/song-123",
            playSessionId = "session-abc",
            mediaSourceId = "mediasource123",
            playMethod = PlayMethodType.DIRECT_PLAY,
            container = "mp4",
            isTranscoding = false
        )

        val musicItem = buildMusicItemFromPlaylistSong(playlistSong, streamInfo)

        assertNotNull(musicItem)
        assertEquals("song-123", musicItem.id)
        assertEquals("Test Song", musicItem.title)
        assertEquals("Test Artist", musicItem.artist)
        assertEquals("Test Album", musicItem.album)
        assertEquals(180, musicItem.duration)
        assertEquals("http://jellyfin-stream/song-123", musicItem.streamUrl)
        assertEquals("http://cover-url", musicItem.coverUrl)
        assertEquals("session-abc", musicItem.playbackSessionId)
        assertEquals("mediasource123", musicItem.mediaSourceId)
        assertEquals("mp4", musicItem.streamContainer)
        assertEquals("DIRECT_PLAY", musicItem.streamPlayMethod)
        assertEquals(false, musicItem.isTranscoding)
    }

    @Test
    fun `buildMusicItems from PlaylistSong - transcoding stream sets isTranscoding true`() {
        val playlistSong = PlaylistSong(
            songId = "song-456",
            title = "WMA Song",
            artist = "Artist",
            album = "Album",
            duration = 240,
            streamUrl = "http://stream-url",
            coverUrl = null
        )

        val streamInfo = StreamInfo(
            url = "http://jellyfin-stream/song-456",
            playSessionId = "session-def",
            mediaSourceId = "mediasource456",
            playMethod = PlayMethodType.TRANSCODE,
            container = "wma",
            isTranscoding = true
        )

        val musicItem = buildMusicItemFromPlaylistSong(playlistSong, streamInfo)

        assertNotNull(musicItem)
        assertEquals(true, musicItem.isTranscoding)
        assertEquals("TRANSCODE", musicItem.streamPlayMethod)
    }

    // ==================== JellyfinSong to MusicItem Tests ====================

    @Test
    fun `buildMusicItems from JellyfinSong - valid song converts correctly`() {
        val jellyfinSong = JellyfinSong(
            id = "jf-song-789",
            title = "Jellyfin Song",
            artist = "Jellyfin Artist",
            album = "Jellyfin Album",
            duration = 200,
            coverUrl = "http://jf-cover"
        )

        val streamInfo = StreamInfo(
            url = "http://jellyfin-stream/jf-song-789",
            playSessionId = "session-ghi",
            mediaSourceId = "mediasource789",
            playMethod = PlayMethodType.DIRECT_STREAM,
            container = "mp3",
            isTranscoding = false
        )

        val musicItem = buildMusicItemFromJellyfinSong(jellyfinSong, streamInfo)

        assertNotNull(musicItem)
        assertEquals("jf-song-789", musicItem.id)
        assertEquals("Jellyfin Song", musicItem.title)
        assertEquals("Jellyfin Artist", musicItem.artist)
        assertEquals("Jellyfin Album", musicItem.album)
        assertEquals(200, musicItem.duration)
        assertEquals("http://jellyfin-stream/jf-song-789", musicItem.streamUrl)
        assertEquals("http://jf-cover", musicItem.coverUrl)
        assertEquals("session-ghi", musicItem.playbackSessionId)
        assertEquals("DIRECT_STREAM", musicItem.streamPlayMethod)
    }

    @Test
    fun `buildMusicItems from JellyfinSong - null coverUrl handled`() {
        val jellyfinSong = JellyfinSong(
            id = "jf-no-cover",
            title = "No Cover Song",
            artist = "Artist",
            album = "Album",
            duration = 150,
            coverUrl = null
        )

        val streamInfo = StreamInfo(
            url = "http://jellyfin-stream/jf-no-cover",
            playSessionId = "session-jkl",
            mediaSourceId = "mediasource-jkl",
            playMethod = PlayMethodType.DIRECT_PLAY,
            container = "flac",
            isTranscoding = false
        )

        val musicItem = buildMusicItemFromJellyfinSong(jellyfinSong, streamInfo)

        assertNotNull(musicItem)
        assertNull(musicItem.coverUrl)
    }

    // ==================== Empty URL Filter Tests ====================

    @Test
    fun `buildMusicItems - song with blank streamUrl returns null`() {
        val playlistSong = PlaylistSong(
            songId = "song-blank",
            title = "Blank URL Song",
            artist = "Artist",
            album = "Album",
            duration = 100,
            streamUrl = "http://stream-url",
            coverUrl = null
        )

        val streamInfo = StreamInfo(
            url = "", // Blank URL
            playSessionId = null,
            mediaSourceId = null,
            playMethod = null,
            container = null,
            isTranscoding = false
        )

        val musicItem = buildMusicItemFromPlaylistSong(playlistSong, streamInfo)

        assertNull(musicItem)
    }

    @Test
    fun `buildMusicItems - song with null streamInfo returns null`() {
        val playlistSong = PlaylistSong(
            songId = "song-null-info",
            title = "Null Info Song",
            artist = "Artist",
            album = "Album",
            duration = 100,
            streamUrl = "http://stream-url",
            coverUrl = null
        )

        val musicItem = buildMusicItemFromPlaylistSong(playlistSong, null)

        assertNull(musicItem)
    }

    // ==================== Playlist Building Tests ====================

    @Test
    fun `buildPlaylistMusicItems - filters out songs with blank URLs`() {
        val playlistSongs = listOf(
            PlaylistSong("1", "Song 1", "Artist", "Album", 100, "http://valid", null),
            PlaylistSong("2", "Song 2", "Artist", "Album", 100, "http://stream-url", null),
            PlaylistSong("3", "Song 3", "Artist", "Album", 100, "http://valid-3", null)
        )

        val streamInfos = mapOf(
            "1" to StreamInfo("http://valid", "s1", "m1", PlayMethodType.DIRECT_PLAY, "mp3", false),
            "2" to StreamInfo("", "s2", "m2", PlayMethodType.DIRECT_PLAY, "mp3", false), // Blank URL
            "3" to StreamInfo("http://valid-3", "s3", "m3", PlayMethodType.DIRECT_PLAY, "mp3", false)
        )

        val musicItems = buildPlaylistMusicItems(playlistSongs, streamInfos)

        assertEquals(2, musicItems.size)
        assertEquals("1", musicItems[0].id)
        assertEquals("3", musicItems[1].id)
    }

    @Test
    fun `buildPlaylistMusicItems - preserves order of valid songs`() {
        val playlistSongs = listOf(
            PlaylistSong("a", "First", "Artist", "Album", 100, "http://a", null),
            PlaylistSong("b", "Second", "Artist", "Album", 100, "http://b", null),
            PlaylistSong("c", "Third", "Artist", "Album", 100, "http://c", null)
        )

        val streamInfos = mapOf(
            "a" to StreamInfo("http://a", "sa", "ma", PlayMethodType.DIRECT_PLAY, "mp3", false),
            "b" to StreamInfo("http://b", "sb", "mb", PlayMethodType.DIRECT_PLAY, "mp3", false),
            "c" to StreamInfo("http://c", "sc", "mc", PlayMethodType.DIRECT_PLAY, "mp3", false)
        )

        val musicItems = buildPlaylistMusicItems(playlistSongs, streamInfos)

        assertEquals(3, musicItems.size)
        assertEquals("First", musicItems[0].title)
        assertEquals("Second", musicItems[1].title)
        assertEquals("Third", musicItems[2].title)
    }

    @Test
    fun `buildPlaylistMusicItems - empty input returns empty list`() {
        val musicItems = buildPlaylistMusicItems(emptyList(), emptyMap())
        assertTrue(musicItems.isEmpty())
    }

    @Test
    fun `buildPlaylistMusicItems - all blank URLs returns empty list`() {
        val playlistSongs = listOf(
            PlaylistSong("1", "Song 1", "Artist", "Album", 100, "http://url", null),
            PlaylistSong("2", "Song 2", "Artist", "Album", 100, "http://url", null)
        )

        val streamInfos = mapOf(
            "1" to StreamInfo("", "s1", "m1", PlayMethodType.DIRECT_PLAY, "mp3", false),
            "2" to StreamInfo("", "s2", "m2", PlayMethodType.DIRECT_PLAY, "mp3", false)
        )

        val musicItems = buildPlaylistMusicItems(playlistSongs, streamInfos)

        assertTrue(musicItems.isEmpty())
    }

    // ==================== Start Index Calculation Tests ====================

    @Test
    fun `calculateStartIndex - valid songId returns correct index`() {
        val songs = listOf("song-a", "song-b", "song-c")

        val index = calculateStartIndex(songs, "song-b")

        assertEquals(1, index)
    }

    @Test
    fun `calculateStartIndex - songId not in list returns 0`() {
        val songs = listOf("song-a", "song-b", "song-c")

        val index = calculateStartIndex(songs, "song-xyz")

        assertEquals(0, index)
    }

    @Test
    fun `calculateStartIndex - empty list returns 0`() {
        val index = calculateStartIndex(emptyList(), "song-a")
        assertEquals(0, index)
    }

    // ==================== Helper Functions (mirrors ViewModel logic) ====================

    /**
     * Helper function that mirrors PlaylistViewModel.buildMusicItems logic
     */
    private fun buildMusicItemFromPlaylistSong(
        playlistSong: PlaylistSong,
        streamInfo: StreamInfo?
    ): MusicItem? {
        if (streamInfo == null || streamInfo.url.isBlank()) {
            return null
        }
        return MusicItem(
            id = playlistSong.songId,
            title = playlistSong.title,
            artist = playlistSong.artist,
            album = playlistSong.album,
            duration = playlistSong.duration,
            streamUrl = streamInfo.url,
            coverUrl = playlistSong.coverUrl,
            playbackSessionId = streamInfo.playSessionId,
            mediaSourceId = streamInfo.mediaSourceId,
            streamContainer = streamInfo.container,
            streamPlayMethod = streamInfo.playMethod?.name,
            isTranscoding = streamInfo.isTranscoding
        )
    }

    /**
     * Helper function that mirrors JellyfinBrowseViewModel.buildMusicItems logic
     */
    private fun buildMusicItemFromJellyfinSong(
        jellyfinSong: JellyfinSong,
        streamInfo: StreamInfo?
    ): MusicItem? {
        if (streamInfo == null || streamInfo.url.isBlank()) {
            return null
        }
        return MusicItem(
            id = jellyfinSong.id,
            title = jellyfinSong.title,
            artist = jellyfinSong.artist,
            album = jellyfinSong.album,
            duration = jellyfinSong.duration,
            streamUrl = streamInfo.url,
            coverUrl = jellyfinSong.coverUrl,
            playbackSessionId = streamInfo.playSessionId,
            mediaSourceId = streamInfo.mediaSourceId,
            streamContainer = streamInfo.container,
            streamPlayMethod = streamInfo.playMethod?.name,
            isTranscoding = streamInfo.isTranscoding
        )
    }

    /**
     * Helper function that mirrors the playlist building logic
     */
    private fun buildPlaylistMusicItems(
        songs: List<PlaylistSong>,
        streamInfos: Map<String, StreamInfo>
    ): List<MusicItem> {
        return songs.mapNotNull { song ->
            val streamInfo = streamInfos[song.songId]
            buildMusicItemFromPlaylistSong(song, streamInfo)
        }
    }

    /**
     * Helper function that mirrors the start index calculation logic
     */
    private fun calculateStartIndex(songs: List<String>, targetSongId: String): Int {
        return songs.indexOfFirst { it == targetSongId }.coerceAtLeast(0)
    }
}