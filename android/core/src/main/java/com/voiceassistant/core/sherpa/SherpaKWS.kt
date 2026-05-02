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
 * Result of KWS process() call
 */
data class KWSResult(
    val detected: Boolean,
    val keyword: String = "",
    val confidence: Float = 0f
)

/**
 * Interface for Keyword Spotting (Wake Word) engine
 */
interface SherpaKWS {

    /**
     * Initialize the KWS engine with model
     * @param modelPath Path to the model directory
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean

    /**
     * Process audio chunk and detect wake word
     * @param audio Audio samples (16kHz, float)
     * @return KWSResult with detection status, keyword, and confidence
     */
    fun process(audio: FloatArray): KWSResult

    /**
     * Set detection sensitivity (0.0 - 1.0)
     */
    fun setSensitivity(sensitivity: Float)

    /**
     * Reload keywords from a new keywords file path.
     * Thread-safe: acquires lock, releases old KeywordSpotter, creates new one.
     * @param keywordsFilePath Absolute path to the new keywords.txt
     * @param threshold Optional new threshold. If null, keeps current threshold.
     * @return true if reload successful
     */
    fun reloadKeywords(keywordsFilePath: String, threshold: Float? = null): Boolean

    /**
     * Release resources
     */
    fun release()
}
