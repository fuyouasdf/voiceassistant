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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地歌曲实体 - 存储播放列表中的歌曲信息
 */
@Entity(
    tableName = "local_songs",
    indices = [Index(value = ["playlistId"])]
)
data class LocalSongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val songId: String,       // Jellyfin 歌曲 ID
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int,        // 秒
    val streamUrl: String,
    val coverUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
)