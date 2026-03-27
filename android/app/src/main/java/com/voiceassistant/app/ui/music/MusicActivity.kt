package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ActivityMusicBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 音乐页面 - 整合 Jellyfin 音乐库和本地播放列表
 */
@AndroidEntryPoint
class MusicActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMusicBinding
    private val viewModel: MusicViewModel by viewModels()

    private val tabTitles = listOf("音乐库", "播放列表")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeState()

        // 初始化加载
        viewModel.loadSongs()
    }

    private fun setupViews() {
        // 返回按钮
        binding.btnBack.setOnClickListener { finish() }

        // 创建播放列表
        binding.btnAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        // 搜索
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString().trim()
                if (query.isNotEmpty()) {
                    viewModel.searchSongs(query)
                }
                true
            } else false
        }

        // ViewPager + TabLayout
        val adapter = MusicPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        // 底部播放控制
        binding.btnPlayPause.setOnClickListener {
            viewModel.togglePlayPause()
        }

        binding.btnPrev.setOnClickListener {
            viewModel.playPrevious()
        }

        binding.btnNext.setOnClickListener {
            viewModel.playNext()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // 加载状态
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                // 播放状态
                if (state.currentSong != null) {
                    binding.playerBar.visibility = View.VISIBLE
                    binding.tvSongTitle.text = state.currentSong.title
                    binding.tvArtist.text = state.currentSong.artist ?: "未知艺术家"

                    val playIcon = if (state.isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    }
                    binding.btnPlayPause.setImageResource(playIcon)
                } else {
                    binding.playerBar.visibility = View.GONE
                }

                // 错误处理
                state.error?.let { error ->
                    Toast.makeText(this@MusicActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val editText = EditText(this).apply {
            hint = "播放列表名称"
            setPadding(48, 32, 48, 32)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("创建播放列表")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createPlaylist(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
