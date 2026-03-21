package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber
import java.io.File

class SherpaTTSImpl(private val context: Context) : SherpaTTS {

    private var tts: OfflineTts? = null

    override fun initialize(modelPath: String): Boolean {
        return try {
            val modelDir = copyModelsFromAssets("models/tts")

            val onnxFile = modelDir.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull()
                ?: throw Exception("No TTS model found")

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = onnxFile.absolutePath,
                        lexicon = "",
                        tokens = File(modelDir, "tokens.txt").absolutePath,
                        dataDir = modelDir.absolutePath,
                        dictDir = "",
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 1.0f
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                ruleFsts = "",
                maxNumSentences = 1
            )

            tts = OfflineTts(assetManager = null, config = config)

            Timber.d("TTS initialized with model: $modelPath")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TTS")
            false
        }
    }

    override fun synthesize(text: String): FloatArray {
        val t = tts ?: return FloatArray(0)

        return try {
            val audio = t.generate(text, sid = 0, speed = 1.0f)
            val samples = audio.samples

            Timber.d("TTS synthesized: ${text.length} chars -> ${samples.size} samples")
            samples
        } catch (e: Exception) {
            Timber.e(e, "TTS synthesis error")
            FloatArray(0)
        }
    }

    override fun stop() {
        // Offline TTS doesn't support stop during generation
    }

    override fun release() {
        tts = null
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
