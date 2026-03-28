package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voiceassistant.app.databinding.FragmentPlaylistDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 播放列表详情页面 - 显示播放列表中的歌曲
 */
@AndroidEntryPoint
class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter

    private var playlistId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistId = arguments?.getLong(ARG_PLAYLIST_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (playlistId == -1L) {
            parentFragmentManager.popBackStack()
            return
        }

        setupRecyclerView()
        setupListeners()
        observeState()

        // 加载播放列表详情
        viewModel.loadPlaylistDetail(playlistId)
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                // 播放歌曲
                viewModel.playSong(song)
            },
            onAddToPlaylistClick = { song ->
                // 显示添加到播放列表对话框（复用现有逻辑）
                showAddToPlaylistDialog(song)
            }
        )

        binding.recyclerViewSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
    }

    private fun setupListeners() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 播放全部 - 播放第一首
        binding.btnPlayAll.setOnClickListener {
            val songs = viewModel.uiState.value.currentPlaylistSongs
            if (songs.isNotEmpty()) {
                viewModel.playSong(songs.first())
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // 加载状态
                binding.progressBar.visibility = if (state.isLoadingPlaylistDetail) View.VISIBLE else View.GONE

                // 播放列表信息
                state.currentPlaylist?.let { playlist ->
                    binding.tvPlaylistName.text = playlist.name
                    val songCount = if (playlist.songIds.isEmpty()) 0 else playlist.songIds.split(",").size
                    binding.tvSongCount.text = "$songCount 首歌曲"
                }

                // 歌曲列表
                if (state.currentPlaylistSongs.isEmpty() && !state.isLoadingPlaylistDetail) {
                    binding.recyclerViewSongs.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                } else {
                    binding.recyclerViewSongs.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    songAdapter.submitList(state.currentPlaylistSongs)
                }
            }
        }
    }

    private fun showAddToPlaylistDialog(song: com.voiceassistant.data.remote.JellyfinSong) {
        // TODO: 实现添加到其他播放列表的功能
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PLAYLIST_ID = "playlist_id"

        /**
         * 创建播放列表详情 Fragment
         */
        fun newInstance(playlistId: Long): PlaylistDetailFragment {
            return PlaylistDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_PLAYLIST_ID, playlistId)
                }
            }
        }
    }
}