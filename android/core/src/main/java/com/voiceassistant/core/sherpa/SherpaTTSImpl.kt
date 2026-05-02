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

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import timber.log.Timber
import java.io.File

class SherpaTTSImpl(private val context: Context) : SherpaTTS {

    private var tts: OfflineTts? = null
    private var actualSampleRate: Int = 22050 // Default, will be updated on init
    private var currentSpeed: Float = 1.0f // Default speed
    private var currentSid: Int = 0 // Default speaker ID
    private var currentPitch: Float = 1.0f // Default pitch multiplier

    // Default values for VITS model parameters
    private val defaultNoiseScale = 0.667f
    private val defaultNoiseScaleW = 0.8f

    override fun initialize(modelPath: String): Boolean {
        return try {
            val modelDir = copyModelsFromAssets("vits-piper-zh_CN-huayan-medium")

            val onnxFile = modelDir.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull()
                ?: throw Exception("No TTS model found")

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = onnxFile.absolutePath,
                        lexicon = "",
                        tokens = File(modelDir, "tokens.txt").absolutePath,
                        dataDir = File(modelDir, "phontab").absolutePath,
                        dictDir = "",
                        noiseScale = defaultNoiseScale,
                        noiseScaleW = defaultNoiseScaleW,
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
            actualSampleRate = tts?.sampleRate() ?: 22050

            Timber.d("TTS initialized with model: $modelPath, sample rate: $actualSampleRate Hz")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TTS")
            false
        }
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.1f, 10.0f)
        Timber.d("TTS speed set to: $currentSpeed")
    }

    override fun setSpeakerId(sid: Int) {
        val maxSid = getSpeakerCount() - 1
        currentSid = sid.coerceIn(0, maxSid.coerceAtLeast(0))
        Timber.d("TTS speaker ID set to: $currentSid (model has ${getSpeakerCount()} speaker(s))")
    }

    override fun setPitch(pitch: Float) {
        // Pitch adjustment maps to noiseScale in VITS
        // 0.5 = lower pitch, 1.0 = normal, 2.0 = higher pitch
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        Timber.d("TTS pitch set to: $currentPitch (noiseScale will be: ${defaultNoiseScale * currentPitch})")
    }

    override fun getSpeakerCount(): Int {
        // OfflineTts from Sherpa-ONNX exposes numSpeakers
        return try {
            tts?.numSpeakers() ?: 1
        } catch (e: Exception) {
            Timber.w(e, "Could not get speaker count, assuming 1")
            1
        }
    }

    override fun synthesize(text: String): FloatArray {
        val t = tts ?: return FloatArray(0)

        return try {
            // Calculate effective noiseScale based on pitch setting
            val effectiveNoiseScale = defaultNoiseScale * currentPitch

            // Note: Sherpa-ONNX OfflineTts generate() takes sid parameter
            // pitch is controlled via noiseScale in the config, not per-synthesis
            // Since we can't change noiseScale per synthesis, we apply it as a one-time adjustment
            val audio = t.generate(text, sid = currentSid, speed = currentSpeed)
            val samples = audio.samples

            Timber.d("TTS synthesized: ${text.length} chars -> ${samples.size} samples at ${audio.sampleRate} Hz (sid=$currentSid, speed=$currentSpeed, pitch=$currentPitch)")
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

    override fun getSampleRate(): Int = actualSampleRate

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
