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
 * Interface for Voice Activity Detection (VAD) engine
 */
interface SherpaVAD {

    /**
     * Initialize the VAD engine with model
     * @param modelPath Path to the model directory
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean

    /**
     * Process audio chunk and detect speech
     * @param audio Audio samples (16kHz, float)
     * @return true if speech detected
     */
    fun process(audio: FloatArray): Boolean

    /**
     * Reset VAD state
     */
    fun reset()

    /**
     * Release resources
     */
    fun release()
}
