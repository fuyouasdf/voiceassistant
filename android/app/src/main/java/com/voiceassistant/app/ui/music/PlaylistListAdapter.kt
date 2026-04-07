package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ItemPlaylistBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlaylistListAdapter(
    private val onPlaylistClick: (PlaylistListItem) -> Unit
) : ListAdapter<PlaylistListItem, PlaylistListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlaylistListItem) {
            binding.tvTitle.text = item.playlist.name
            binding.tvSubtitle.text = "${item.songCount} 首歌曲"
            binding.tvUpdatedAt.text = "更新于 ${formatTime(item.playlist.updatedAt)}"
            binding.root.setOnClickListener {
                onPlaylistClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PlaylistListItem>() {
        override fun areItemsTheSame(oldItem: PlaylistListItem, newItem: PlaylistListItem): Boolean {
            return oldItem.playlist.id == newItem.playlist.id
        }

        override fun areContentsTheSame(oldItem: PlaylistListItem, newItem: PlaylistListItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private val timeFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        private fun formatTime(timestamp: Long): String {
            return timeFormatter.format(Date(timestamp))
        }
    }
}
