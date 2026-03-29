package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voiceassistant.app.databinding.ActivityPlaylistBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 播放列表页面
 * 用于显示和管理播放列表中的歌曲
 */
@AndroidEntryPoint
class PlaylistActivity : AppCompatActivity() {

    private val viewModel: PlaylistViewModel by viewModels()
    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var songAdapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get playlistId from intent and load playlist
        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        if (playlistId == -1L) {
            Toast.makeText(this, "无效的播放列表", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupRecyclerView()
        setupListeners()
        observeState()

        // Load playlist
        viewModel.setPlaylistId(playlistId)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        songAdapter = PlaylistAdapter(
            onSongClick = { song ->
                // TODO: 播放歌曲
                Toast.makeText(this, "播放: ${song.title}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { song ->
                showDeleteSongDialog(song.songId, song.title)
            }
        )
        binding.recyclerSongs.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = songAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDeletePlaylist.setOnClickListener {
            showDeletePlaylistDialog()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Loading
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                // Playlist info
                state.playlist?.let { playlist ->
                    binding.tvPlaylistName.text = playlist.name
                }

                // Songs
                songAdapter.submitList(state.songs)

                // Empty state
                val isEmpty = !state.isLoading && state.songs.isEmpty()
                binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerSongs.visibility = if (isEmpty) View.GONE else View.VISIBLE

                // Song count
                binding.tvSongCount.text = "${state.songs.size} 首歌曲"

                // Error
                state.error?.let { error ->
                    Toast.makeText(this@PlaylistActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun showDeleteSongDialog(songId: String, songTitle: String) {
        AlertDialog.Builder(this)
            .setTitle("移除歌曲")
            .setMessage("确定要从播放列表中移除「$songTitle」吗？")
            .setPositiveButton("移除") { _, _ ->
                viewModel.removeSong(songId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeletePlaylistDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除播放列表")
            .setMessage("确定要删除这个播放列表吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deletePlaylist()
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlistId"
    }
}
