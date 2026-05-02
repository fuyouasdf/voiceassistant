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

package com.voiceassistant.domain.model

/**
 * Domain model for a playlist (stores raw song data as JSON string)
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: String = "",      // Comma-separated song IDs (legacy)
    val songData: String = "",     // JSON format song data
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Domain model for a song in a playlist
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
