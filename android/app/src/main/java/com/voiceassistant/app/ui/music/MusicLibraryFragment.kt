package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voiceassistant.app.databinding.FragmentMusicLibraryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 音乐库 Fragment - 显示歌曲、专辑、艺术家
 */
@AndroidEntryPoint
class MusicLibraryFragment : Fragment() {

    private var _binding: FragmentMusicLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChips()
        observeState()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                viewModel.playSong(song)
            },
            onAddToPlaylistClick = { song ->
                showAddToPlaylistDialog(song)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
    }

    private fun setupChips() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(binding.chipSongs.id) -> viewModel.loadSongs()
                checkedIds.contains(binding.chipAlbums.id) -> viewModel.loadAlbums()
                checkedIds.contains(binding.chipArtists.id) -> viewModel.loadArtists()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // 显示歌曲列表
                val songsToShow = if (state.searchResults.isNotEmpty()) {
                    state.searchResults
                } else {
                    state.songs
                }

                if (songsToShow.isNotEmpty()) {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE
                    songAdapter.submitList(songsToShow)
                } else if (!state.isLoading) {
                    binding.recyclerView.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                }

                // 错误处理
                state.error?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddToPlaylistDialog(song: com.voiceassistant.data.remote.JellyfinSong) {
        val playlists = viewModel.uiState.value.playlists
        if (playlists.isEmpty()) {
            Toast.makeText(requireContext(), "请先创建播放列表", Toast.LENGTH_SHORT).show()
            return
        }

        val playlistNames = playlists.map { it.name }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加到播放列表")
            .setItems(playlistNames) { _, which ->
                val playlist = playlists[which]
                viewModel.addToPlaylist(playlist.id, song)
                Toast.makeText(requireContext(), "已添加到 ${playlist.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
