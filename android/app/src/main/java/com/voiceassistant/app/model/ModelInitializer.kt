package com.voiceassistant.app.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Initializes and manages ML models
 * Copies models from assets to internal storage
 */
class ModelInitializer(private val context: Context) {

    private val _downloadStates = MutableStateFlow<List<ModelDownloadState>>(emptyList())
    val downloadStates: Flow<List<ModelDownloadState>> = _downloadStates.asStateFlow()

    private val modelsDir = File(context.filesDir, "models")

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("model_init", Context.MODE_PRIVATE)
    }

    init {
        modelsDir.mkdirs()
    }

    /**
     * Check if all models are ready
     * Note: Models are actually loaded by SherpaKWSImpl, SherpaASRImpl, SherpaTTSImpl
     * during their own initialization, not by ModelInitializer.
     * This method always returns true since each sherpa impl handles its own model loading.
     */
    fun areModelsReady(): Boolean {
        return true
    }

    /**
     * Get model directory for a specific type
     */
    fun getModelDir(type: ModelType): File {
        return File(modelsDir, type.name.lowercase()).apply { mkdirs() }
    }

    /**
     * Initialize all models from assets
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val models = ModelInfo.getRequiredModels()

            // Initialize states
            _downloadStates.value = models.map {
                ModelDownloadState(it, ModelStatus.NOT_DOWNLOADED)
            }

            for (model in models) {
                if (isModelReady(model)) {
                    updateState(model, ModelStatus.READY)
                    continue
                }

                // Copy from assets
                updateState(model, ModelStatus.EXTRACTING)
                copyModelFromAssets(model)
                updateState(model, ModelStatus.READY)
            }

            // Mark initialization as completed
            prefs.edit().putBoolean(KEY_INIT_COMPLETED, true).apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Model initialization failed")
            Result.failure(e)
        }
    }

    companion object {
        private const val KEY_INIT_COMPLETED = "init_completed"
    }

    private fun isModelReady(model: ModelInfo): Boolean {
        val dir = getModelDir(model.type)
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }

    private suspend fun copyModelFromAssets(model: ModelInfo) = withContext(Dispatchers.IO) {
        val outputDir = getModelDir(model.type)
        val assetPath = "models/${model.type.name.lowercase()}"

        // List all files in asset directory
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: emptyArray()

        if (files.isEmpty()) {
            Timber.e("Asset directory $assetPath is empty or not found")
            throw IllegalStateException("模型资源不存在: $assetPath")
        }

        // Copy each file
        for (fileName in files) {
            val fullAssetPath = "$assetPath/$fileName"
            val outputFile = File(outputDir, fileName)

            // Skip directories and special files
            if (fileName.endsWith(".md") || fileName == "test_wavs") continue

            try {
                // Check if it's a directory
                val subFiles = assetManager.list(fullAssetPath)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    // It's a directory, recurse
                    File(outputDir, fileName).mkdirs()
                    copyAssetDir(assetManager, fullAssetPath, File(outputDir, fileName))
                } else {
                    // It's a file, copy it
                    outputFile.parentFile?.mkdirs()
                    assetManager.open(fullAssetPath).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Update progress
                    updateState(model, ModelStatus.EXTRACTING, progress = 0.5f)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to copy $fullAssetPath")
            }
        }

        updateState(model, ModelStatus.EXTRACTING, progress = 1f)
    }

    private fun copyAssetDir(assetManager: android.content.res.AssetManager, srcDir: String, dstDir: File) {
        val files = assetManager.list(srcDir) ?: return

        for (fileName in files) {
            val fullSrcPath = "$srcDir/$fileName"
            val dstFile = File(dstDir, fileName)

            try {
                val subFiles = assetManager.list(fullSrcPath)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    dstFile.mkdirs()
                    copyAssetDir(assetManager, fullSrcPath, dstFile)
                } else {
                    assetManager.open(fullSrcPath).use { input ->
                        FileOutputStream(dstFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to copy $fullSrcPath")
            }
        }
    }
    
    private fun updateState(
        model: ModelInfo, 
        status: ModelStatus, 
        progress: Float = 1f,
        error: String? = null
    ) {
        _downloadStates.value = _downloadStates.value.map { state ->
            if (state.modelInfo.name == model.name) {
                state.copy(status = status, progress = progress, error = error)
            } else {
                state
            }
        }
    }
    
    /**
     * Get total download size (always 0 for bundled models)
     */
    fun getTotalDownloadSize(): Long {
        return 0L
    }

    /**
     * Clean up all models
     */
    fun clearModels() {
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()
        prefs.edit().remove(KEY_INIT_COMPLETED).apply()
    }
}
