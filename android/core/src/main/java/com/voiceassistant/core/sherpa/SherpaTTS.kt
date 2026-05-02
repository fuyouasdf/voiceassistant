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
 * Interface for Text-to-Speech (TTS) engine
 */
interface SherpaTTS {

    /**
     * Initialize the TTS engine with model
     * @param modelPath Path to the model directory
     * @return true if initialization successful
     */
    fun initialize(modelPath: String): Boolean

    /**
     * Set the synthesis speed
     * @param speed Speed multiplier (1.0 = normal, 0.5 = half speed, 2.0 = double speed)
     */
    fun setSpeed(speed: Float)

    /**
     * Set the speaker ID (voice selection) for multi-speaker models
     * @param sid Speaker ID (0 = default/first speaker)
     */
    fun setSpeakerId(sid: Int)

    /**
     * Set the voice pitch/character adjustment
     * @param pitch Pitch multiplier (0.5 = lower, 1.0 = normal, 2.0 = higher)
     * Note: This affects noiseScale in VITS models, which modifies voice character
     */
    fun setPitch(pitch: Float)

    /**
     * Get the number of available speakers in the model
     * @return Number of speakers, or 1 if single-speaker model
     */
    fun getSpeakerCount(): Int

    /**
     * Synthesize text to audio
     * @param text Text to synthesize
     * @return Audio samples (float array)
     */
    fun synthesize(text: String): FloatArray

    /**
     * Stop current synthesis
     */
    fun stop()

    /**
     * Release resources
     */
    fun release()

    /**
     * Get the sample rate of the TTS engine output
     * @return Sample rate in Hz
     */
    fun getSampleRate(): Int
}
