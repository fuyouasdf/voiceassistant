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
import com.voiceassistant.app.databinding.FragmentPlaylistsBinding
import com.voiceassistant.data.local.PlaylistEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 播放列表 Fragment
 */
@AndroidEntryPoint
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var playlistAdapter: PlaylistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeState()
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            onPlaylistClick = { playlist ->
                // TODO: 跳转到播放列表详情页面
                Toast.makeText(requireContext(), "播放列表: ${playlist.name}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { playlist ->
                showDeleteConfirmation(playlist)
            }
        )

        binding.recyclerViewPlaylists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playlistAdapter
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state.playlists.isEmpty()) {
                    binding.recyclerViewPlaylists.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                } else {
                    binding.recyclerViewPlaylists.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    playlistAdapter.submitList(state.playlists)
                }
            }
        }
    }

    private fun showDeleteConfirmation(playlist: PlaylistEntity) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除播放列表")
            .setMessage("确定要删除 \"${playlist.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deletePlaylist(playlist.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
