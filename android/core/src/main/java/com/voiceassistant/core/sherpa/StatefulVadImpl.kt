package com.voiceassistant.core.sherpa

import android.content.Context
import timber.log.Timber

/**
 * Implementation of StatefulVad that wraps SherpaVAD and tracks speech segments.
 */
class StatefulVadImpl(private val context: Context) : StatefulVad {

    private var sherpaVad: SherpaVAD? = null
    private var isInitialized = false
    private var config: VadConfig = VadConfig()

    // Current state
    private var state = VadState.IDLE

    // Timing tracking
    private var speechStartTime = 0L
    private var lastSpeechTime = 0L
    private var speechEndedTime = 0L

    // Sample rate for timing calculations
    private val sampleRate = 16000

    // Buffer for holding audio until we have enough for VAD
    private val audioBuffer = mutableListOf<FloatArray>()
    private val requiredSamples = 512 // Sherpa VAD requires 512 samples (32ms)

    override fun initialize(modelPath: String, config: VadConfig): Boolean {
        return try {
            this.config = config

            // Create and initialize Sherpa VAD
            val vadImpl = SherpaVADImpl(context)
            val success = vadImpl.initialize(modelPath)

            if (success) {
                sherpaVad = vadImpl
                isInitialized = true
                reset()
                Timber.d("StatefulVad initialized: minSpeech=${config.minSpeechDurationMs}ms, silence=${config.minSilenceDurationMs}ms")
                true
            } else {
                Timber.e("Failed to initialize Sherpa VAD")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "StatefulVad initialization failed")
            false
        }
    }

    override fun process(audio: FloatArray): StatefulVadResult {
        if (!isInitialized) {
            return StatefulVadResult(state = VadState.IDLE)
        }

        // Buffer incoming audio until we have enough
        audioBuffer.add(audio)

        // Process when we have enough samples
        while (audioBuffer.totalSamples() >= requiredSamples) {
            val chunk = audioBuffer.consumeFirst(requiredSamples)
            processChunk(chunk)
        }

        // Check for timeout regardless of state
        checkTimeout()

        return StatefulVadResult(
            state = state,
            speechStarted = state == VadState.SPEECH_STARTED && wasJustStarted(),
            speechEnded = state == VadState.SPEECH_ENDED,
            isSpeech = sherpaVad?.process(audioBuffer.flatten()) ?: false
        )
    }

    private fun processChunk(chunk: FloatArray) {
        val vad = sherpaVad ?: return
        val isSpeech = vad.process(chunk)

        when (state) {
            VadState.IDLE -> {
                if (isSpeech) {
                    state = VadState.SPEECH_STARTED
                    speechStartTime = System.currentTimeMillis()
                    lastSpeechTime = speechStartTime
                    Timber.d("StatefulVad: SPEECH_STARTED")
                }
            }
            VadState.SPEECH_STARTED -> {
                if (isSpeech) {
                    lastSpeechTime = System.currentTimeMillis()

                    // Check max speech duration
                    val speechDuration = lastSpeechTime - speechStartTime
                    if (speechDuration > config.maxSpeechDurationMs) {
                        Timber.d("StatefulVad: Max speech duration reached (${speechDuration}ms)")
                        state = VadState.SPEECH_ENDED
                        speechEndedTime = lastSpeechTime
                    }
                } else {
                    // Check min silence duration to confirm speech end
                    val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                    if (silenceDuration >= config.minSilenceDurationMs) {
                        // Verify it's really silence by checking next chunk
                        // For now, we'll trust the VAD
                        Timber.d("StatefulVad: SPEECH_ENDED (silence=${silenceDuration}ms)")
                        state = VadState.SPEECH_ENDED
                        speechEndedTime = System.currentTimeMillis()
                    }
                }
            }
            VadState.SPEECH_ENDED -> {
                // Already ended, do nothing until reset
            }
        }
    }

    private fun checkTimeout() {
        if (state == VadState.SPEECH_STARTED) {
            val elapsed = System.currentTimeMillis() - lastSpeechTime
            if (elapsed > config.silenceTimeoutMs) {
                Timber.d("StatefulVad: TIMEOUT (${elapsed}ms of silence)")
                state = VadState.SPEECH_ENDED
                speechEndedTime = System.currentTimeMillis()
            }
        }
    }

    private fun wasJustStarted(): Boolean {
        // Check if this is the first frame after state transition
        return state == VadState.SPEECH_STARTED &&
                (System.currentTimeMillis() - speechStartTime) < 100 // Within 100ms of start
    }

    override fun getState(): VadState = state

    override fun reset() {
        state = VadState.IDLE
        speechStartTime = 0L
        lastSpeechTime = 0L
        speechEndedTime = 0L
        audioBuffer.clear()
        sherpaVad?.reset()
        Timber.d("StatefulVad: reset to IDLE")
    }

    override fun release() {
        sherpaVad?.release()
        sherpaVad = null
        isInitialized = false
        reset()
        Timber.d("StatefulVad: released")
    }

    private fun MutableList<FloatArray>.totalSamples(): Int = this.sumOf { it.size }

    private fun MutableList<FloatArray>.consumeFirst(n: Int): FloatArray {
        val result = mutableListOf<FloatArray>()
        var remaining = n
        while (remaining > 0 && this.isNotEmpty()) {
            val first = this.removeAt(0)
            if (first.size <= remaining) {
                result.add(first)
                remaining -= first.size
            } else {
                // Split the array
                result.add(first.copyOfRange(0, remaining))
                // Put the rest back at the beginning
                this.add(0, first.copyOfRange(remaining, first.size))
                remaining = 0
            }
        }
        return result.flattenToFloatArray()
    }

    private fun List<FloatArray>.flattenToFloatArray(): FloatArray {
        val size = this.sumOf { it.size }
        val result = FloatArray(size)
        var index = 0
        for (array in this) {
            for (value in array) {
                result[index++] = value
            }
        }
        return result
    }

    private fun MutableList<FloatArray>.flatten(): FloatArray {
        return this.flattenToFloatArray()
    }
}
