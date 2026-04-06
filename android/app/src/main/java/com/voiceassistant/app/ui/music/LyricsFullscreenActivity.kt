package com.voiceassistant.app.ui.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ActivityLyricsFullscreenBinding
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.LyricLine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LyricsFullscreenActivity : AppCompatActivity() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    @Inject
    lateinit var jellyfinClient: JellyfinClient

    @Inject
    lateinit var imageLoader: ImageLoader

    private lateinit var binding: ActivityLyricsFullscreenBinding
    private val lyricsAdapter = LyricsAdapter { line ->
        musicPlayer.seekTo(line.startMs)
        updateLyricsPosition(line.startMs)
    }
    private var progressJob: Job? = null
    private var lyricsJob: Job? = null
    private var currentLyricsSongId: String? = null
    private var currentLyrics: List<LyricLine> = emptyList()
    private var highlightedLyricIndex: Int = -1
    private var isUserScrollingLyrics = false
    private var pendingCenterLyricIndex: Int? = null

    // Fallback gradient drawable used when no cover is available
    private val noCoverGradient: GradientDrawable by lazy {
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#FF1A1A2E"), Color.parseColor("#FF0A0A0F"))
        ).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLyricsFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupRecyclerView()
        setupListeners()
        observePlayerState()
    }

    override fun onStart() {
        super.onStart()
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (true) {
                updateLyricsPosition(musicPlayer.getState().currentPosition)
                delay(500)
            }
        }
    }

    override fun onStop() {
        progressJob?.cancel()
        progressJob = null
        lyricsJob?.cancel()
        lyricsJob = null
        super.onStop()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root.findViewById<View>(R.id.topBar)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerLyrics.apply {
            layoutManager = LinearLayoutManager(this@LyricsFullscreenActivity)
            adapter = lyricsAdapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    isUserScrollingLyrics = newState != RecyclerView.SCROLL_STATE_IDLE
                    if (!isUserScrollingLyrics) {
                        pendingCenterLyricIndex?.let { index ->
                            pendingCenterLyricIndex = null
                            centerLyricLine(index)
                        }
                    }
                }
            })
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            musicPlayer.state.collectLatest { state ->
                val currentItem = state.playlist.getOrNull(state.currentIndex)
                binding.tvSongTitle.text = currentItem?.title ?: "暂无播放"
                binding.tvArtist.text = currentItem?.artist ?: "未知艺术家"
                syncLyrics(currentItem?.id)
                updatePlaybackSummary(state.currentPosition, state.duration)
                loadBlurredCoverBackground(currentItem?.coverUrl)
            }
        }
    }

    private fun loadBlurredCoverBackground(coverUrl: String?) {
        if (coverUrl.isNullOrEmpty()) {
            showNoCoverBackground()
            return
        }

        lifecycleScope.launch {
            try {
                val request = ImageRequest.Builder(this@LyricsFullscreenActivity)
                    .data(coverUrl)
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        applyBlurredCoverWithPalette(bitmap)
                    } else {
                        showNoCoverBackground()
                    }
                } else {
                    showNoCoverBackground()
                }
            } catch (e: Exception) {
                showNoCoverBackground()
            }
        }
    }

    private suspend fun applyBlurredCoverWithPalette(bitmap: Bitmap) = withContext(Dispatchers.Main) {
        binding.ivBlurredCover.visibility = View.VISIBLE
        binding.viewGradientScrim.visibility = View.VISIBLE

        // Apply fast blur by scaling down then up
        val blurredBitmap = createBlurredBitmap(bitmap, 200, 200, 8)
        binding.ivBlurredCover.setImageBitmap(blurredBitmap)

        // Extract dominant color for gradient scrim tint
        val palette = Palette.from(bitmap).generate()
        val dominantColor = palette.getDominantColor(Color.parseColor("#FF1A1A2E"))
        val scrimColor = shiftColor(dominantColor, 0.25f)

        val scrimGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                (dominantColor and 0x00FFFFFF) or (0x99000000.toInt()),
                (scrimColor and 0x00FFFFFF) or (0xDD000000.toInt())
            )
        )
        binding.viewGradientScrim.background = scrimGradient
    }

    private fun showNoCoverBackground() {
        binding.ivBlurredCover.visibility = View.GONE
        binding.viewGradientScrim.visibility = View.VISIBLE
        binding.viewGradientScrim.background = noCoverGradient
    }

    /**
     * Create a fast blurred bitmap by scaling down, applying blur via multiple canvas draws,
     * then scaling back up.
     */
    private fun createBlurredBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        blurRadius: Int
    ): Bitmap {
        // Scale down first for efficiency
        val smallBitmap = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

        // Create a mutable bitmap for the blur result
        val blurred = createBitmap(targetWidth, targetHeight)
        val canvas = Canvas(blurred)

        // Draw the scaled bitmap multiple times with decreasing alpha to simulate blur
        val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }

        val iterations = blurRadius.coerceIn(1, 16)
        val alphaStep = (200 / iterations).coerceAtLeast(10)

        for (i in 0 until iterations) {
            paint.alpha = alphaStep
            canvas.drawBitmap(smallBitmap, 0f, 0f, paint)
        }

        // Scale up to target size with filtering for soft blur
        paint.isFilterBitmap = true
        paint.alpha = 255
        val result = createBitmap(targetWidth, targetHeight)
        val resultCanvas = Canvas(result)
        resultCanvas.drawBitmap(blurred, 0f, 0f, paint)

        return result
    }

    /**
     * Shift a color's brightness by the given factor.
     * factor < 1.0 darkens, factor > 1.0 lightens
     */
    private fun shiftColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun syncLyrics(songId: String?) {
        if (songId == currentLyricsSongId) return
        currentLyricsSongId = songId
        lyricsJob?.cancel()
        currentLyrics = emptyList()
        highlightedLyricIndex = -1
        lyricsAdapter.submitLyrics(emptyList(), -1)
        renderLyricsState(
            status = if (songId == null) "当前没有播放内容" else "正在加载歌词",
            emptyMessage = if (songId == null) "当前没有播放内容" else "歌词加载中"
        )
        if (songId == null) return

        lyricsJob = lifecycleScope.launch {
            val result = jellyfinClient.getLyrics(songId)
            currentLyrics = result?.lines.orEmpty()
            if (currentLyrics.isEmpty()) {
                renderLyricsState("当前歌曲没有可用歌词", "当前歌曲没有可用歌词")
            } else {
                lyricsAdapter.submitLyrics(currentLyrics, -1)
                binding.recyclerLyrics.scrollToPosition(0)
                updateLyricsPosition(musicPlayer.getState().currentPosition)
            }
        }
    }

    private fun updateLyricsPosition(positionMs: Long) {
        if (currentLyrics.isEmpty()) return
        val currentIndex = currentLyrics.indexOfLast { it.startMs <= positionMs }
        if (currentIndex < 0) {
            renderLyricsState("已加载 ${currentLyrics.size} 行歌词，可点击歌词跳转", "前奏中")
            lyricsAdapter.updateActiveLine(-1)
            highlightedLyricIndex = -1
            return
        }
        renderLyricsState("已加载 ${currentLyrics.size} 行歌词，可点击歌词跳转", null)
        lyricsAdapter.updateActiveLine(currentIndex)
        if (highlightedLyricIndex != currentIndex) {
            highlightedLyricIndex = currentIndex
            if (isUserScrollingLyrics) {
                pendingCenterLyricIndex = currentIndex
            } else {
                centerLyricLine(currentIndex)
            }
        }
    }

    private fun renderLyricsState(status: String, emptyMessage: String?) {
        binding.tvLyricsStatus.text = status
        binding.tvLyricsEmpty.text = emptyMessage ?: ""
        binding.tvLyricsEmpty.visibility = if (emptyMessage != null) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerLyrics.visibility = if (emptyMessage != null && currentLyrics.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun centerLyricLine(index: Int) {
        val layoutManager = binding.recyclerLyrics.layoutManager as? LinearLayoutManager ?: return
        val recyclerHeight = binding.recyclerLyrics.height
        if (recyclerHeight <= 0) {
            binding.recyclerLyrics.post { centerLyricLine(index) }
            return
        }
        layoutManager.scrollToPositionWithOffset(index, recyclerHeight / 2 - 72)
    }

    private fun updatePlaybackSummary(positionMs: Long, durationMs: Long) {
        binding.tvPlaybackTime.text = "${formatTime(positionMs)} / ${formatTime(durationMs)}"
    }

    private fun formatTime(positionMs: Long): String {
        if (positionMs <= 0L) return "00:00"
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}