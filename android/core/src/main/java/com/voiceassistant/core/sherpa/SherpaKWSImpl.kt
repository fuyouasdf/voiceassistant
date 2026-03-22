package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber
import java.io.File

class SherpaKWSImpl(private val context: Context) : SherpaKWS {

    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var currentModelDir: File? = null

    override fun initialize(modelPath: String): Boolean {
        return try {
            // KWS模型目录: sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01
            val modelDir = copyKWSModelsFromAssets()
            currentModelDir = modelDir

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
                keywordsThreshold = 0.5f,
                numTrailingBlanks = 0
            )

            kws = KeywordSpotter(assetManager = null, config = config)
            stream = kws?.createStream("")

            Timber.d("KWS initialized with model: ${modelDir.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize KWS")
            false
        }
    }

    override fun process(audio: FloatArray): Boolean {
        val s = stream ?: return false
        val k = kws ?: return false

        return try {
            s.acceptWaveform(audio, 16000)

            // Process all ready audio (official pattern)
            while (k.isReady(s)) {
                k.decode(s)
            }

            val result = k.getResult(s)
            // Check if keyword is detected (non-empty)
            val detected = result.keyword.isNotEmpty()
            if (detected) {
                Timber.d("Wake word detected: ${result.keyword}")
                k.reset(s)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "KWS process error")
            false
        }
    }

    override fun setSensitivity(sensitivity: Float) {
        // Note: sensitivity is set in config for Sherpa-ONNX
    }

    override fun release() {
        try {
            stream = null
            kws?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing KWS resources")
        }
        kws = null
        currentModelDir = null
    }

    /**
     * Copy KWS model files from assets to internal storage
     * The model is located at: sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/
     */
    private fun copyKWSModelsFromAssets(): File {
        val assetModelDir = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
        val destDir = File(context.filesDir, "models/kws")

        // If already copied, return existing directory
        if (destDir.exists() && destDir.listFiles()?.isNotEmpty() == true) {
            Timber.d("KWS models already copied to: ${destDir.absolutePath}")
            return destDir
        }

        destDir.mkdirs()
        Timber.d("Copying KWS models from assets to: ${destDir.absolutePath}")

        // List of required files
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
