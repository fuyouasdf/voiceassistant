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

package com.voiceassistant.app.di

import com.voiceassistant.data.repository.SettingsRepository
import com.voiceassistant.core.pipeline.WakeWord
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holder for configuration values that can be accessed synchronously from OkHttp interceptors
 */
@Singleton
class ConfigHolder @Inject constructor() {
    var settingsRepository: SettingsRepository? = null

    // Cached values updated on app start
    var jellyfinUrl: String = ""
    var jellyfinApiKey: String = ""
    var llmBaseUrl: String = ""
    var llmApiKey: String = ""
    var llmModel: String = "deepseek-chat"
    var ttsEnabled: Boolean = true
    var ttsSpeed: Float = 1.0f  // TTS 速度，默认 1.0
    var ttsPitch: Float = 1.0f  // TTS 音调，默认 1.0
    var wakeSensitivity: Float = 0.5f  // 唤醒灵敏度，默认 0.5
    var wakeWords: List<WakeWord> = emptyList()  // 唤醒词列表
    var maxContextCount: Int = 5  // 最大上下文消息数量
    var maxTurnsBeforeReset: Int = 10  // 最大对话轮次后重置上下文
    var conversationTimeoutSeconds: Int = 300  // 对话超时时间（秒）
    var summarizationThreshold: Int = 20  // 触发上下文摘要的消息数量阈值

    fun reload() {
        settingsRepository?.let { repo ->
            runBlocking {
                jellyfinUrl = repo.getJellyfinUrl()
                jellyfinApiKey = repo.getJellyfinApiKey()
                llmBaseUrl = repo.getLLMBaseUrl().ifEmpty { "https://api.deepseek.com" }
                llmApiKey = repo.getLLMApiKey()
                llmModel = repo.getLLMModel().ifEmpty { "deepseek-chat" }
                ttsEnabled = repo.getTtsEnabled()
                ttsSpeed = repo.getTtsSpeed()
                ttsPitch = repo.getTtsPitch()
                wakeSensitivity = repo.getWakeSensitivity()
                // Parse wake words from stored format: "keyword:response|keyword:response"
                wakeWords = repo.getWakeWords().mapNotNull { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.isNotEmpty()) {
                        WakeWord(
                            keyword = parts[0].trim(),
                            response = parts.getOrNull(1)?.trim() ?: "我在"
                        )
                    } else null
                }
                // Load conversation management settings
                maxContextCount = repo.getLLMContextCount()
                maxTurnsBeforeReset = repo.getLLMMaxTurnsBeforeReset()
                conversationTimeoutSeconds = repo.getLLMConversationTimeoutSeconds()
                summarizationThreshold = repo.getLLMSummarizationThreshold()
            }
        }
    }
}
