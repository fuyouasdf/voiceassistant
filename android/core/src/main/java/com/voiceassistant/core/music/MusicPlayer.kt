package com.voiceassistant.core.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音乐播放器状态
 */
data class MusicPlayerState(
    val isPlaying: Boolean = false,
    val currentSongId: String? = null,
    val currentSongTitle: String? = null,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val playlist: List<MusicItem> = emptyList(),
    val currentIndex: Int = -1
)

/**
 * 音乐项
 */
data class MusicItem(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int, // 秒
    val streamUrl: String,
    val coverUrl: String? = null
)

/**
 * 音乐播放器 - 使用 ExoPlayer
 * 使用 Retrofit 获取的流 URL，URL 已包含 API Key
 */
@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private var playlist: List<MusicItem> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e("Player error: ${error.errorCode}, ${error.message}")
            Timber.e("Player error cause: ${error.cause?.message}")
            Timber.e("Player error stack: ${error.stackTraceToString()}")
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_ENDED -> {
                    // 播放完成，自动播放下一首
                    playNext()
                }
                Player.STATE_BUFFERING -> {
                    Timber.d("Player buffering...")
                }
                Player.STATE_READY -> {
                    updateState {
                        it.copy(duration = exoPlayer?.duration ?: 0)
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val currentIndex = exoPlayer?.currentMediaItemIndex ?: -1
            if (currentIndex >= 0 && currentIndex < playlist.size) {
                val item = playlist[currentIndex]
                updateState {
                    it.copy(
                        currentIndex = currentIndex,
                        currentSongId = item.id,
                        currentSongTitle = item.title
                    )
                }
            }
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            // 使用 FFmpeg 解码器扩展，支持 WMA 等格式
            val renderersFactory = DefaultRenderersFactory(context).apply {
                setEnableDecoderFallback(true)
                // 启用扩展解码器（FFmpeg 解码器）
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            }
            exoPlayer = ExoPlayer.Builder(context, renderersFactory).build().also {
                it.addListener(playerListener)
            }
            Timber.d("MusicPlayer: 创建 ExoPlayer with FFmpeg decoder extension")
        }
        return exoPlayer!!
    }

    /**
     * 检查 URL 是否为 HLS 流
     */
    private fun isHlsStream(url: String): Boolean {
        return url.contains("master.m3u8") || url.contains(".m3u8")
    }

    /**
     * 播放歌曲
     */
    fun play(item: MusicItem) {
        // 释放旧播放器
        exoPlayer?.release()
        exoPlayer = null

        val player = getOrCreatePlayer()
        playlist = listOf(item)

        val mediaItem = MediaItem.fromUri(item.streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        updateState {
            it.copy(
                playlist = playlist,
                currentIndex = 0,
                currentSongId = item.id,
                currentSongTitle = item.title,
                isPlaying = true
            )
        }

        Timber.d("MusicPlayer: playing ${item.title}, isHls: ${isHlsStream(item.streamUrl)}")
    }

    /**
     * 播放播放列表
     */
    fun playPlaylist(items: List<MusicItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return

        // 释放旧播放器
        exoPlayer?.release()
        exoPlayer = null

        val player = getOrCreatePlayer()
        playlist = items

        val mediaItems = items.map { MediaItem.fromUri(it.streamUrl) }
        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
        player.play()

        val currentItem = items.getOrNull(startIndex)
        updateState {
            it.copy(
                playlist = items,
                currentIndex = startIndex,
                currentSongId = currentItem?.id,
                currentSongTitle = currentItem?.title,
                isPlaying = true
            )
        }

        Timber.d("MusicPlayer: playing playlist, start at index $startIndex")
    }

    /**
     * 暂停
     */
    fun pause() {
        exoPlayer?.pause()
    }

    /**
     * 继续播放
     */
    fun resume() {
        exoPlayer?.play()
    }

    /**
     * 停止
     */
    fun stop() {
        exoPlayer?.stop()
        updateState { it.copy(isPlaying = false, currentPosition = 0) }
    }

    /**
     * 跳到下一首
     */
    fun playNext() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            // 循环播放
            player.seekTo(0, 0)
        }
    }

    /**
     * 跳到上一首
     */
    fun playPrevious() {
        val player = exoPlayer ?: return
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            // 循环播放到最后一首
            val lastIndex = player.mediaItemCount - 1
            if (lastIndex >= 0) {
                player.seekTo(lastIndex, 0)
            }
        }
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    /**
     * 获取当前播放位置
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0
    }

    /**
     * 获取当前播放状态
     */
    fun getState(): MusicPlayerState {
        val player = exoPlayer
        return _state.value.copy(
            currentPosition = player?.currentPosition ?: 0,
            duration = player?.duration ?: 0
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _state.value = MusicPlayerState()
        playlist = emptyList()
    }

    private fun updateState(update: (MusicPlayerState) -> MusicPlayerState) {
        _state.value = update(_state.value)
    }
}