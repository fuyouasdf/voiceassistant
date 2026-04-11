package com.voiceassistant.core.sherpa

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber

/**
 * Sherpa-ONNX ASR 实现
 * 使用 streaming zipformer2 transducer 模型 (sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30)
 */
class SherpaASRImpl(private val context: Context) : SherpaASR {

    private var recognizer: OnlineRecognizer? = null

    override fun initialize(modelPath: String, provider: String): Boolean {
        return try {
            // modelPath 格式: "models/asr"
            // 模型目录名从 modelPath 末尾提取
            val modelDir = modelPath.substringAfterLast("/")

            Timber.d("Initializing ASR with model path: $modelPath, dir: $modelDir, provider: $provider")

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$modelDir/encoder.int8.onnx",
                        decoder = "$modelDir/decoder.onnx",
                        joiner = "$modelDir/joiner.int8.onnx"
                    ),
                    tokens = "$modelDir/tokens.txt",
                    numThreads = 4,
                    debug = false,
                    provider = provider,
                    modelType = "zipformer2"  // 新模型使用 zipformer2
                ),
                endpointConfig = EndpointConfig(
                    // 调整静音阈值，避免短命令被过早截断
                    // rule1: 非语音连续超时（静音多久认为一句话结束）
                    // rule2: 语音段落后的静音超时（增大到4.0s，避免2-3秒短命令被截断）
                    rule1 = EndpointRule(false, 4.0f, 0.0f),
                    rule2 = EndpointRule(true, 4.0f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 30.0f)
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search"
            )

            recognizer = OnlineRecognizer(
                assetManager = context.assets,
                config = config
            )

            Timber.d("ASR initialized successfully with model: $modelDir, provider: $provider")
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

    override suspend fun recognizeStreaming(audio: FloatArray, listener: SherpaASR.RecognitionListener) {
        val r = recognizer ?: return

        if (audio.isEmpty()) {
            Timber.w("ASR received empty audio for streaming")
            return
        }

        Timber.d("ASR streaming recognition: ${audio.size} samples")

        try {
            val stream = r.createStream()
            // 重置 stream 状态，确保新的识别从干净状态开始
            r.reset(stream)

            val interval = 0.1  // 100ms per chunk
            val bufferSize = (interval * 16000).toInt()
            var offset = 0

            // 先处理完所有音频（不在循环内检查 endpoint，避免 Sherpa 过早锁定中间结果）
            // 正确做法：先发送所有音频，再统一检查 endpoint
            while (offset < audio.size) {
                val end = minOf(offset + bufferSize, audio.size)
                val chunk = audio.copyOfRange(offset, end)

                stream.acceptWaveform(chunk, sampleRate = 16000)

                while (r.isReady(stream)) {
                    r.decode(stream)
                }

                // 获取中间结果并回调，实现流式显示
                val partialText = r.getResult(stream).text ?: ""
                Timber.d("ASR partial: '$partialText'")
                if (partialText.isNotEmpty()) {
                    listener.onPartialResult(partialText)
                }

                offset = end
            }

            // 统一在所有音频处理完后检查 endpoint
            if (r.isEndpoint(stream)) {
                Timber.d("ASR endpoint detected after all audio processed")
                listener.onEndpointDetected()
                // 添加尾部填充以获得更好的识别效果
                val tailPaddings = FloatArray((0.8 * 16000).toInt())
                stream.acceptWaveform(tailPaddings, sampleRate = 16000)
                while (r.isReady(stream)) {
                    r.decode(stream)
                }
            }

            // 获取最终结果
            val finalText = r.getResult(stream).text ?: ""
            if (finalText.isNotEmpty()) {
                listener.onFinalResult(finalText)
            }

            // 识别完成后重置 stream 状态，为下一次识别做准备
            r.reset(stream)
            stream.release()
        } catch (e: Exception) {
            Timber.e(e, "ASR streaming recognition error")
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
