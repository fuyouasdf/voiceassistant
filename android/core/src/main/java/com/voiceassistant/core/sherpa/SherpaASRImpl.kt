package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber

/**
 * Sherpa-ONNX ASR 实现
 * 使用 streaming zipformer transducer 模型
 */
class SherpaASRImpl(private val context: Context) : SherpaASR {

    private var recognizer: OnlineRecognizer? = null

    override fun initialize(modelPath: String): Boolean {
        return try {
            // 使用 sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20 模型
            // 该模型支持中英文双语识别
            val modelDir = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"

            Timber.d("Initializing ASR with model dir: $modelDir")

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$modelDir/encoder-epoch-99-avg-1.int8.onnx",
                        decoder = "$modelDir/decoder-epoch-99-avg-1.onnx",
                        joiner = "$modelDir/joiner-epoch-99-avg-1.onnx"
                    ),
                    tokens = "$modelDir/tokens.txt",
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "zipformer"  // 必须指定模型类型
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0.0f),
                    rule2 = EndpointRule(true, 1.4f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 20.0f)
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search"
            )

            recognizer = OnlineRecognizer(
                assetManager = context.assets,
                config = config
            )

            Timber.d("ASR initialized successfully with model: $modelDir")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ASR: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    override suspend fun recognize(audio: FloatArray): String {
        val r = recognizer ?: return ""

        if (audio.isEmpty()) {
            Timber.w("ASR received empty audio")
            return ""
        }

        Timber.d("ASR recognizing: ${audio.size} samples")

        return try {
            // 每次识别创建新的 stream（参考官方示例）
            val stream = r.createStream()

            // 流式处理音频数据（每 100ms 一个 chunk）
            val interval = 0.1
            val bufferSize = (interval * 16000).toInt()
            var offset = 0

            while (offset < audio.size) {
                val end = minOf(offset + bufferSize, audio.size)
                val chunk = audio.copyOfRange(offset, end)

                stream.acceptWaveform(chunk, sampleRate = 16000)

                // 解码就绪的数据
                while (r.isReady(stream)) {
                    r.decode(stream)
                }

                offset = end
            }

            // 获取识别结果
            val result = r.getResult(stream)
            val text = result.text ?: ""

            stream.release()

            Timber.d("ASR result: '$text'")
            text
        } catch (e: Exception) {
            Timber.e(e, "ASR recognition error")
            ""
        }
    }

    override fun reset() {
        // 每个识别周期创建新 stream，无需额外重置
        // 参考官方示例：stream 在 recognize 中创建和释放
    }

    override fun release() {
        recognizer?.release()
        recognizer = null
        Timber.d("ASR released")
    }
}
