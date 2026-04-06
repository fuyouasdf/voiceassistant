package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ItemQueueSongBinding
import com.voiceassistant.core.music.MusicItem

data class QueueEntry(
    val item: MusicItem,
    val index: Int,
    val isCurrent: Boolean
)

/**
 * 队列项操作类型
 */
enum class QueueAction {
    PLAY_NOW,          // 立即播放
    PLAY_NEXT,         // 下一首播放
    MOVE_UP,           // 上移
    MOVE_DOWN,         // 下移
    REMOVE             // 移除
}

class QueueAdapter(
    private val onSongClick: (QueueEntry) -> Unit,
    private val onMoveUpClick: (QueueEntry) -> Unit,
    private val onMoveDownClick: (QueueEntry) -> Unit,
    private val onMoreClick: (QueueEntry) -> Unit
) : RecyclerView.Adapter<QueueAdapter.ViewHolder>() {

    private val items = mutableListOf<QueueEntry>()

    val currentItems: List<QueueEntry>
        get() = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun submitList(entries: List<QueueEntry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in items.indices || toPosition !in items.indices) {
            return false
        }
        if (fromPosition == toPosition) {
            return true
        }
        val movedItem = items.removeAt(fromPosition)
        items.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun getItem(position: Int): QueueEntry = items[position]

    inner class ViewHolder(
        private val binding: ItemQueueSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: QueueEntry) {
            val context = binding.root.context
            binding.tvTitle.text = entry.item.title
            binding.tvArtist.text = entry.item.artist ?: "未知艺术家"
            binding.tvDuration.text = formatDuration(entry.item.duration)
            binding.tvOrder.text = String.format("%02d", entry.index + 1)
            binding.tvPlayingBadge.text = if (entry.isCurrent) "正在播放" else "队列中"
            binding.tvPlayingBadge.backgroundTintList = ContextCompat.getColorStateList(
                context,
                if (entry.isCurrent) R.color.primary else R.color.surface_elevated
            )
            binding.tvPlayingBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (entry.isCurrent) R.color.on_primary else R.color.text_secondary
                )
            )
            binding.root.alpha = if (entry.isCurrent) 1f else 0.9f
            binding.ivCover.load(entry.item.coverUrl) {
                placeholder(R.drawable.ic_music)
                error(R.drawable.ic_music)
            }

            binding.root.setOnClickListener { onSongClick(entry) }
            binding.btnMoveUp.setOnClickListener { onMoveUpClick(entry) }
            binding.btnMoveDown.setOnClickListener { onMoveDownClick(entry) }
            binding.btnMore.setOnClickListener { onMoreClick(entry) }

            // 当前歌曲禁用上移/下移按钮
            binding.btnMoveUp.isEnabled = !entry.isCurrent
            binding.btnMoveUp.alpha = if (entry.isCurrent) 0.3f else 1f
            binding.btnMoveDown.isEnabled = !entry.isCurrent
            binding.btnMoveDown.alpha = if (entry.isCurrent) 0.3f else 1f
        }

        private fun formatDuration(seconds: Int): String {
            val minutes = seconds / 60
            val secs = seconds % 60
            return String.format("%d:%02d", minutes, secs)
        }
    }
}
