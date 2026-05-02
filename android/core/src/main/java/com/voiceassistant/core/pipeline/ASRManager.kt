/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.core.pipeline

import com.voiceassistant.core.sherpa.EndpointTimingConfig
import com.voiceassistant.core.sherpa.SherpaASR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages ASR (Automatic Speech Recognition) lifecycle.
 *
 * Responsibilities:
 * - Lazy initialization of ASR model
 * - Perform speech-to-text recognition
 * - Release resources
 *
 * This class does NOT manage pipeline states.
 */
class ASRManager(
    private val asr: SherpaASR,
    private val modelPath: String,
    private val endpointTimingConfig: EndpointTimingConfig = EndpointTimingConfig()
) {
    private var isLoaded = false
    private val provider: String = "cpu"  // TODO: 后续调查 GPU 支持问题

    /**
     * Lazily initialize ASR model.
     */
    suspend fun ensureInitialized() {
        if (isLoaded) return

        Timber.d("Lazy loading ASR with provider: $provider...")
        withContext(Dispatchers.IO) {
            // 模型实际目录名: sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20
            val result = asr.initialize(modelPath, provider, endpointTimingConfig)
            Timber.d("ASR initialized: $result")
            isLoaded = true
        }
    }

    /**
     * Recognize speech from audio data.
     * @throws IllegalStateException if ASR not initialized
     */
    suspend fun recognize(audioData: FloatArray): String {
        ensureInitialized()

        return withContext(Dispatchers.Default) {
            try {
                val text = asr.recognize(audioData)
                Timber.d("ASR result: '$text'")
                text
            } catch (e: Exception) {
                Timber.e(e, "ASR recognition failed")
                throw e
            }
        }
    }

    /**
     * Streaming recognition with real-time callbacks.
     * @param audioData Audio samples
     * @param listener Callback for recognition events
     */
    suspend fun recognizeStreaming(audioData: FloatArray, listener: SherpaASR.RecognitionListener) {
        ensureInitialized()

        withContext(Dispatchers.Default) {
            try {
                asr.recognizeStreaming(audioData, listener)
            } catch (e: Exception) {
                Timber.e(e, "ASR streaming recognition failed")
            }
        }
    }

    fun isLoaded(): Boolean = isLoaded
}
