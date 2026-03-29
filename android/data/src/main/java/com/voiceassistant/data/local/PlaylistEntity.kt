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