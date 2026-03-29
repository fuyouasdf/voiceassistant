package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ItemPlaylistSongBinding
import com.voiceassistant.domain.model.PlaylistSong

/**
 * 播放列表歌曲适配器
 */
class PlaylistAdapter(
    private val onSongClick: (PlaylistSong) -> Unit,
    private val onDeleteClick: (PlaylistSong) -> Unit
) : ListAdapter<PlaylistSong, PlaylistAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistSongBinding.inflate(
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
        private val binding: ItemPlaylistSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: PlaylistSong) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist ?: "未知艺术家"
            binding.tvDuration.text = formatDuration(song.duration)

            binding.root.setOnClickListener {
                onSongClick(song)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(song)
            }
        }

        private fun formatDuration(seconds: Int): String {
            val minutes = seconds / 60
            val secs = seconds % 60
            return String.format("%d:%02d", minutes, secs)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PlaylistSong>() {
        override fun areItemsTheSame(oldItem: PlaylistSong, newItem: PlaylistSong): Boolean {
            return oldItem.songId == newItem.songId
        }

        override fun areContentsTheSame(oldItem: PlaylistSong, newItem: PlaylistSong): Boolean {
            return oldItem == newItem
        }
    }
}
