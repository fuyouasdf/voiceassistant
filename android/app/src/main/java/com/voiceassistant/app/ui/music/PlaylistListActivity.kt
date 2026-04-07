package com.voiceassistant.app.ui.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.voiceassistant.app.databinding.ActivityPlaylistListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaylistListActivity : AppCompatActivity() {

    private val viewModel: PlaylistListViewModel by viewModels()
    private lateinit var binding: ActivityPlaylistListBinding
    private lateinit var playlistAdapter: PlaylistListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistListAdapter { item ->
            startActivity(
                Intent(this, PlaylistActivity::class.java).apply {
                    putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, item.playlist.id)
                }
            )
        }
        binding.recyclerPlaylists.apply {
            layoutManager = LinearLayoutManager(this@PlaylistListActivity)
            adapter = playlistAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCreatePlaylist.setOnClickListener { showCreatePlaylistDialog() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                playlistAdapter.submitList(state.playlists)

                val isEmpty = !state.isLoading && state.playlists.isEmpty()
                binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerPlaylists.visibility = if (isEmpty) View.GONE else View.VISIBLE
                binding.tvPlaylistCount.text = "${state.playlists.size} 个播放列表"

                state.error?.let { error ->
                    Toast.makeText(this@PlaylistListActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val editText = EditText(this).apply {
            hint = "播放列表名称"
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle("新建播放列表")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.createPlaylist(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
