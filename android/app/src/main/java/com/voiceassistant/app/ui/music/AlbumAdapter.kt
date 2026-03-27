package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ItemAlbumBinding
import com.voiceassistant.data.remote.JellyfinAlbum

/**
 * 专辑列表适配器
 */
class AlbumAdapter(
    private val onAlbumClick: (JellyfinAlbum) -> Unit
) : ListAdapter<JellyfinAlbum, AlbumAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlbumBinding.inflate(
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
        private val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: JellyfinAlbum) {
            binding.tvName.text = album.name
            binding.tvArtist.text = album.artist ?: "未知艺术家"

            // 加载封面
            album.imageTag?.let { imageTag ->
                val coverUrl = "http://localhost/Items/${album.id}/Images/Primary?api_key=&maxWidth=300&maxHeight=300"
                binding.ivCover.load(coverUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_music)
                    error(R.drawable.ic_music)
                }
            } ?: binding.ivCover.setImageResource(R.drawable.ic_music)

            binding.root.setOnClickListener {
                onAlbumClick(album)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<JellyfinAlbum>() {
        override fun areItemsTheSame(oldItem: JellyfinAlbum, newItem: JellyfinAlbum): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: JellyfinAlbum, newItem: JellyfinAlbum): Boolean {
            return oldItem == newItem
        }
    }
}