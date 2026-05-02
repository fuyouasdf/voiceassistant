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

package com.voiceassistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地播放列表实体
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val songIds: String = "", // 歌曲 ID 列表，用逗号分隔（兼容旧数据）
    val songData: String = "", // JSON 格式的歌曲数据
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 播放列表中的歌曲（不存储到数据库，作为内存数据结构）
 */
data class PlaylistSong(
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int,
    val streamUrl: String,
    val coverUrl: String?
)

/**
 * 播放列表
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songs: List<PlaylistSong> = emptyList(),
    val songCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)