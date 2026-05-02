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

/**
 * Voice pipeline states
 */
enum class PipelineState {
    /** Initializing models in background */
    INITIALIZING,

    /** Idle, waiting for wake word */
    IDLE,

    /** Wake word detected, showing feedback */
    WAKEWORD_DETECTED,

    /** Wake word detected, waiting for speech */
    LISTENING,

    /** Recording speech */
    RECORDING,

    /** Recognizing speech to text */
    RECOGNIZING,

    /** Processing intent and LLM */
    THINKING,

    /** Speaking response */
    SPEAKING
}

/**
 * State with timestamp for tracking duration
 */
data class StateInfo(
    val state: PipelineState,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = "",  // Optional message, e.g., recognized text or AI response
    val recognizedText: String = "",  // Final ASR result to add to conversation
    val wakeConfidence: Float = 0f  // Confidence score for wake word detection
) {
    fun durationMs(): Long = System.currentTimeMillis() - timestamp
}
