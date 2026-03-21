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
