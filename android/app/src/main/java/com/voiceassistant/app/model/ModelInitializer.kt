package com.voiceassistant.app.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Initializes and manages ML models
 * Handles download, extraction and validation
 */
class ModelInitializer(private val context: Context) {
    
    private val _downloadStates = MutableStateFlow<List<ModelDownloadState>>(emptyList())
    val downloadStates: Flow<List<ModelDownloadState>> = _downloadStates.asStateFlow()
    
    private val client = OkHttpClient()
    private val modelsDir = File(context.filesDir, "models")
    
    init {
        modelsDir.mkdirs()
    }
    
    /**
     * Check if all models are ready
     */
    fun areModelsReady(): Boolean {
        return ModelInfo.getRequiredModels().all { model ->
            isModelExtracted(model)
        }
    }
    
    /**
     * Get model directory for a specific type
     */
    fun getModelDir(type: ModelType): File {
        return File(modelsDir, type.name.lowercase()).apply { mkdirs() }
    }
    
    /**
     * Initialize all models
     * Downloads and extracts if needed
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val models = ModelInfo.getRequiredModels()
            
            // Initialize states
            _downloadStates.value = models.map { 
                ModelDownloadState(it, ModelStatus.NOT_DOWNLOADED) 
            }
            
            for (model in models) {
                if (isModelExtracted(model)) {
                    updateState(model, ModelStatus.READY)
                    continue
                }
                
                // Download
                updateState(model, ModelStatus.DOWNLOADING)
                val downloadedFile = downloadModel(model)
                    ?: return@withContext Result.failure(IOException("Failed to download ${model.name}"))
                
                updateState(model, ModelStatus.DOWNLOADED)
                
                // Extract
                updateState(model, ModelStatus.EXTRACTING)
                extractModel(downloadedFile, model)
                
                // Cleanup
                downloadedFile.delete()
                
                updateState(model, ModelStatus.READY)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Model initialization failed")
            Result.failure(e)
        }
    }
    
    private fun isModelExtracted(model: ModelInfo): Boolean {
        val dir = getModelDir(model.type)
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }
    
    private suspend fun downloadModel(model: ModelInfo): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            
            val body = response.body ?: throw IOException("Empty response")
            
            val tempFile = File(context.cacheDir, model.fileName)
            val outputStream = FileOutputStream(tempFile)
            val inputStream = body.byteStream()
            
            val buffer = ByteArray(8192)
            var downloaded = 0L
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                
                // Update progress
                val progress = downloaded.toFloat() / model.sizeBytes
                updateState(model, ModelStatus.DOWNLOADING, progress.coerceIn(0f, 1f))
            }
            
            outputStream.close()
            inputStream.close()
            
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "Download failed for ${model.name}")
            updateState(model, ModelStatus.ERROR, error = e.message)
            null
        }
    }
    
    private suspend fun extractModel(zipFile: File, model: ModelInfo) = withContext(Dispatchers.IO) {
        val outputDir = getModelDir(model.type)
        
        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry: ZipEntry?
            while ((entry = zipInput.nextEntry) != null) {
                val outputFile = File(outputDir, entry.name)
                
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output ->
                        zipInput.copyTo(output)
                    }
                }
                
                zipInput.closeEntry()
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
     * Get total download size
     */
    fun getTotalDownloadSize(): Long {
        return ModelInfo.getRequiredModels().sumOf { it.sizeBytes }
    }
    
    /**
     * Clean up all models
     */
    fun clearModels() {
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()
    }
}
