package com.voiceassistant.core.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.voiceassistant.core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queueSource: QueueSource = QueueSource.UNKNOWN
)

/**
 * 重复播放模式
 */
enum class RepeatMode {
    OFF,    // 不重复
    ALL,    // 列表循环
    ONE     // 单曲循环
}

/**
 * 队列来源
 */
enum class QueueSource {
    UNKNOWN,        // 未知来源
    BROWSER,        // 来自浏览页
    PLAYLIST,       // 来自播放列表
    LOCAL           // 来自本地歌曲
}

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
    val coverUrl: String? = null,
    // Jellyfin 播放会话信息
    val playbackSessionId: String? = null,
    val mediaSourceId: String? = null,
    val streamContainer: String? = null,
    val streamPlayMethod: String? = null,
    val isTranscoding: Boolean = false
)

/**
 * 音乐播放器 - 使用 ExoPlayer
 * 使用 Retrofit 获取的流 URL，URL 已包含 API Key
 */
@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackReporter: PlaybackReporter = NoOpPlaybackReporter()
) {
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "music_player_channel"
        const val CHANNEL_NAME = "音乐播放"
        private const val NOW_PLAYING_ACTIVITY_CLASS = "com.voiceassistant.app.ui.music.NowPlayingActivity"

        const val ACTION_PLAY = "com.voiceassistant.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.voiceassistant.app.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.voiceassistant.app.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.voiceassistant.app.ACTION_NEXT"
        const val ACTION_STOP = "com.voiceassistant.app.ACTION_STOP"

        private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private var exoPlayer: ExoPlayer? = null
    private var _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private var playlist: List<MusicItem> = emptyList()

    // Notification support
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService() ?: throw IllegalStateException("NotificationManager not available")
    }
    private var notificationReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    // Shuffle support
    private var originalPlaylist: List<MusicItem> = emptyList()
    private var shuffleMode = false

    // Playback progress reporting
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressUpdateJob: Job? = null
    private var currentPlaySessionId: String? = null
    // 记录最近一次 seek 的位置，用于处理 ExoPlayer timeline 重建导致的 position 重置
    private var lastSeekPosition: Long? = null
    // 记录最近一次 seek 的系统时间（毫秒），用于计算 effective position
    private var lastSeekRealtimeMs: Long = 0L
    // 当使用 StartTimeTicks 方案时，记录 seek 的目标位置（毫秒）
    // 用于计算 effective position = seekedPositionMs + currentPosition
    private var seekedPositionMs: Long? = null

    // 播放错误状态
    private var _playbackError: androidx.media3.common.PlaybackException? = null
    private val playbackError: androidx.media3.common.PlaybackException? get() = _playbackError

    // Favorite state
    private var isFavorite = false

    /**
     * Seek 回调接口，用于获取带起始位置的流 URL
     * 当 ExoPlayer 的 range seek 不支持时，通过重新加载流来实现 seek
     */
    interface SeekCallback {
        /**
         * 获取指定位置的流媒体 URL
         * @param item 当前的 MusicItem（包含已有的 playbackSessionId 和 mediaSourceId）
         * @param positionMs 目标位置（毫秒）
         * @return 新的流媒体 URL，如果不需要特殊处理则返回 null
         */
        suspend fun onSeekToGetStreamUrl(item: MusicItem, positionMs: Long): String?
    }
    private var seekCallback: SeekCallback? = null

    /**
     * 设置 Seek 回调
     */
    fun setSeekCallback(callback: SeekCallback?) {
        seekCallback = callback
    }

    // MediaSession for external control (bluetooth, car audio, etc.)
    private val mediaSession: MediaSession by lazy {
        MediaSession(context, "MusicPlayer").apply {
            setCallback(mediaSessionCallback)
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            resume()
        }

        override fun onPause() {
            pause()
        }

        override fun onSkipToNext() {
            playNext()
        }

        override fun onSkipToPrevious() {
            playPrevious()
        }

        override fun onSeekTo(pos: Long) {
            seekTo(pos)
        }

        override fun onStop() {
            stop()
        }

        override fun onFastForward() {
            seekTo(playbackPosition + 10000) // 10 seconds
        }

        override fun onRewind() {
            seekTo(playbackPosition - 10000) // 10 seconds
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            android.util.Log.i("♪", "onIsPlayingChanged: $isPlaying")
            updateState { it.copy(isPlaying = isPlaying) }
            updateMediaSessionPlaybackState()
            updateNotification()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.i("♪", "onPlayerError: ${error.errorCode}")
            Timber.e("Player error: ${error.errorCode}, ${error.message}")
            Timber.e("Player error cause: ${error.cause?.message}")
            Timber.e("Player error stack: ${error.stackTraceToString()}")
            _playbackError = error
            updateMediaSessionPlaybackState()
        }

        override fun onPlaybackStateChanged(state: Int) {
            val pos = exoPlayer?.currentPosition ?: -1
            val mediaId = exoPlayer?.currentMediaItem?.mediaId ?: "null"
            val mediaIndex = exoPlayer?.currentMediaItemIndex ?: -1
            android.util.Log.i("♪", "onPlaybackStateChanged: state=$state, pos=$pos, mediaId=$mediaId, mediaIndex=$mediaIndex")
            when (state) {
                Player.STATE_ENDED -> {
                    android.util.Log.i("♪", "STATE_ENDED - calling playNext()")
                    // 播放完成，自动播放下一首
                    playNext()
                }
                Player.STATE_BUFFERING -> {
                    android.util.Log.i("♪", "STATE_BUFFERING, pos=$pos")
                }
                Player.STATE_READY -> {
                    android.util.Log.i("♪", "STATE_READY, pos=$pos")
                    // 清除错误状态
                    _playbackError = null
                    // ExoPlayer 的 duration 对流媒体可能无效，使用 MusicItem 的 duration
                    val currentItem = _state.value.playlist.getOrNull(_state.value.currentIndex)
                    val playerDuration = exoPlayer?.duration ?: 0
                    val itemDuration = (currentItem?.duration ?: 0) * 1000L
                    val finalDuration = if (playerDuration > 0) playerDuration else itemDuration
                    updateState {
                        it.copy(duration = finalDuration)
                    }
                }
            }
            updateMediaSessionPlaybackState()
            updateNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId ?: "null"
            val mediaIndex = exoPlayer?.currentMediaItemIndex ?: -1
            val playlistSize = playlist.size
            android.util.Log.i("♪", "onMediaItemTransition: reason=$reason, mediaId=$mediaId, mediaIndex=$mediaIndex, playlistSize=$playlistSize")
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
                // 更新 MediaSession 元数据
                updateMediaSessionMetadata(item)
                // 更新通知
                updateNotification()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val mediaId = exoPlayer?.currentMediaItem?.mediaId ?: "null"
            val mediaIndex = exoPlayer?.currentMediaItemIndex ?: -1
            val playlistSize = playlist.size
            android.util.Log.i("♪", "onPositionDiscontinuity: reason=$reason, oldPos=${oldPosition.positionMs}, newPos=${newPosition.positionMs}, state=${exoPlayer?.playbackState}, mediaId=$mediaId, mediaIndex=$mediaIndex, playlistSize=$playlistSize")

            // 处理 ExoPlayer 内部 timeline 重建导致的 position 重置
            // 当 Jellyfin 转码流不支持 Range seek 时，ExoPlayer 会收到从头的数据，触发 DISCONTINUITY_REASON_REMOVE
            // 此时 newPosition 被重置为 0，但实际应该保持用户请求的 seek 位置
            if (reason == Player.DISCONTINUITY_REASON_REMOVE && newPosition.positionMs == 0L) {
                val player = exoPlayer ?: return
                // 检查是否刚刚 seek 过
                val lastSeekPos = lastSeekPosition
                if (lastSeekPos != null && lastSeekPos > 0 && kotlin.math.abs(lastSeekPos - oldPosition.positionMs) < 5000) {
                    android.util.Log.i("♪", "DISCONTINUITY_REASON_REMOVE after seek to $lastSeekPos, re-apply seek")
                    player.seekTo(lastSeekPos)
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

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    1500,  // minBufferMs
                    5000,  // maxBufferMs
                    250,   // bufferForPlaybackMs
                    750    // bufferForPlaybackAfterRebufferMs
                )
                .build()

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .build().also {
                    it.addListener(playerListener)
                }
            Timber.d("MusicPlayer: 创建 ExoPlayer with FFmpeg decoder extension and low-latency load control")
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
        // 如果正在播放同一首歌且 URL 相同，不重新创建播放器
        val currentItem = _state.value.playlist.getOrNull(_state.value.currentIndex)
        if (currentItem != null && currentItem.id == item.id && exoPlayer != null) {
            val player = exoPlayer
            val currentMediaUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentMediaUri == item.streamUrl) {
                val isActivelyPlaying = player?.isPlaying == true
                if (isActivelyPlaying) {
                    android.util.Log.i("♪", "SAME song+url ${item.title}, already playing, ignoring")
                    return
                }
                android.util.Log.i("♪", "SAME song+url ${item.title}, but player is not actively playing, restarting")
            } else {
                android.util.Log.i("♪", "SAME id but DIFFERENT url! current=$currentMediaUri, new=${item.streamUrl}")
            }
        }

        android.util.Log.i("♪", "NEW song: ${item.title}")
        // 清除 seek 相关状态
        seekedPositionMs = null
        lastSeekPosition = null
        lastSeekRealtimeMs = 0L

        val player = getOrCreatePlayer()
        player.stop()
        player.clearMediaItems()
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

        // 更新 MediaSession metadata
        updateMediaSessionMetadata(item)

        // 显示通知
        showNotification(item)

        // 上报播放开始
        reportPlaybackStart(item.id)

        // 开始进度更新
        startProgressUpdates()
    }

    /**
     * 播放播放列表
     * @param items 播放列表
     * @param startIndex 起始索引
     * @param source 队列来源
     */
    fun playPlaylist(items: List<MusicItem>, startIndex: Int = 0, source: QueueSource = QueueSource.UNKNOWN) {
        if (items.isEmpty()) return

        val player = getOrCreatePlayer()
        player.stop()
        player.clearMediaItems()
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
                isPlaying = true,
                queueSource = source
            )
        }

        Timber.d("MusicPlayer: playing playlist, start at index $startIndex, source=$source")

        // 更新 MediaSession metadata
        currentItem?.let { updateMediaSessionMetadata(it) }

        // 显示通知
        currentItem?.let { showNotification(it) }

        // 上报播放开始
        currentItem?.let { reportPlaybackStart(it.id) }

        // 开始进度更新
        startProgressUpdates()
    }

    /**
     * 跳转到播放列表中的指定位置
     */
    fun seekToIndex(index: Int) {
        val player = exoPlayer ?: return
        if (index < 0 || index >= player.mediaItemCount) {
            Timber.w("seekToIndex: index $index out of range (0-${player.mediaItemCount - 1})")
            return
        }
        player.seekTo(index, 0)
        Timber.d("MusicPlayer: seek to index $index")
    }

    /**
     * 从当前播放队列移除歌曲。
     * 如果移除的是当前歌曲，则自动切到下一首；如果队列清空，则停止播放并清空状态。
     */
    fun removeFromQueue(index: Int): Boolean {
        val player = exoPlayer ?: return false
        if (index !in playlist.indices) {
            Timber.w("removeFromQueue: index $index out of range")
            return false
        }

        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(_state.value.currentIndex)
        val wasPlaying = player.isPlaying
        val newPlaylist = playlist.toMutableList().apply { removeAt(index) }

        if (shuffleMode) {
            originalPlaylist = originalPlaylist.toMutableList().apply {
                val originalIndex = indexOfFirst { it.id == playlist[index].id }
                if (originalIndex >= 0) {
                    removeAt(originalIndex)
                }
            }
        }

        if (newPlaylist.isEmpty()) {
            player.stop()
            player.clearMediaItems()
            stopProgressUpdates()
            dismissNotification()
            playlist = emptyList()
            originalPlaylist = emptyList()
            shuffleMode = false
            updateState {
                it.copy(
                    isPlaying = false,
                    currentSongId = null,
                    currentSongTitle = null,
                    currentPosition = 0,
                    duration = 0,
                    playlist = emptyList(),
                    currentIndex = -1,
                    isShuffleEnabled = false
                )
            }
            return true
        }

        val targetIndex = when {
            index < currentIndex -> currentIndex - 1
            index > currentIndex -> currentIndex
            else -> index.coerceAtMost(newPlaylist.lastIndex)
        }.coerceIn(0, newPlaylist.lastIndex)

        val startPositionMs = if (index == currentIndex) 0L else player.currentPosition
        playlist = newPlaylist

        player.stop()
        player.clearMediaItems()
        player.setMediaItems(newPlaylist.map { MediaItem.fromUri(it.streamUrl) }, targetIndex, startPositionMs)
        player.prepare()
        if (wasPlaying) {
            player.play()
        }

        val currentItem = newPlaylist.getOrNull(targetIndex)
        val currentRepeatMode = _state.value.repeatMode
        updateState {
            it.copy(
                isPlaying = wasPlaying,
                currentSongId = currentItem?.id,
                currentSongTitle = currentItem?.title,
                currentPosition = startPositionMs,
                duration = (currentItem?.duration ?: 0) * 1000L,
                playlist = newPlaylist,
                currentIndex = targetIndex,
                isShuffleEnabled = shuffleMode,
                repeatMode = currentRepeatMode
            )
        }
        // 更新 MediaSession 元数据
        currentItem?.let { updateMediaSessionMetadata(it) }
        updateMediaSessionPlaybackState()
        updateNotification()
        return true
    }

    /**
     * 调整当前播放队列顺序。
     * from/to 基于当前展示的播放队列索引。
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int): Boolean {
        val player = exoPlayer ?: return false
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices) {
            Timber.w("moveQueueItem: from=$fromIndex or to=$toIndex out of range")
            return false
        }
        if (fromIndex == toIndex) {
            return true
        }

        val currentItem = playlist.getOrNull(player.currentMediaItemIndex.coerceAtLeast(_state.value.currentIndex))
        val wasPlaying = player.isPlaying
        val currentPosition = player.currentPosition
        val reordered = playlist.toMutableList().apply {
            val movedItem = removeAt(fromIndex)
            add(toIndex, movedItem)
        }

        playlist = reordered
        originalPlaylist = reordered.toList()

        val targetIndex = currentItem?.let { item ->
            reordered.indexOfFirst { it.id == item.id }
        }?.takeIf { it >= 0 } ?: toIndex

        player.stop()
        player.clearMediaItems()
        player.setMediaItems(reordered.map { MediaItem.fromUri(it.streamUrl) }, targetIndex, currentPosition)
        player.prepare()
        if (wasPlaying) {
            player.play()
        }

        val activeItem = reordered.getOrNull(targetIndex)
        val currentRepeatMode = _state.value.repeatMode
        updateState {
            it.copy(
                isPlaying = wasPlaying,
                currentSongId = activeItem?.id,
                currentSongTitle = activeItem?.title,
                currentPosition = currentPosition,
                duration = (activeItem?.duration ?: 0) * 1000L,
                playlist = reordered,
                currentIndex = targetIndex,
                isShuffleEnabled = shuffleMode,
                repeatMode = currentRepeatMode
            )
        }
        // 更新 MediaSession 元数据
        activeItem?.let { updateMediaSessionMetadata(it) }
        updateMediaSessionPlaybackState()
        updateNotification()
        return true
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
        val player = exoPlayer
        val itemId = _state.value.currentSongId
        val position = player?.currentPosition ?: 0

        player?.stop()
        stopProgressUpdates()

        // 上报播放停止
        itemId?.let { reportPlaybackStopped(it, position) }

        updateState { it.copy(isPlaying = false, currentPosition = 0) }
    }

    /**
     * 跳到下一首
     */
    fun playNext() {
        val player = exoPlayer ?: return
        val state = _state.value

        // 处理单曲循环
        if (state.repeatMode == RepeatMode.ONE) {
            player.seekTo(0)
            return
        }

        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (state.repeatMode == RepeatMode.ALL) {
            // 循环播放到第一首
            player.seekTo(0, 0)
        }
        // repeatMode.OFF 且没有下一首时不做任何操作
    }

    /**
     * 跳到上一首
     * 参考 jellyfin-android：如果当前位置 > 3秒则回到开头，否则播放上一首
     */
    fun playPrevious() {
        val player = exoPlayer ?: return
        val MAX_SKIP_TO_PREV_MS = 3000L
        val state = _state.value

        when {
            // 如果当前位置超过 3 秒，回到当前歌曲开头
            player.currentPosition > MAX_SKIP_TO_PREV_MS -> {
                player.seekTo(0)
            }
            // 有上一首则播放上一首
            player.hasPreviousMediaItem() -> {
                player.seekToPreviousMediaItem()
            }
            // 列表循环则跳到最后一首
            state.repeatMode == RepeatMode.ALL -> {
                val lastIndex = player.mediaItemCount - 1
                if (lastIndex >= 0) {
                    player.seekTo(lastIndex, 0)
                }
            }
        }
    }

    /**
     * 设置播放速度
     * @param speed 播放速度，1.0 为正常速度
     * @return true 如果速度已更改
     */
    fun setPlaybackSpeed(speed: Float): Boolean {
        val player = exoPlayer ?: return false
        val parameters = player.playbackParameters
        if (parameters.speed != speed) {
            player.playbackParameters = parameters.withSpeed(speed)
            android.util.Log.i("♪", "Playback speed set to $speed")
            return true
        }
        return false
    }

    /**
     * 获取当前播放速度
     */
    fun getPlaybackSpeed(): Float {
        return exoPlayer?.playbackParameters?.speed ?: 1.0f
    }

    /**
     * 快退（参考 jellyfin）
     */
    fun rewind() {
        val player = exoPlayer ?: return
        val SKIP_BACK_LENGTH_MS = 10000L // 10秒
        val newPosition = (player.currentPosition - SKIP_BACK_LENGTH_MS).coerceAtLeast(0)
        player.seekTo(newPosition)
        android.util.Log.i("♪", "Rewind to $newPosition")
    }

    /**
     * 快进（参考 jellyfin）
     */
    fun fastForward() {
        val player = exoPlayer ?: return
        val SKIP_FORWARD_LENGTH_MS = 10000L // 10秒
        val newPosition = (player.currentPosition + SKIP_FORWARD_LENGTH_MS).coerceAtMost(player.duration)
        player.seekTo(newPosition)
        android.util.Log.i("♪", "Fast forward to $newPosition")
    }

    /**
     * 切换 shuffle 模式
     * @return 新的 shuffle 状态
     */
    fun toggleShuffle(): Boolean {
        shuffleMode = !shuffleMode
        val player = exoPlayer ?: return shuffleMode
        val currentIndex = _state.value.currentIndex

        if (shuffleMode) {
            // 保存原始播放列表
            originalPlaylist = playlist.toList()
            // 随机打乱播放列表，但保持当前歌曲在首位
            val currentItem = playlist.getOrNull(currentIndex)
            val otherItems = playlist.filterIndexed { index, _ -> index != currentIndex }.shuffled()
            playlist = if (currentItem != null) {
                listOf(currentItem) + otherItems
            } else {
                otherItems
            }
            // 更新 ExoPlayer 媒体项
            val newIndex = playlist.indexOfFirst { it.id == currentItem?.id }.coerceAtLeast(0)
            val mediaItems = playlist.map { MediaItem.fromUri(it.streamUrl) }
            player.setMediaItems(mediaItems, newIndex, 0)
        } else {
            // 恢复原始播放列表
            val currentItem = playlist.getOrNull(currentIndex)
            playlist = originalPlaylist
            val newIndex = currentItem?.let { originalPlaylist.indexOfFirst { item -> item.id == it.id } } ?: 0
            val mediaItems = originalPlaylist.map { MediaItem.fromUri(it.streamUrl) }
            player.setMediaItems(mediaItems, newIndex, 0)
        }

        updateState { it.copy(isShuffleEnabled = shuffleMode) }
        android.util.Log.i("♪", "Shuffle mode: $shuffleMode")
        return shuffleMode
    }

    /**
     * 切换 repeat 模式
     * @return 新的 repeat 模式
     */
    fun toggleRepeat(): RepeatMode {
        val state = _state.value
        val newMode = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        updateState { it.copy(repeatMode = newMode) }
        android.util.Log.i("♪", "Repeat mode: $newMode")
        return newMode
    }

    /**
     * 获取当前 shuffle 状态
     */
    fun isShuffleEnabled(): Boolean = shuffleMode

    /**
     * 获取当前 repeat 模式
     */
    fun getRepeatMode(): RepeatMode = _state.value.repeatMode

    /**
     * 切换收藏状态
     * @return 新的收藏状态
     */
    fun toggleFavorite(): Boolean {
        val itemId = _state.value.currentSongId ?: return false
        isFavorite = !isFavorite

        playerScope.launch {
            try {
                if (isFavorite) {
                    playbackReporter.markAsFavorite(itemId)
                    android.util.Log.i("♪", "Added to favorites: $itemId")
                } else {
                    playbackReporter.removeFromFavorites(itemId)
                    android.util.Log.i("♪", "Removed from favorites: $itemId")
                }
            } catch (e: Exception) {
                Timber.e(e, "toggleFavorite failed")
                // 回滚状态
                isFavorite = !isFavorite
            }
        }

        return isFavorite
    }

    /**
     * 获取当前收藏状态
     */
    fun isFavorite(): Boolean = isFavorite

    /**
     * 上报播放开始
     */
    private fun reportPlaybackStart(itemId: String) {
        playerScope.launch {
            try {
                val position = exoPlayer?.currentPosition ?: 0
                playbackReporter.reportPlaybackStart(
                    itemId = itemId,
                    positionTicks = position * 10000 // 毫秒转 ticks
                )
            } catch (e: Exception) {
                Timber.e(e, "reportPlaybackStart failed")
            }
        }
    }

    /**
     * 上报播放进度
     */
    private fun reportPlaybackProgress(itemId: String, positionMs: Long, playSessionId: String? = null, mediaSourceId: String? = null) {
        playerScope.launch {
            try {
                playbackReporter.reportPlaybackProgress(
                    itemId = itemId,
                    positionTicks = positionMs * 10000, // 毫秒转 ticks
                    playSessionId = playSessionId,
                    mediaSourceId = mediaSourceId
                )
            } catch (e: Exception) {
                Timber.e(e, "reportPlaybackProgress failed")
            }
        }
    }

    /**
     * 上报播放停止
     */
    private fun reportPlaybackStopped(itemId: String, positionMs: Long) {
        playerScope.launch {
            try {
                playbackReporter.reportPlaybackStopped(
                    itemId = itemId,
                    positionTicks = positionMs * 10000 // 毫秒转 ticks
                )
            } catch (e: Exception) {
                Timber.e(e, "reportPlaybackStopped failed")
            }
        }
    }

    /**
     * 开始进度更新
     */
    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateJob = playerScope.launch {
            while (isActive) {
                delay(10000) // 每 10 秒更新一次
                val player = exoPlayer ?: continue
                if (player.isPlaying) {
                    val currentItem = _state.value.playlist.getOrNull(_state.value.currentIndex)
                    val itemId = currentItem?.id ?: continue
                    reportPlaybackProgress(
                        itemId = itemId,
                        positionMs = getCurrentPosition(),
                        playSessionId = currentItem.playbackSessionId,
                        mediaSourceId = currentItem.mediaSourceId
                    )
                }
            }
        }
    }

    /**
     * 停止进度更新
     */
    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val currentItem = _state.value.playlist.getOrNull(_state.value.currentIndex)
        val itemId = currentItem?.id ?: return
        android.util.Log.i("♪", "SEEK to $positionMs ms, before=${player.currentPosition}, playing=${player.isPlaying}")

        // 先上报 seek 位置到 Jellyfin
        val playSessionId = currentItem.playbackSessionId
        val mediaSourceId = currentItem.mediaSourceId
        playerScope.launch {
            try {
                playbackReporter.reportPlaybackProgress(
                    itemId = itemId,
                    positionTicks = positionMs * 10000, // 毫秒转 ticks
                    playSessionId = playSessionId,
                    mediaSourceId = mediaSourceId
                )
            } catch (e: Exception) {
                Timber.e(e, "reportPlaybackProgress on seek failed")
            }
        }

        // Jellyfin 转码流（/Audio/{id}/stream）不支持 HTTP Range seek，
        // 改用 StartTimeTicks 方案：通过回调获取带起始位置的 URL，重新加载媒体源
        val callback = seekCallback
        if (callback != null) {
            android.util.Log.i("♪", "SEEK: using StartTimeTicks callback")
            playerScope.launch {
                try {
                    val newUrl = callback.onSeekToGetStreamUrl(currentItem, positionMs)
                    if (!newUrl.isNullOrEmpty()) {
                        android.util.Log.i("♪", "SEEK: reloading media with StartTimeTicks URL, pos=$positionMs")
                        val wasPlaying = player.isPlaying
                        lastSeekPosition = positionMs
                        lastSeekRealtimeMs = android.os.SystemClock.elapsedRealtime()
                        seekedPositionMs = positionMs
                        val mediaItem = MediaItem.fromUri(newUrl)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        if (wasPlaying) {
                            player.play()
                        }
                        android.util.Log.i("♪", "SEEK: media reloaded, Jellyfin will transcode from $positionMs")
                    } else {
                        player.seekTo(positionMs)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "SEEK: callback failed, falling back to player.seekTo()")
                    lastSeekPosition = positionMs
                    lastSeekRealtimeMs = android.os.SystemClock.elapsedRealtime()
                    player.seekTo(positionMs)
                }
            }
        } else {
            // 没有回调，使用普通的 ExoPlayer seek
            lastSeekPosition = positionMs
            lastSeekRealtimeMs = android.os.SystemClock.elapsedRealtime()
            player.seekTo(positionMs)
            android.util.Log.i("♪", "SEEK done, now=${player.currentPosition}")
        }
    }

    private fun logCurrentState(tag: String, msg: String) {
        android.util.Log.i("MusicPlayer", "[$tag] $msg")
    }

    /**
     * 获取当前播放位置
     * 当使用 StartTimeTicks 方案时，返回 seekedPosition + currentPosition
     */
    fun getCurrentPosition(): Long {
        val rawPos = exoPlayer?.currentPosition ?: 0
        val seeked = seekedPositionMs
        return if (seeked != null && seeked > 0) {
            // StartTimeTicks 模式下：音频从 seekedPosition 开始播放
            // ExoPlayer.currentPosition 是新媒体源中的相对位置
            seeked + rawPos
        } else {
            rawPos
        }
    }

    /**
     * 获取当前播放位置（供 MediaSession 使用）
     */
    private val playbackPosition: Long
        get() = getCurrentPosition()

    /**
     * 更新 MediaSession 播放状态
     */
    private fun updateMediaSessionPlaybackState() {
        val player = exoPlayer ?: return
        val playbackState = when {
            player.playbackState == Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
            player.isPlaying -> PlaybackState.STATE_PLAYING
            player.playbackState == Player.STATE_READY -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }

        val playbackActions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_STOP

        val state = PlaybackState.Builder()
            .setActions(playbackActions)
            .setState(playbackState, playbackPosition, if (player.isPlaying) 1.0f else 0.0f)
            .build()

        mediaSession.setPlaybackState(state)
        mediaSession.isActive = player.isPlaying
    }

    /**
     * 更新 MediaSession 元数据
     */
    @Suppress("DEPRECATION")
    private fun updateMediaSessionMetadata(item: MusicItem) {
        try {
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist ?: "未知艺术家")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, item.album ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, item.duration * 1000L)
                .build()
            mediaSession.setMetadata(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update media session metadata")
        }
    }

    /**
     * 显示播放通知
     */
    private fun showNotification(item: MusicItem) {
        createNotificationChannel()
        val player = exoPlayer ?: return

        val style = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        val hasPrevious = player.hasPreviousMediaItem()
        val hasNext = player.hasNextMediaItem()

        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        builder.setStyle(style)
        @Suppress("DEPRECATION")
        builder.setSmallIcon(R.drawable.ic_notification_play)
        builder.setContentTitle(item.title)
        builder.setContentText(item.artist ?: "未知艺术家")
        builder.setSubText(item.album ?: "")
        builder.setVisibility(Notification.VISIBILITY_PUBLIC)
        builder.setOngoing(player.isPlaying)
        builder.setContentIntent(createActivityPendingIntent())

        // Previous button
        builder.addAction(
            R.drawable.ic_notification_previous,
            "上一首",
            createPendingIntent(ACTION_PREVIOUS)
        )

        // Play/Pause button
        @Suppress("DEPRECATION")
        val playPauseIcon = if (player.isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play
        val playPauseText = if (player.isPlaying) "暂停" else "播放"
        builder.addAction(
            playPauseIcon,
            playPauseText,
            createPendingIntent(if (player.isPlaying) ACTION_PAUSE else ACTION_PLAY)
        )

        // Next button
        builder.addAction(
            R.drawable.ic_notification_next,
            "下一首",
            createPendingIntent(ACTION_NEXT)
        )

        builder.setDeleteIntent(createPendingIntent(ACTION_STOP))
        val notification = builder.build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        registerNotificationReceiver()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 更新通知
     */
    private fun updateNotification() {
        val currentItem = _state.value.playlist.getOrNull(_state.value.currentIndex) ?: return
        showNotification(currentItem)
    }

    /**
     * 创建待定意图
     */
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PENDING_INTENT_FLAGS)
    }

    private fun createActivityPendingIntent(): PendingIntent {
        val intent = Intent().setClassName(context.packageName, NOW_PLAYING_ACTIVITY_CLASS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(context, 1, intent, PENDING_INTENT_FLAGS)
    }

    /**
     * 注册通知广播接收器
     */
    private fun registerNotificationReceiver() {
        if (isReceiverRegistered) return

        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_PLAY -> resume()
                    ACTION_PAUSE -> pause()
                    ACTION_PREVIOUS -> playPrevious()
                    ACTION_NEXT -> playNext()
                    ACTION_STOP -> {
                        stop()
                        dismissNotification()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
            addAction(ACTION_STOP)
        }

        ContextCompat.registerReceiver(
            context,
            notificationReceiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
    }

    /**
     * 关闭通知
     */
    private fun dismissNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        if (isReceiverRegistered && notificationReceiver != null) {
            try {
                context.unregisterReceiver(notificationReceiver!!)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            notificationReceiver = null
            isReceiverRegistered = false
        }
    }

    /**
     * 处理外部 Intent（如通知点击）
     */
    fun handleNotificationAction(intent: Intent) {
        when (intent.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> {
                stop()
                dismissNotification()
            }
        }
    }

    /**
     * 获取当前播放状态
     */
    fun getState(): MusicPlayerState {
        val player = exoPlayer
        val position = getCurrentPosition()
        val duration = player?.duration ?: 0
        // 过滤无效的 duration 值 (TIME_UNSET = -9223372036854775808L)
        // 如果播放器 duration 无效，尝试使用 MusicItem 中的 duration（秒转毫秒）
        val currentItem = _state.value.playlist.getOrNull(_state.value.currentIndex)
        val itemDurationMs = (currentItem?.duration ?: 0) * 1000L
        val validDuration = if (duration > 0) {
            duration
        } else if (itemDurationMs > 0) {
            itemDurationMs
        } else {
            0
        }
        return _state.value.copy(
            currentPosition = position,
            duration = validDuration
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        val itemId = _state.value.currentSongId
        val position = exoPlayer?.currentPosition ?: 0

        stopProgressUpdates()
        dismissNotification()

        // 上报播放停止
        itemId?.let { reportPlaybackStopped(it, position) }

        exoPlayer?.release()
        exoPlayer = null
        mediaSession.isActive = false
        mediaSession.release()
        _state.value = MusicPlayerState()
        playlist = emptyList()
        originalPlaylist = emptyList()
    }

    private fun updateState(update: (MusicPlayerState) -> MusicPlayerState) {
        _state.value = update(_state.value)
    }
}
