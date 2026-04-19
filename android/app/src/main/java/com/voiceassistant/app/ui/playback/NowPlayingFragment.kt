/*
 * Full playback panel fragment.
 */

package com.voiceassistant.app.ui.playback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.TimeBar
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.FragmentNowPlayingBinding
import com.voiceassistant.core.music.RepeatMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Full playback panel showing the current playing song with controls.
 */
@AndroidEntryPoint
class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val playbackViewModel: PlaybackViewModel by activityViewModels()

    private var isUserScrubbing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWindowInsets()
        setupListeners()
        observePlaybackState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply padding to topBar only, keeping the scroll view full screen
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayPause.setOnClickListener {
            playbackViewModel.togglePlaying()
        }

        binding.btnPrevious.setOnClickListener {
            playbackViewModel.prev()
        }

        binding.btnNext.setOnClickListener {
            playbackViewModel.next()
        }

        binding.btnShuffle.setOnClickListener {
            playbackViewModel.toggleShuffle()
        }

        binding.btnRepeat.setOnClickListener {
            playbackViewModel.toggleRepeat()
        }

        binding.btnPlaylist.setOnClickListener {
            // Open queue fragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, QueueFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.defaultTimeBar.addListener(
            object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    isUserScrubbing = true
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) {
                    binding.tvCurrentTime.text = formatTime(position)
                }

                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    isUserScrubbing = false
                    if (!canceled) {
                        playbackViewModel.seekToMs(position)
                    }
                }
            }
        )
    }

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playbackViewModel.song.collectLatest { song ->
                        binding.tvSongTitle.text = song?.title ?: "暂无播放"
                        binding.tvArtist.text = song?.artist ?: "未知艺术家"
                        binding.tvAlbum.text = song?.album ?: ""
                        binding.ivCover.load(song?.coverUrl) {
                            placeholder(R.drawable.ic_music)
                            error(R.drawable.ic_music)
                        }
                    }
                }

                launch {
                    playbackViewModel.isPlaying.collectLatest { isPlaying ->
                        binding.btnPlayPause.setImageResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                    }
                }

                launch {
                    playbackViewModel.positionDs.collectLatest { positionDs ->
                        if (!isUserScrubbing) {
                            val positionMs = positionDs * 100
                            binding.defaultTimeBar.setPosition(positionMs)
                            binding.tvCurrentTime.text = formatTime(positionMs)
                        }
                    }
                }

                launch {
                    playbackViewModel.durationDs.collectLatest { durationDs ->
                        if (durationDs > 0) {
                            binding.defaultTimeBar.setDuration(durationDs * 100)
                            binding.tvTotalTime.text = formatTime(durationDs * 100)
                        }
                    }
                }

                launch {
                    playbackViewModel.isShuffled.collectLatest { isShuffled ->
                        binding.btnShuffle.alpha = if (isShuffled) 1f else 0.5f
                        binding.btnShuffle.setColorFilter(
                            if (isShuffled) {
                                requireContext().getColor(R.color.primary)
                            } else {
                                requireContext().getColor(R.color.text_secondary)
                            }
                        )
                    }
                }

                launch {
                    playbackViewModel.repeatMode.collectLatest { mode ->
                        updateRepeatButton(mode)
                    }
                }
            }
        }
    }

    private fun updateRepeatButton(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.alpha = 0.5f
            }
            RepeatMode.ALL -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.alpha = 1f
                binding.btnRepeat.setColorFilter(requireContext().getColor(R.color.primary))
            }
            RepeatMode.ONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                binding.btnRepeat.alpha = 1f
                binding.btnRepeat.setColorFilter(requireContext().getColor(R.color.primary))
            }
        }
    }

    private fun formatTime(positionMs: Long): String {
        if (positionMs <= 0) return "00:00"
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
