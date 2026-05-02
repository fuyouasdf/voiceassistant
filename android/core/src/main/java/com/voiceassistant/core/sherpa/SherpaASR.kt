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

package com.voiceassistant.core.sherpa

/**
 * Endpoint timing configuration for ASR
 */
data class EndpointTimingConfig(
    val rule1MustStartWithTrailingSilence: Boolean = false,
    val rule1TimeoutSec: Float = 4.0f,
    val rule1TrailingSilenceSec: Float = 0.0f,
    val rule2MustStartWithTrailingSilence: Boolean = true,
    val rule2TimeoutSec: Float = 4.0f,
    val rule2TrailingSilenceSec: Float = 0.0f,
    val rule3MustStartWithTrailingSilence: Boolean = false,
    val rule3TimeoutSec: Float = 0.0f,
    val rule3TrailingSilenceSec: Float = 30.0f
)

/**
 * Interface for Automatic Speech Recognition (ASR) engine
 */
interface SherpaASR {

    /**
     * Callback for streaming recognition
     */
    interface RecognitionListener {
        /** Called with partial recognition result during speaking */
        fun onPartialResult(text: String)
        /** Called with final result after endpoint detected */
        fun onFinalResult(text: String)
        /** Called when speech endpoint is detected (user stopped speaking) */
        fun onEndpointDetected()
    }

    /**
     * Initialize the ASR engine with model
     * @param modelPath Path to the model directory
     * @param provider Compute provider: "cpu", "gpu", or "npu"
     * @param endpointTimingConfig Endpoint timing configuration (rule timeouts)
     * @return true if initialization successful
     */
    fun initialize(
        modelPath: String,
        provider: String = "cpu",
        endpointTimingConfig: EndpointTimingConfig = EndpointTimingConfig()
    ): Boolean

    /**
     * Recognize speech from audio
     * @param audio Audio samples (16kHz, float)
     * @return Recognized text
     */
    suspend fun recognize(audio: FloatArray): String

    /**
     * Streaming recognition with real-time callbacks
     * Processes audio incrementally and calls listener with partial results
     * @param audio Audio samples (16kHz, float)
     * @param listener Callback for recognition events
     */
    suspend fun recognizeStreaming(audio: FloatArray, listener: RecognitionListener)

    /**
     * Reset the recognizer state
     */
    fun reset()

    /**
     * Release resources
     */
    fun release()
}
