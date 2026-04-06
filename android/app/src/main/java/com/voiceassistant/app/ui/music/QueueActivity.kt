package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ActivityQueueBinding
import com.voiceassistant.core.music.MusicPlayer
import com.voiceassistant.core.music.QueueSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QueueActivity : AppCompatActivity() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    private lateinit var binding: ActivityQueueBinding
    private val queueAdapter by lazy {
        QueueAdapter(
            onSongClick = { entry ->
                musicPlayer.seekToIndex(entry.index)
                finish()
            },
            onMoveUpClick = { entry ->
                val currentIndex = entry.index
                if (currentIndex > 0) {
                    musicPlayer.moveQueueItem(currentIndex, currentIndex - 1)
                }
            },
            onMoveDownClick = { entry ->
                val currentIndex = entry.index
                musicPlayer.moveQueueItem(currentIndex, currentIndex + 1)
            },
            onMoreClick = { entry ->
                showMoreActionsMenu(entry)
            }
        )
    }

    private fun showMoreActionsMenu(entry: QueueEntry) {
        val view = binding.recyclerQueue.findViewHolderForAdapterPosition(entry.index)?.itemView
            ?: return
        val popup = PopupMenu(this, view.findViewById(R.id.btnMore))
        popup.menuInflater.inflate(R.menu.menu_queue_more, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_play_now -> {
                    musicPlayer.seekToIndex(entry.index)
                    finish()
                    true
                }
                R.id.action_play_next -> {
                    // 将当前项移动到当前播放项的下一首
                    val currentIndex = musicPlayer.state.value.currentIndex
                    if (currentIndex >= 0 && entry.index > currentIndex) {
                        // 如果插入位置在当前项之后，需要调整
                        musicPlayer.moveQueueItem(entry.index, currentIndex + 1)
                    } else if (entry.index < currentIndex) {
                        musicPlayer.moveQueueItem(entry.index, currentIndex)
                    }
                    true
                }
                R.id.action_remove -> {
                    musicPlayer.removeFromQueue(entry.index)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    private var isDragging = false
    private var dragFromIndex = RecyclerView.NO_POSITION
    private var dragToIndex = RecyclerView.NO_POSITION
    private val itemTouchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                isDragging = true
                if (dragFromIndex == RecyclerView.NO_POSITION) {
                    dragFromIndex = viewHolder.bindingAdapterPosition
                }
                dragToIndex = target.bindingAdapterPosition
                return queueAdapter.moveItem(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition
                )
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (!isDragging) return
                isDragging = false
                syncQueueOrderWithPlayer()
                dragFromIndex = RecyclerView.NO_POSITION
                dragToIndex = RecyclerView.NO_POSITION
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupInsets()
        setupRecyclerView()
        setupListeners()
        observeQueue()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerQueue.apply {
            layoutManager = LinearLayoutManager(this@QueueActivity)
            adapter = queueAdapter
        }
        itemTouchHelper.attachToRecyclerView(binding.recyclerQueue)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnClearQueue.setOnClickListener {
            showClearQueueConfirmation()
        }
    }

    private fun showClearQueueConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("清空队列")
            .setMessage("确定要清空当前播放队列吗？")
            .setPositiveButton("清空") { _, _ ->
                val entries = queueAdapter.currentItems
                for (entry in entries.asReversed()) {
                    musicPlayer.removeFromQueue(entry.index)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun observeQueue() {
        lifecycleScope.launch {
            musicPlayer.state.collectLatest { state ->
                if (isDragging) {
                    return@collectLatest
                }
                val entries = state.playlist.mapIndexed { index, item ->
                    QueueEntry(
                        item = item,
                        index = index,
                        isCurrent = index == state.currentIndex
                    )
                }
                queueAdapter.submitList(entries)

                // 显示队列来源
                val sourceText = when (state.queueSource) {
                    QueueSource.BROWSER -> "来自浏览页"
                    QueueSource.PLAYLIST -> "来自播放列表"
                    QueueSource.LOCAL -> "来自本地歌曲"
                    QueueSource.UNKNOWN -> ""
                }

                binding.tvQueueSummary.text = if (entries.isEmpty()) {
                    "当前没有待播放歌曲"
                } else {
                    buildString {
                        append("共 ${entries.size} 首")
                        if (state.currentIndex >= 0) {
                            append("，当前第 ${state.currentIndex + 1} 首")
                        }
                        if (sourceText.isNotEmpty()) {
                            append(" · $sourceText")
                        }
                    }
                }
                binding.tvQueueHint.text = if (entries.isEmpty()) {
                    "回到音乐列表选择歌曲后，这里会显示当前播放队列"
                } else {
                    "点击可立即切歌，长按条目可拖动排序，右侧按钮可调整顺序或移除"
                }
                binding.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerQueue.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
                binding.btnClearQueue.isEnabled = entries.isNotEmpty()
                binding.btnClearQueue.alpha = if (entries.isNotEmpty()) 1f else 0.4f
            }
        }
    }

    private fun syncQueueOrderWithPlayer() {
        if (dragFromIndex == RecyclerView.NO_POSITION || dragToIndex == RecyclerView.NO_POSITION) {
            return
        }
        musicPlayer.moveQueueItem(dragFromIndex, dragToIndex)
    }
}
