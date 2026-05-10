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

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val isEncrypted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

// Config keys
object ConfigKeys {
    const val JELLYFIN_URL = "jellyfin_url"
    const val JELLYFIN_API_KEY = "jellyfin_api_key"

    const val DLNA_DEVICE_IP = "dlna_device_ip"
    const val DLNA_DEVICE_NAME = "dlna_device_name"
    const val DLNA_DEVICE_UUID = "dlna_device_uuid"

    const val LLM_BASE_URL = "llm_base_url"
    const val LLM_API_KEY = "llm_api_key"
    const val LLM_API_PATH = "llm_api_path"
    const val LLM_MODEL = "llm_model"
    const val LLM_SYSTEM_PROMPT = "llm_system_prompt"
    const val LLM_ROUTER_PROMPT = "llm_router_prompt"
    const val LLM_COMMAND_PROMPT = "llm_command_prompt"
    const val LLM_CONTEXT_COUNT = "llm_context_count"
    const val LLM_MAX_TURNS_BEFORE_RESET = "llm_max_turns_before_reset"
    const val LLM_CONVERSATION_TIMEOUT_SECONDS = "llm_conversation_timeout_seconds"
    const val LLM_SUMMARIZATION_THRESHOLD = "llm_summarization_threshold"

    const val OPENCLAW_URL = "openclaw_url"
    const val OPENCLAW_TOKEN = "openclaw_token"

    const val WAKE_SENSITIVITY = "wake_sensitivity"
    const val WAKE_WORDS = "wake_words"
    const val TTS_SPEED = "tts_speed"
    const val TTS_PITCH = "tts_pitch"
    const val TTS_ENABLED = "tts_enabled"

    const val IS_FIRST_LAUNCH = "is_first_launch"
}
