package com.voiceassistant.app.model

/**
 * Model information for download and initialization
 */
data class ModelInfo(
    val name: String,
    val type: ModelType,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val md5Hash: String? = null
) {
    companion object {
        fun getRequiredModels(): List<ModelInfo> = listOf(
            ModelInfo(
                name = "KWS Wake Word",
                type = ModelType.KWS,
                fileName = "kws.zip",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2",
                sizeBytes = 3_500_000
            ),
            ModelInfo(
                name = "ASR Chinese",
                type = ModelType.ASR,
                fileName = "asr.zip",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-2024-03-09.tar.bz2",
                sizeBytes = 105_000_000
            ),
            ModelInfo(
                name = "TTS Chinese",
                type = ModelType.TTS,
                fileName = "tts.zip",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/piper-zh_CN-huayan-medium.tar.bz2",
                sizeBytes = 125_000_000
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
