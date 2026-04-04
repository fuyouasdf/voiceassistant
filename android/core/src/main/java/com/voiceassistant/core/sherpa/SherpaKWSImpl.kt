package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantLock

class SherpaKWSImpl(private val context: Context) : SherpaKWS {

    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var currentModelDir: File? = null
    private var currentThreshold = 0.5f

    // 线程安全锁，保护 process() / updateThreshold() / reloadKeywords() / release() 的临界区
    private val lock = ReentrantLock()

    // 标记是否正在重建（防止 process() 在重建期间访问已销毁的 native 资源）
    private var isReloading = false

    override fun initialize(modelPath: String): Boolean {
        return try {
            val modelDir = copyKWSModelsFromAssets()
            currentModelDir = modelDir
            return initStream(currentThreshold)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize KWS")
            false
        }
    }

    /**
     * 初始化流，使用指定的阈值
     * 注意：此方法内部不加锁，由调用方负责加锁
     */
    private fun initStream(threshold: Float): Boolean {
        val modelDir = currentModelDir ?: return false

        return try {
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDir, "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath,
                        decoder = File(modelDir, "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath,
                        joiner = File(modelDir, "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxActivePaths = 4,
                keywordsFile = File(modelDir, "keywords.txt").absolutePath,
                keywordsScore = 1.0f,
                keywordsThreshold = threshold,
                numTrailingBlanks = 0
            )

            kws = KeywordSpotter(assetManager = null, config = config)
            stream = kws?.createStream("")
            currentThreshold = threshold

            Timber.d("KWS stream initialized with threshold: $threshold, model: ${modelDir.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to init KWS stream with threshold $threshold")
            false
        }
    }

    /**
     * 更新检测阈值
     * 内部会重建 KeywordSpotter 实例以应用新阈值
     * 线程安全：持有锁期间释放旧实例并创建新实例
     * @param threshold 新阈值 (0.0 - 1.0)
     * @return true 成功更新
     */
    fun updateThreshold(threshold: Float): Boolean {
        lock.lock()
        if (isReloading) {
            lock.unlock()
            Timber.w("KWS is reloading, skip threshold update")
            return false
        }
        isReloading = true
        try {
            val modelDir = currentModelDir
            if (modelDir == null) {
                Timber.w("Cannot update threshold: modelDir is null")
                return false
            }

            // 释放旧实例
            safeRelease()

            // 用新阈值重建
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDir, "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath,
                        decoder = File(modelDir, "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath,
                        joiner = File(modelDir, "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxActivePaths = 4,
                keywordsFile = File(modelDir, "keywords.txt").absolutePath,
                keywordsScore = 1.0f,
                keywordsThreshold = threshold,
                numTrailingBlanks = 0
            )

            kws = KeywordSpotter(assetManager = null, config = config)
            stream = kws?.createStream("")
            currentThreshold = threshold

            Timber.d("KWS threshold updated to: $threshold")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to update KWS threshold to $threshold")
            return false
        } finally {
            isReloading = false
            lock.unlock()
        }
    }

    override fun process(audio: FloatArray): KWSResult {
        lock.lock()
        if (isReloading) {
            lock.unlock()
            // 重建期间跳过本次处理，不崩溃
            return KWSResult(detected = false)
        }
        try {
            val s = stream
            val k = kws

            if (s == null || k == null) {
                Timber.w("KWS process called but stream or kws is null")
                return KWSResult(detected = false)
            }

            return try {
                s.acceptWaveform(audio, 16000)

                while (k.isReady(s)) {
                    k.decode(s)
                }

                val result = k.getResult(s)
                val detected = result.keyword.isNotEmpty()
                if (detected) {
                    Timber.d("Wake word detected: ${result.keyword}")
                    // Extract confidence from result - Sherpa returns 'prob' field
                    val confidence = extractConfidence(result)
                    k.reset(s)
                    KWSResult(detected = true, keyword = result.keyword, confidence = confidence)
                } else {
                    KWSResult(detected = false)
                }
            } catch (e: Exception) {
                Timber.e(e, "KWS process error")
                KWSResult(detected = false)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Extract confidence score from Sherpa KeywordSpotter result.
     * The actual field name may vary - try common names.
     */
    private fun extractConfidence(result: KeywordSpotterResult): Float {
        return try {
            // Sherpa's KeywordSpotterResult structure may vary across versions.
            // Try field first.
            val fieldNames = listOf("prob", "score", "confidence")
            for (name in fieldNames) {
                try {
                    val field = result.javaClass.getDeclaredField(name)
                    field.isAccessible = true
                    val value = (field.get(result) as? Number)?.toFloat()
                    if (value != null && value > 0f) {
                        return value.coerceIn(0f, 1f)
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }

            // Then try getter methods.
            val methodNames = listOf("getProb", "getScore", "getConfidence")
            for (name in methodNames) {
                try {
                    val method = result.javaClass.getMethod(name)
                    val value = (method.invoke(result) as? Number)?.toFloat()
                    if (value != null && value > 0f) {
                        return value.coerceIn(0f, 1f)
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }

            // If confidence is unavailable, avoid false negative blocking in upper layers.
            1.0f
        } catch (e: Exception) {
            // If we can't extract, return a permissive default.
            1.0f
        }
    }

    override fun setSensitivity(sensitivity: Float) {
        updateThreshold(sensitivity)
    }

    override fun reloadKeywords(keywordsFilePath: String, threshold: Float?): Boolean {
        lock.lock()
        if (isReloading) {
            lock.unlock()
            Timber.w("KWS is already reloading, skip")
            return false
        }
        isReloading = true
        try {
            val modelDir = currentModelDir
            if (modelDir == null) {
                Timber.w("Cannot reload keywords: modelDir is null")
                return false
            }

            // 释放旧实例
            safeRelease()

            val newThreshold = threshold ?: currentThreshold

            // Build config with new keywords file
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDir, "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath,
                        decoder = File(modelDir, "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath,
                        joiner = File(modelDir, "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxActivePaths = 4,
                keywordsFile = keywordsFilePath,
                keywordsScore = 1.0f,
                keywordsThreshold = newThreshold,
                numTrailingBlanks = 0
            )

            kws = KeywordSpotter(assetManager = null, config = config)
            stream = kws?.createStream("")
            currentThreshold = newThreshold

            Timber.d("KWS keywords reloaded from: $keywordsFilePath, threshold: $newThreshold")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to reload KWS keywords from $keywordsFilePath")
            return false
        } finally {
            isReloading = false
            lock.unlock()
        }
    }

    /**
     * 安全释放 KWS 资源，捕获所有异常避免崩溃
     */
    private fun safeRelease() {
        try {
            stream?.let { s ->
                try { s.release() } catch (e: Exception) { Timber.w("stream release: ${e.message}") }
            }
        } catch (e: Exception) {
            Timber.w("stream release outer: ${e.message}")
        }
        try {
            kws?.release()
        } catch (e: Exception) {
            Timber.w("kws release: ${e.message}")
        }
        kws = null
        stream = null
    }

    override fun release() {
        lock.lock()
        try {
            safeRelease()
            currentModelDir = null
        } finally {
            lock.unlock()
        }
    }

    /**
     * Copy KWS model files from assets to internal storage
     */
    private fun copyKWSModelsFromAssets(): File {
        val assetModelDir = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
        val destDir = File(context.filesDir, "models/kws")

        if (destDir.exists() && destDir.listFiles()?.isNotEmpty() == true) {
            Timber.d("KWS models already copied to: ${destDir.absolutePath}")
            return destDir
        }

        destDir.mkdirs()
        Timber.d("Copying KWS models from assets to: ${destDir.absolutePath}")

        val requiredFiles = listOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "tokens.txt",
            "keywords.txt"
        )

        try {
            requiredFiles.forEach { fileName ->
                val assetPath = "$assetModelDir/$fileName"
                val destFile = File(destDir, fileName)

                if (!destFile.exists()) {
                    context.assets.open(assetPath).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Timber.d("Copied: $fileName")
                }
            }
            Timber.i("KWS models copied successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy KWS models from assets")
            throw e
        }

        return destDir
    }
}
