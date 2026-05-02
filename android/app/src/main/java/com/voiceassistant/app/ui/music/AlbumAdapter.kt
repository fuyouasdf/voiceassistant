/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.app.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voiceassistant.app.R
import com.voiceassistant.app.databinding.ItemAlbumBinding
import com.voiceassistant.domain.model.Album

/**
 * 专辑列表适配器
 */
class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit,
    private val coverBaseUrl: String = ""
) : ListAdapter<Album, AlbumAdapter.ViewHolder>(DiffCallback()) {

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

        fun bind(album: Album) {
            binding.tvName.text = album.name
            binding.tvArtist.text = album.artist ?: "未知艺术家"

            // 加载封面
            if (album.imageTag != null && coverBaseUrl.isNotEmpty()) {
                val coverUrl = "$coverBaseUrl/Items/${album.id}/Images/Primary?maxWidth=300&maxHeight=300"
                binding.ivCover.load(coverUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_music)
                    error(R.drawable.ic_music)
                }
            } else {
                binding.ivCover.setImageResource(R.drawable.ic_music)
            }

            binding.root.setOnClickListener {
                onAlbumClick(album)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean {
            return oldItem == newItem
        }
    }
}