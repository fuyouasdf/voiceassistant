package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ItemFolderBinding
import com.voiceassistant.data.remote.JellyfinItem

/**
 * 文件夹内容适配器 - 支持瀑布流布局显示混合内容
 */
class FolderAdapter(
    private val onItemClick: (JellyfinItem) -> Unit,
    private val getCoverUrl: (String) -> String
) : ListAdapter<JellyfinItem, FolderAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFolderBinding.inflate(
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
        private val binding: ItemFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: JellyfinItem) {
            binding.tvName.text = item.name

            // 根据类型显示副标题
            binding.tvSubtitle.text = when {
                item.isAudio -> item.artist ?: "歌曲"
                item.isAlbum -> item.albumName ?: item.artist ?: "专辑"
                item.isArtist -> "艺术家"
                item.isFolder -> "文件夹"
                else -> item.type
            }

            // 根据类型选择占位图
            val placeholderIcon = when {
                item.isAudio -> R.drawable.ic_music
                item.isAlbum -> R.drawable.ic_album
                item.isArtist -> R.drawable.ic_artist
                item.isFolder -> R.drawable.ic_folder
                else -> R.drawable.ic_music
            }

            // 加载封面
            val coverUrl = getCoverUrl(item.id)
            binding.ivCover.load(coverUrl) {
                crossfade(true)
                placeholder(placeholderIcon)
                error(placeholderIcon)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<JellyfinItem>() {
        override fun areItemsTheSame(oldItem: JellyfinItem, newItem: JellyfinItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: JellyfinItem, newItem: JellyfinItem): Boolean {
            return oldItem == newItem
        }
    }
}