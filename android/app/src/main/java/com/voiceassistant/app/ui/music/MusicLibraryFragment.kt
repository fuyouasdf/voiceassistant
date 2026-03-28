package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
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
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var folderAdapter: FolderAdapter

    private var currentCategory: MusicCategory = MusicCategory.ALBUMS

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

        setupAdapters()
        setupRecyclerView()
        setupChips()
        observeState()

        // 默认加载专辑
        viewModel.loadAlbums()
    }

    private fun setupAdapters() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                viewModel.playSong(song)
            },
            onAddToPlaylistClick = { song ->
                showAddToPlaylistDialog(song)
            }
        )

        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                // 点击专辑加载该专辑下的歌曲
                viewModel.loadAlbumSongs(album.id)
            }
        )

        folderAdapter = FolderAdapter(
            onItemClick = { item ->
                viewModel.onFolderItemClick(item)
            },
            getCoverUrl = { itemId ->
                viewModel.getCoverUrl(itemId)
            }
        )
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupChips() {
        // 默认选中专辑
        binding.chipAlbums.isChecked = true

        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(binding.chipSongs.id) -> {
                    if (currentCategory != MusicCategory.SONGS) {
                        currentCategory = MusicCategory.SONGS
                        viewModel.loadSongs()
                    }
                }
                checkedIds.contains(binding.chipAlbums.id) -> {
                    if (currentCategory != MusicCategory.ALBUMS) {
                        currentCategory = MusicCategory.ALBUMS
                        viewModel.loadAlbums()
                    }
                }
                checkedIds.contains(binding.chipArtists.id) -> {
                    if (currentCategory != MusicCategory.ARTISTS) {
                        currentCategory = MusicCategory.ARTISTS
                        viewModel.loadArtists()
                    }
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state.currentCategory) {
                    MusicCategory.SONGS -> showSongs(state)
                    MusicCategory.ALBUMS -> showAlbums(state)
                    MusicCategory.ARTISTS -> showArtists(state)
                    MusicCategory.FOLDER -> showFolder(state)
                }

                // 错误处理
                state.error?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSongs(state: MusicUiState) {
        // 切换到歌曲列表布局
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = songAdapter

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
            binding.tvEmpty.text = "暂无歌曲"
        }
    }

    private fun showAlbums(state: MusicUiState) {
        // 切换到网格布局显示专辑
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = albumAdapter

        if (state.albums.isNotEmpty()) {
            binding.recyclerView.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            albumAdapter.submitList(state.albums)
        } else if (!state.isLoading) {
            binding.recyclerView.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "暂无专辑"
        }
    }

    private fun showArtists(state: MusicUiState) {
        // 暂时使用歌曲列表布局显示艺术家
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = songAdapter

        // 艺术家没有专门的适配器，暂时显示提示
        if (state.artists.isEmpty() && !state.isLoading) {
            binding.recyclerView.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "暂无艺术家"
        }
    }

    private fun showFolder(state: MusicUiState) {
        // 文件夹内显示混合内容（歌曲、专辑、子文件夹）使用瀑布流布局
        if (state.folderItems.isNotEmpty()) {
            binding.recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            binding.recyclerView.adapter = folderAdapter
            binding.recyclerView.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            folderAdapter.submitList(state.folderItems)
        } else if (!state.isLoading) {
            binding.recyclerView.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "文件夹为空"
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