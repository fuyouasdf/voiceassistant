/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.app.ui.music

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.FragmentNowPlayingBinding
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.RepeatMode
import com.voiceassistant.domain.model.LyricLine
import com.voiceassistant.domain.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NowPlayingActivity : AppCompatActivity() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    @Inject
    lateinit var musicRepository: MusicRepository

    private lateinit var binding: FragmentNowPlayingBinding
    private var progressJob: Job? = null
    private var isUserScrubbing = false
    private var lyricsJob: Job? = null
    private var currentLyricsSongId: String? = null
    private var currentLyrics: List<LyricLine> = emptyList()
    private var highlightedLyricIndex: Int = -1
    private var isUserScrollingLyrics = false
    private var pendingCenterLyricIndex: Int? = null
    private val lyricsAdapter = LyricsAdapter { line ->
        musicPlayer.seekTo(line.startMs)
        updateLyricsPosition(line.startMs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupLyricsList()
        setupListeners()
        observePlayerState()
    }

    override fun onStart() {
        super.onStart()
        startProgressUpdates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-observe player state to ensure UI is up-to-date
        // The collectLatest in observePlayerState will handle updates
    }

    private fun setupLyricsList() {
        binding.recyclerLyrics.apply {
            layoutManager = LinearLayoutManager(this@NowPlayingActivity)
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

    override fun onStop() {
        progressJob?.cancel()
        progressJob = null
        lyricsJob?.cancel()
        lyricsJob = null
        super.onStop()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showMoreMenu() }

        binding.btnPlayPause.setOnClickListener {
            if (musicPlayer.getState().isPlaying) {
                musicPlayer.pause()
            } else {
                musicPlayer.resume()
            }
        }
        binding.btnPrevious.setOnClickListener { musicPlayer.playPrevious() }
        binding.btnNext.setOnClickListener { musicPlayer.playNext() }
        binding.btnFavorite.setOnClickListener {
            val isFavorite = musicPlayer.toggleFavorite()
            updateFavoriteButton(isFavorite)
        }
        binding.btnShuffle.setOnClickListener {
            val enabled = musicPlayer.toggleShuffle()
            updateShuffleButton(enabled)
        }
        binding.btnRepeat.setOnClickListener {
            val mode = musicPlayer.toggleRepeat()
            updateRepeatButton(mode)
        }
        binding.btnPlaylist.setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }
        binding.lyricsCard.setOnClickListener {
            startActivity(Intent(this, LyricsFullscreenActivity::class.java))
        }

        binding.defaultTimeBar.addListener(
            object : androidx.media3.ui.TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    isUserScrubbing = true
                }

                override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    binding.tvCurrentTime.text = formatTime(position)
                }

                override fun onScrubStop(
                    timeBar: androidx.media3.ui.TimeBar,
                    position: Long,
                    canceled: Boolean
                ) {
                    isUserScrubbing = false
                    if (!canceled) {
                        musicPlayer.seekTo(position)
                    }
                }
            }
        )
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            musicPlayer.state.collectLatest { state ->
                val currentItem = state.playlist.getOrNull(state.currentIndex)
                binding.tvSongTitle.text = currentItem?.title ?: getString(R.string.no_playback)
                binding.tvArtist.text = currentItem?.artist ?: getString(R.string.artist_unknown)
                binding.tvAlbum.text = currentItem?.album ?: ""
                binding.tvPlaybackSummary.text = buildPlaybackSummary(state.isPlaying, currentItem != null)
                binding.tvQueueInfo.text = buildQueueInfo(state.currentIndex, state.playlist.size)
                binding.tvStreamStatus.text = buildStreamStatus(currentItem)
                binding.btnPlayPause.setImageResource(
                    if (state.isPlaying) R.drawable.ic_pause
                    else R.drawable.ic_play
                )
                applyButtonState(binding.btnPrevious, state.playlist.isNotEmpty())
                applyButtonState(binding.btnNext, state.playlist.isNotEmpty())
                applyButtonState(binding.btnPlayPause, state.playlist.isNotEmpty())
                applyButtonState(binding.btnShuffle, state.playlist.size > 1)
                applyButtonState(binding.btnRepeat, state.playlist.isNotEmpty())
                applyButtonState(binding.btnPlaylist, state.playlist.isNotEmpty())
                applyButtonState(binding.btnFavorite, state.playlist.isNotEmpty())
                binding.defaultTimeBar.isEnabled = state.duration > 0
                binding.ivCover.load(currentItem?.coverUrl) {
                    placeholder(R.drawable.ic_music)
                    error(R.drawable.ic_music)
                }
                syncLyrics(currentItem?.id)
                binding.tvTotalTime.text = formatTime(state.duration)
                if (!isUserScrubbing) {
                    binding.defaultTimeBar.setDuration(state.duration)
                    binding.defaultTimeBar.setPosition(state.currentPosition)
                    binding.tvCurrentTime.text = formatTime(state.currentPosition)
                    updateLyricsPosition(state.currentPosition)
                }
                updateFavoriteButton(musicPlayer.isFavorite())
                updateShuffleButton(state.isShuffleEnabled)
                updateRepeatButton(state.repeatMode)
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (true) {
                val state = musicPlayer.getState()
                if (!isUserScrubbing) {
                    binding.defaultTimeBar.setDuration(state.duration)
                    binding.defaultTimeBar.setPosition(state.currentPosition)
                    binding.tvCurrentTime.text = formatTime(state.currentPosition)
                    binding.tvTotalTime.text = formatTime(state.duration)
                    updateLyricsPosition(state.currentPosition)
                }
                delay(500)
            }
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        binding.btnFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled
            else R.drawable.ic_favorite_outline
        )
        binding.btnFavorite.setColorFilter(
            ContextCompat.getColor(
                this,
                if (isFavorite) R.color.primary else R.color.text_secondary
            )
        )
    }

    private fun updateShuffleButton(enabled: Boolean) {
        // Change icon AND color for active state
        binding.btnShuffle.setImageResource(
            if (enabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        )
        binding.btnShuffle.setColorFilter(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.primary else R.color.text_secondary
            )
        )
        binding.btnShuffle.alpha = if (enabled) 1f else 0.72f
    }

    private fun updateRepeatButton(mode: RepeatMode) {
        // Change icon AND color for each mode
        when (mode) {
            RepeatMode.OFF -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnRepeat.alpha = 0.72f
            }
            RepeatMode.ALL -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                binding.btnRepeat.alpha = 1f
            }
            RepeatMode.ONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                binding.btnRepeat.alpha = 1f
            }
        }
        binding.btnRepeat.contentDescription = when (mode) {
            RepeatMode.OFF -> getString(R.string.repeat_off)
            RepeatMode.ALL -> getString(R.string.repeat_all)
            RepeatMode.ONE -> getString(R.string.repeat_one)
        }
    }

    private fun showMoreMenu() {
        val state = musicPlayer.getState()
        val currentItem = state.playlist.getOrNull(state.currentIndex)
        val options = arrayOf(
            getString(R.string.view_stream_info),
            getString(R.string.open_jellyfin_browse),
            getString(R.string.fullscreen_lyrics),
            getString(R.string.stop_playback)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.more_options)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showStreamInfoDialog(currentItem)
                    1 -> startActivity(Intent(this, JellyfinBrowseActivity::class.java))
                    2 -> startActivity(Intent(this, LyricsFullscreenActivity::class.java))
                    3 -> {
                        musicPlayer.stop()
                        finish()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showStreamInfoDialog(currentItem: com.voiceassistant.core.music.MusicItem?) {
        if (currentItem == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.stream_info_title)
                .setMessage(R.string.stream_info_no_content)
                .setPositiveButton(R.string.btn_ok, null)
                .show()
            return
        }

        val message = buildString {
            appendLine("${getString(R.string.label_title)}: ${currentItem.title}")
            appendLine("${getString(R.string.label_artist)}: ${currentItem.artist ?: getString(R.string.artist_unknown)}")
            appendLine("${getString(R.string.label_container)}: ${currentItem.streamContainer ?: getString(R.string.unknown)}")
            appendLine("${getString(R.string.label_playback_method)}: ${currentItem.streamPlayMethod ?: getString(R.string.unknown)}")
            appendLine("${getString(R.string.label_transcoding)}: ${if (currentItem.isTranscoding) getString(R.string.yes) else getString(R.string.no)}")
            appendLine("${getString(R.string.label_session_id)}: ${currentItem.playbackSessionId ?: getString(R.string.none)}")
            append("${getString(R.string.label_media_source_id)}: ${currentItem.mediaSourceId ?: getString(R.string.none)}")
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.stream_info_title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    private fun buildStreamStatus(currentItem: com.voiceassistant.core.music.MusicItem?): String {
        if (currentItem == null) {
            return getString(R.string.no_playback_content)
        }

        val container = currentItem.streamContainer?.uppercase() ?: getString(R.string.unknown)
        return if (currentItem.isTranscoding) {
            getString(R.string.transcoding_to_aac, container)
        } else {
            val method = currentItem.streamPlayMethod ?: "DIRECT"
            getString(R.string.direct_playback_with_method, container, method)
        }
    }

    private fun buildPlaybackSummary(isPlaying: Boolean, hasCurrentItem: Boolean): String {
        return when {
            !hasCurrentItem -> getString(R.string.waiting_for_playback)
            isPlaying -> getString(R.string.local_playing)
            else -> getString(R.string.state_paused)
        }
    }

    private fun syncLyrics(songId: String?) {
        if (songId == currentLyricsSongId) return
        currentLyricsSongId = songId
        lyricsJob?.cancel()
        currentLyrics = emptyList()
        highlightedLyricIndex = -1
        lyricsAdapter.submitLyrics(emptyList(), -1)
        renderLyricsState(
            status = if (songId == null) getString(R.string.no_playback_content) else getString(R.string.currently_loading_lyrics),
            emptyMessage = if (songId == null) getString(R.string.no_playback_content) else getString(R.string.lyrics_loading)
        )
        if (songId == null) return

        lyricsJob = lifecycleScope.launch {
            val result = musicRepository.getLyrics(songId)
            currentLyrics = result.getOrNull()?.lines.orEmpty()
            if (currentLyrics.isEmpty()) {
                lyricsAdapter.submitLyrics(emptyList(), -1)
                renderLyricsState(
                    status = getString(R.string.no_lyrics_available),
                    emptyMessage = getString(R.string.no_lyrics_available)
                )
            } else {
                lyricsAdapter.submitLyrics(currentLyrics, -1)
                binding.recyclerLyrics.scrollToPosition(0)
                updateLyricsPosition(musicPlayer.getState().currentPosition)
            }
        }
    }

    private fun updateLyricsPosition(positionMs: Long) {
        if (currentLyrics.isEmpty()) {
            return
        }
        val currentIndex = currentLyrics.indexOfLast { it.startMs <= positionMs }
        if (currentIndex < 0) {
            renderLyricsState(
                status = getString(R.string.lyrics_loaded, currentLyrics.size),
                emptyMessage = getString(R.string.intro)
            )
            lyricsAdapter.updateActiveLine(-1)
            highlightedLyricIndex = -1
            return
        }
        renderLyricsState(
            status = getString(R.string.lyrics_loaded, currentLyrics.size),
            emptyMessage = null
        )
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
        layoutManager.scrollToPositionWithOffset(index, recyclerHeight / 2 - 48)
    }

    private fun buildQueueInfo(currentIndex: Int, total: Int): String {
        if (total <= 0) {
            return getString(R.string.queue_empty)
        }
        return getString(R.string.queue_position, currentIndex + 1, total)
    }

    private fun applyButtonState(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.38f
    }

    private fun formatTime(positionMs: Long): String {
        if (positionMs <= 0L) return "00:00"
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
