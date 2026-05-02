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

package com.voiceassistant.app.model

/**
 * Model information for initialization
 */
data class ModelInfo(
    val name: String,
    val type: ModelType,
    val assetPath: String
) {
    companion object {
        fun getRequiredModels(): List<ModelInfo> = listOf(
            ModelInfo(
                name = "KWS 唤醒词",
                type = ModelType.KWS,
                assetPath = "models/kws"
            ),
            ModelInfo(
                name = "ASR 语音识别",
                type = ModelType.ASR,
                assetPath = "models/asr"
            ),
            ModelInfo(
                name = "TTS 语音合成",
                type = ModelType.TTS,
                assetPath = "models/tts"
            )
        )
    }
}

enum class ModelType {
    KWS, ASR, TTS
}

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    EXTRACTING,
    READY,
    ERROR
}

data class ModelDownloadState(
    val modelInfo: ModelInfo,
    val status: ModelStatus,
    val progress: Float = 0f,
    val error: String? = null
)
