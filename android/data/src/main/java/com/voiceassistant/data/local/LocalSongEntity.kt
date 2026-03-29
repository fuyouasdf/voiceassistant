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