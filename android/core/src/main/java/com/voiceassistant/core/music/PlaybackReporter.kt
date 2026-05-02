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

package com.voiceassistant.core.music

/**
 * 播放进度上报接口
 * 由 app 模块实现，用于将播放状态上报给 Jellyfin 等服务端
 */
interface PlaybackReporter {
    /**
     * 上报播放开始
     */
    suspend fun reportPlaybackStart(itemId: String, positionTicks: Long = 0, isPaused: Boolean = false)

    /**
     * 上报播放进度
     * @param playSessionId Jellyfin 播放会话 ID（用于关联播放会话）
     * @param mediaSourceId 媒体源 ID
     */
    suspend fun reportPlaybackProgress(itemId: String, positionTicks: Long, playSessionId: String? = null, mediaSourceId: String? = null)

    /**
     * 上报播放停止
     */
    suspend fun reportPlaybackStopped(itemId: String, positionTicks: Long)

    /**
     * 标记为收藏
     */
    suspend fun markAsFavorite(itemId: String): Boolean

    /**
     * 取消收藏
     */
    suspend fun removeFromFavorites(itemId: String): Boolean

    /**
     * 检查是否为收藏
     */
    suspend fun isFavorite(itemId: String): Boolean
}

/**
 * 空实现的 PlaybackReporter，用于没有服务端报告的情况
 */
class NoOpPlaybackReporter : PlaybackReporter {
    override suspend fun reportPlaybackStart(itemId: String, positionTicks: Long, isPaused: Boolean) {
        // No-op
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionTicks: Long, playSessionId: String?, mediaSourceId: String?) {
        // No-op
    }

    override suspend fun reportPlaybackStopped(itemId: String, positionTicks: Long) {
        // No-op
    }

    override suspend fun markAsFavorite(itemId: String): Boolean = false

    override suspend fun removeFromFavorites(itemId: String): Boolean = false

    override suspend fun isFavorite(itemId: String): Boolean = false
}