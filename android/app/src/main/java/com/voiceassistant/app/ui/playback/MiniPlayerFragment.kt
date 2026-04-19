/*
 * Copyright (c) 2024 Auxio Project
 * PlaybackBarFragment.kt is part of Auxio.
 * Adapted for Voice Assistant project.
 */

package com.voiceassistant.app.ui.playback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.FragmentMiniPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Mini player bar shown at the bottom of the screen.
 */
@AndroidEntryPoint
class MiniPlayerFragment : Fragment() {

    private var _binding: FragmentMiniPlayerBinding? = null
    private val binding get() = _binding!!

    private val playbackViewModel: PlaybackViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiniPlayerBinding.inflate(inflater, container, false)
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
            val params = v.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom
            v.layoutParams = params
            insets
        }
    }

    private fun setupListeners() {
        binding.root.setOnClickListener {
            // Open full playback panel
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NowPlayingFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnPlayPause.setOnClickListener {
            playbackViewModel.togglePlaying()
        }

        binding.btnNext.setOnClickListener {
            playbackViewModel.next()
        }

        binding.btnTempPlaylist.setOnClickListener {
            playbackViewModel.exitTempPlaylist()
        }
    }

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playbackViewModel.song.collectLatest { song ->
                        binding.tvSongTitle.text = song?.title ?: "暂无播放"
                        binding.tvArtist.text = song?.artist ?: binding.root.context.getString(R.string.artist_unknown)
                        binding.ivCover.load(song?.coverUrl) {
                            placeholder(R.drawable.ic_music)
                            error(R.drawable.ic_music)
                        }
                        binding.rootLayout.visibility =
                            if (song != null) View.VISIBLE else View.GONE
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
                        val durationDs = playbackViewModel.durationDs.value
                        if (durationDs > 0) {
                            // Progress bar update - will be added when layout is finalized
                        }
                    }
                }

                launch {
                    playbackViewModel.isTempPlaylistActive.collectLatest { isTemp ->
                        binding.btnTempPlaylist.visibility = if (isTemp) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }
}
