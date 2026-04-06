package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ActivityQueueBinding
import com.voiceassistant.core.music.MusicPlayer
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
            onDeleteClick = { entry ->
                musicPlayer.removeFromQueue(entry.index)
            }
        )
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
            val entries = queueAdapter.currentItems
            for (entry in entries.asReversed()) {
                musicPlayer.removeFromQueue(entry.index)
            }
        }
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
                binding.tvQueueSummary.text = if (entries.isEmpty()) {
                    "当前没有待播放歌曲"
                } else {
                    "共 ${entries.size} 首，当前第 ${state.currentIndex + 1} 首"
                }
                binding.tvQueueHint.text = if (entries.isEmpty()) {
                    "回到音乐列表选择歌曲后，这里会显示当前播放队列"
                } else {
                    "点击可立即切歌，长按条目可拖动排序，右侧可从当前队列移除"
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
