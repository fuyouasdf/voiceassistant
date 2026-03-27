package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ItemSongBinding
import com.voiceassistant.data.remote.JellyfinSong

/**
 * 歌曲列表适配器
 */
class SongAdapter(
    private val onSongClick: (JellyfinSong) -> Unit,
    private val onAddToPlaylistClick: (JellyfinSong) -> Unit
) : ListAdapter<JellyfinSong, SongAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSongBinding.inflate(
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
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: JellyfinSong) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist ?: "未知艺术家"
            binding.tvDuration.text = formatDuration(song.duration)

            // 加载封面
            song.coverUrl?.let { url ->
                // 使用 Glide 或其他图片加载库
                // 这里简化处理
            }

            binding.root.setOnClickListener {
                onSongClick(song)
            }

            binding.btnMore.setOnClickListener {
                onAddToPlaylistClick(song)
            }
        }

        private fun formatDuration(seconds: Int): String {
            val minutes = seconds / 60
            val secs = seconds % 60
            return String.format("%d:%02d", minutes, secs)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<JellyfinSong>() {
        override fun areItemsTheSame(oldItem: JellyfinSong, newItem: JellyfinSong): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: JellyfinSong, newItem: JellyfinSong): Boolean {
            return oldItem == newItem
        }
    }
}
