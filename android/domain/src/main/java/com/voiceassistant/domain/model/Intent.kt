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
 * Intent types for voice commands
 */
enum class IntentType {
    MUSIC,      // Play/pause music
    VOLUME,     // Volume control
    DEVICE,     // Device control
    QUERY,      // Information query (requires LLM)
    CHAT,       // General chat (requires LLM)
    UNKNOWN     // Unknown intent
}

/**
 * Parsed voice command intent.
 * This is a domain model - pure data, no side effects.
 */
data class Intent(
    val type: IntentType,
    val action: String? = null,
    val query: String? = null,
    val artist: String? = null,  // 歌手名
    val value: Int? = null,
    val song: Song? = null
)
