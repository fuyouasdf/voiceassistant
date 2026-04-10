/*
 * Queue adapter for displaying playback queue.
 */

package com.voiceassistant.app.ui.playback

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ItemQueueSongBinding
import com.voiceassistant.core.music.MusicItem

class QueueAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onRemoveClick: (Int) -> Unit
) : ListAdapter<MusicItem, QueueAdapter.ViewHolder>(DiffCallback()) {

    private var currentIndex: Int = -1

    fun setCurrentIndex(index: Int) {
        val oldIndex = currentIndex
        currentIndex = index
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == currentIndex)
    }

    inner class ViewHolder(
        private val binding: ItemQueueSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(position)
                }
            }

            binding.btnMore.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRemoveClick(position)
                }
            }
        }

        fun bind(item: MusicItem, isPlaying: Boolean) {
            binding.tvTitle.text = item.title
            binding.tvArtist.text = item.artist ?: "未知艺术家"
            binding.tvOrder.text = (adapterPosition + 1).toString().padStart(2, '0')
            binding.ivCover.load(item.coverUrl) {
                placeholder(R.drawable.ic_music)
                error(R.drawable.ic_music)
            }

            // Highlight currently playing item
            binding.root.alpha = if (isPlaying) 1f else 0.7f
            binding.tvPlayingBadge.visibility = if (isPlaying) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MusicItem>() {
        override fun areItemsTheSame(oldItem: MusicItem, newItem: MusicItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MusicItem, newItem: MusicItem): Boolean {
            return oldItem == newItem
        }
    }
}
