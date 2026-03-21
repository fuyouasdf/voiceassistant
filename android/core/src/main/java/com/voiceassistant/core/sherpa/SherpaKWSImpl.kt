package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber
import java.io.File

class SherpaKWSImpl(private val context: Context) : SherpaKWS {

    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    override fun initialize(modelPath: String): Boolean {
        return try {
            val modelDir = copyModelsFromAssets("models/kws")

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
                keywordsThreshold = 0.7f,
                numTrailingBlanks = 0
            )

            kws = KeywordSpotter(assetManager = null, config = config)
            stream = kws?.createStream("")

            Timber.d("KWS initialized with model: $modelPath")
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
            k.decode(s)

            val result = k.getResult(s)
            if (result != null) {
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
        stream = null
        kws = null
    }

    private fun copyModelsFromAssets(assetPath: String): File {
        val destDir = File(context.filesDir, assetPath)
        if (destDir.exists() && destDir.listFiles()?.isNotEmpty() == true) {
            return destDir
        }
        destDir.mkdirs()

        try {
            context.assets.list(assetPath)?.forEach { fileName ->
                val fullAssetPath = "$assetPath/$fileName"
                val isDir = try {
                    context.assets.list(fullAssetPath)?.isNotEmpty() == true
                } catch (e: Exception) {
                    false
                }

                if (isDir) {
                    copyModelsFromAssets(fullAssetPath)
                } else {
                    val destFile = File(destDir, fileName)
                    if (!destFile.exists()) {
                        context.assets.open(fullAssetPath).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy models from assets")
        }

        return destDir
    }
}
