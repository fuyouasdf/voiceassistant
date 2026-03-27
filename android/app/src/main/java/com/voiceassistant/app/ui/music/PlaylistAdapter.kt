package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ItemPlaylistBinding
import com.voiceassistant.data.local.PlaylistEntity

/**
 * 播放列表适配器
 */
class PlaylistAdapter(
    private val onPlaylistClick: (PlaylistEntity) -> Unit,
    private val onDeleteClick: (PlaylistEntity) -> Unit
) : ListAdapter<PlaylistEntity, PlaylistAdapter.ViewHolder>(DiffCallback()) {

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

        fun bind(playlist: PlaylistEntity) {
            binding.tvName.text = playlist.name

            val songCount = if (playlist.songIds.isEmpty()) 0 else playlist.songIds.split(",").size
            binding.tvSongCount.text = "$songCount 首歌曲"

            binding.root.setOnClickListener {
                onPlaylistClick(playlist)
            }

            binding.btnMore.setOnClickListener {
                onDeleteClick(playlist)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PlaylistEntity>() {
        override fun areItemsTheSame(oldItem: PlaylistEntity, newItem: PlaylistEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PlaylistEntity, newItem: PlaylistEntity): Boolean {
            return oldItem == newItem
        }
    }
}
