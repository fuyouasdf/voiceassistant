package com.voiceassistant.app.ui.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.tabs.TabLayoutMediator
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ActivityMusicBinding
import com.voiceassistant.app.ui.settings.SettingsActivity
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

        // 默认加载专辑列表
        viewModel.loadAlbums()
    }

    private fun setupViews() {
        // 返回按钮 - 返回上一级，不要直接退出
        binding.btnBack.setOnClickListener {
            if (!viewModel.navigateBack()) {
                finish()
            }
        }

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

        // 播放控制按钮 - 点击也跳转到正在播放页面
        binding.btnPlayPause.setOnClickListener {
            viewModel.togglePlayPause()
            openNowPlayingFragment()
        }

        binding.btnPrev.setOnClickListener {
            viewModel.playPrevious()
            openNowPlayingFragment()
        }

        binding.btnNext.setOnClickListener {
            viewModel.playNext()
            openNowPlayingFragment()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // 检查是否需要配置 Jellyfin
                if (state.needsJellyfinConfig) {
                    Toast.makeText(this@MusicActivity, "请先配置 Jellyfin", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@MusicActivity, SettingsActivity::class.java))
                    finish()
                    return@collectLatest
                }

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

                    // 加载封面
                    state.currentSong.coverUrl?.let { url ->
                        binding.ivCover.load(url) {
                            crossfade(true)
                            placeholder(R.drawable.ic_music)
                            error(R.drawable.ic_music)
                        }
                    } ?: binding.ivCover.setImageResource(R.drawable.ic_music)
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

    /**
     * 打开正在播放页面
     */
    private fun openNowPlayingFragment() {
        val fragment = NowPlayingFragment()
        supportFragmentManager.commit {
            setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            add(android.R.id.content, fragment)
            addToBackStack(null)
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
