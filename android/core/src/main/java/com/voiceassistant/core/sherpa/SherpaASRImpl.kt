package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber
import java.io.File

class SherpaASRImpl(private val context: Context) : SherpaASR {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    override fun initialize(modelPath: String): Boolean {
        return try {
            val modelDir = copyModelsFromAssets("models/asr")

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDir, "model.int8.onnx").absolutePath,
                        decoder = File(modelDir, "model.int8.onnx").absolutePath,
                        joiner = File(modelDir, "model.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )

            recognizer = OnlineRecognizer(assetManager = null, config = config)
            stream = recognizer?.createStream()

            Timber.d("ASR initialized with model: $modelPath")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ASR")
            false
        }
    }

    override suspend fun recognize(audio: FloatArray): String {
        val s = stream ?: return ""
        val r = recognizer ?: return ""

        return try {
            s.acceptWaveform(audio, 16000)
            r.decode(s)

            val result = r.getResult(s)
            val text = result.text ?: ""

            Timber.d("ASR result: $text")
            text
        } catch (e: Exception) {
            Timber.e(e, "ASR recognition error")
            ""
        }
    }

    override fun reset() {
        // Create a new stream for next recognition
        stream = recognizer?.createStream()
    }

    override fun release() {
        stream = null
        recognizer = null
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
