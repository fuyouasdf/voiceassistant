package com.voiceassistant.core.sherpa

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * Model configuration loader
 * Maps model types to actual file paths
 */
class ModelConfig(private val context: Context) {
    
    private val config: ModelConfigData by lazy {
        loadConfig()
    }
    
    private fun loadConfig(): ModelConfigData {
        val json = context.assets.open("models/model_config.json")
            .bufferedReader()
            .use { it.readText() }
        return Gson().fromJson(json, ModelConfigData::class.java)
    }
    
    /**
     * Get model directory for a specific type
     */
    fun getModelDir(type: ModelType): File {
        return File(context.filesDir, "models/${type.name.lowercase()}")
    }
    
    /**
     * Get model file path
     */
    fun getModelFile(type: ModelType, fileKey: String): String {
        val modelInfo = when (type) {
            ModelType.KWS -> config.models.kws
            ModelType.ASR -> config.models.asr
            ModelType.TTS -> config.models.tts
        }
        
        val fileName = modelInfo.files[fileKey] 
            ?: throw IllegalArgumentException("Unknown file key: $fileKey")
        
        return File(getModelDir(type), fileName).absolutePath
    }
    
    /**
     * Get model configuration
     */
    fun getModelConfig(type: ModelType): ModelInfo {
        return when (type) {
            ModelType.KWS -> config.models.kws
            ModelType.ASR -> config.models.asr
            ModelType.TTS -> config.models.tts
        }
    }
    
    /**
     * Check if model files exist
     */
    fun isModelReady(type: ModelType): Boolean {
        val modelInfo = getModelConfig(type)
        val modelDir = getModelDir(type)
        
        return modelInfo.files.values.all { fileName ->
            File(modelDir, fileName).exists()
        }
    }
    
    /**
     * Get all required model files for download
     */
    fun getRequiredDownloads(): List<ModelDownloadInfo> {
        return ModelType.values().map { type ->
            val info = getModelConfig(type)
            ModelDownloadInfo(
                type = type,
                name = info.name,
                version = info.version,
                url = getDownloadUrl(type),
                sizeBytes = getEstimatedSize(type)
            )
        }
    }
    
    private fun getDownloadUrl(type: ModelType): String {
        return when (type) {
            ModelType.KWS -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2025-02-28.tar.bz2"
            ModelType.ASR -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.30/sherpa-onnx-v1.12.30-vad-asr-zh_en-paraformer_large.tar.bz2"
            ModelType.TTS -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-huayan-medium-2025-01-15.tar.bz2"
        }
    }
    
    private fun getEstimatedSize(type: ModelType): Long {
        return when (type) {
            ModelType.KWS -> 3_500_000L
            ModelType.ASR -> 350_000_000L  // paraformer_large is bigger
            ModelType.TTS -> 64_000_000L
        }
    }
}

enum class ModelType {
    KWS, ASR, TTS
}

data class ModelDownloadInfo(
    val type: ModelType,
    val name: String,
    val version: String,
    val url: String,
    val sizeBytes: Long
)

// JSON data classes

data class ModelConfigData(
    val version: String,
    val models: Models
)

data class Models(
    val kws: ModelInfo,
    val asr: ModelInfo,
    val tts: ModelInfo
)

data class ModelInfo(
    val name: String,
    val version: String,
    val type: String,
    val files: Map<String, String>,
    val config: ModelParams
)

data class ModelParams(
    val numThreads: Int,
    val provider: String,
    val sampleRate: Int,
    val decodingMethod: String? = null,
    val speed: Float? = null
)
