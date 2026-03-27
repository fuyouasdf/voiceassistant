package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.transform.RoundedCornersTransformation
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.FragmentNowPlayingBinding
import com.voiceassistant.core.music.MusicItem
import com.voiceassistant.core.music.MusicPlayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 正在播放页面 - 显示当前播放歌曲的详细信息和播放控制
 */
@AndroidEntryPoint
class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var musicPlayer: MusicPlayer

    private val handler = Handler(Looper.getMainLooper())
    private var isTrackingTouch = false

    // 进度更新 Runnable
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (!isTrackingTouch) {
                updateProgress()
            }
            handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
        }
    }

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
        setupViews()
        observePlayerState()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateProgressRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateProgressRunnable)
    }

    private fun setupViews() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 播放控制
        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.btnPrevious.setOnClickListener {
            musicPlayer.playPrevious()
        }

        binding.btnNext.setOnClickListener {
            musicPlayer.playNext()
        }

        // 进度条拖动
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val state = musicPlayer.getState()
                    val position = (progress.toLong() * state.duration) / 100
                    binding.tvCurrentTime.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isTrackingTouch = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isTrackingTouch = false
                seekBar?.let {
                    val state = musicPlayer.getState()
                    val position = (it.progress.toLong() * state.duration) / 100
                    musicPlayer.seekTo(position)
                }
            }
        })
    }

    private fun observePlayerState() {
        // 观察播放状态变化
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicPlayer.state.collect { state ->
                    updateUI(state.playlist.getOrNull(state.currentIndex), state.isPlaying)
                }
            }
        }
    }

    private fun updateUI(item: MusicItem?, isPlaying: Boolean) {
        if (item == null) {
            binding.tvSongTitle.text = "未播放"
            binding.tvArtist.text = ""
            binding.tvAlbum.text = ""
            binding.ivCover.setImageResource(R.drawable.ic_music)
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        // 更新歌曲信息
        binding.tvSongTitle.text = item.title
        binding.tvArtist.text = item.artist ?: "未知艺术家"
        binding.tvAlbum.text = item.album ?: ""

        // 加载封面
        item.coverUrl?.let { url ->
            binding.ivCover.load(url) {
                crossfade(true)
                placeholder(R.drawable.ic_music)
                error(R.drawable.ic_music)
                transformations(RoundedCornersTransformation(16f))
            }
        } ?: binding.ivCover.setImageResource(R.drawable.ic_music)

        // 更新播放按钮图标
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        binding.btnPlayPause.setImageResource(playPauseIcon)
    }

    private fun updateProgress() {
        val state = musicPlayer.getState()
        if (state.duration > 0) {
            val progress = ((state.currentPosition * 100) / state.duration).toInt()
            binding.seekBar.progress = progress
            binding.tvCurrentTime.text = formatTime(state.currentPosition)
            binding.tvTotalTime.text = formatTime(state.duration)
        } else {
            binding.seekBar.progress = 0
            binding.tvCurrentTime.text = formatTime(0)
            binding.tvTotalTime.text = formatTime(0)
        }
    }

    private fun togglePlayPause() {
        val state = musicPlayer.state.value
        if (state.isPlaying) {
            musicPlayer.pause()
        } else {
            musicPlayer.resume()
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateProgressRunnable)
        _binding = null
    }

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 500L
    }
}
