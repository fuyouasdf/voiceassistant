package com.voiceassistant.core.pipeline

import com.voiceassistant.core.audio.AudioCapture
import com.voiceassistant.core.intent.IntentRouter
import com.voiceassistant.core.sherpa.SherpaASR
import com.voiceassistant.core.sherpa.SherpaKWS
import com.voiceassistant.core.sherpa.SherpaTTS
import com.voiceassistant.core.sherpa.SherpaVAD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Main voice pipeline controller
 * Manages the state machine and coordinates all components
 */
class VoicePipeline(
    private val config: PipelineConfig,
    private val kws: SherpaKWS,
    private val vad: SherpaVAD,
    private val asr: SherpaASR,
    private val tts: SherpaTTS,
    private val intentRouter: IntentRouter,
    private val audioCapture: AudioCapture
) {
    private val _state = MutableStateFlow(StateInfo(PipelineState.IDLE))
    val state: StateFlow<StateInfo> = _state.asStateFlow()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJob: Job? = null
    
    private val audioBuffer = mutableListOf<FloatArray>()
    private var silenceFrames = 0
    
    fun start() {
        Timber.d("VoicePipeline starting")
        currentJob = scope.launch {
            _state.value = StateInfo(PipelineState.IDLE)
            startKWSListening()
        }
    }
    
    fun stop() {
        Timber.d("VoicePipeline stopping")
        currentJob?.cancel()
        scope.cancel()
        audioCapture.stop()
    }
    
    fun interrupt() {
        Timber.d("VoicePipeline interrupted")
        currentJob?.cancel()
        when (_state.value.state) {
            PipelineState.SPEAKING -> {
                tts.stop()
                transitionTo(PipelineState.LISTENING)
                currentJob = scope.launch {
                    startRecording()
                }
            }
            else -> {
                transitionTo(PipelineState.IDLE)
                currentJob = scope.launch {
                    startKWSListening()
                }
            }
        }
    }
    
    private fun startKWSListening() {
        transitionTo(PipelineState.IDLE)

        audioCapture.start { audioChunk ->
            if (kws.process(audioChunk)) {
                onWakeWordDetected()
            }
        }
    }
    
    private fun onWakeWordDetected() {
        currentJob = scope.launch {
            Timber.d("Wake word detected")
            transitionTo(PipelineState.LISTENING)
            
            // Optional: play beep sound
            // playBeep()
            
            startRecording()
        }
    }
    
    private fun startRecording() {
        transitionTo(PipelineState.RECORDING)

        audioBuffer.clear()
        silenceFrames = 0

        val maxSilenceFrames = (config.sampleRate * config.silenceTimeoutSec).toInt() / config.frameSize
        val maxRecordingFrames = (config.sampleRate * config.maxRecordingSec).toInt() / config.frameSize

        audioCapture.start { audioChunk ->
            val isSpeech = vad.process(audioChunk)

            if (isSpeech) {
                audioBuffer.add(audioChunk)
                silenceFrames = 0
            } else {
                silenceFrames++
            }

            // Stop condition: silence timeout or max duration
            if (silenceFrames > maxSilenceFrames || audioBuffer.size > maxRecordingFrames) {
                val speechAudio = audioBuffer.flattenToFloatArray()
                scope.launch {
                    startRecognition(speechAudio)
                }
                audioCapture.stop()
            }
        }
    }
    
    private suspend fun startRecognition(audioData: FloatArray) {
        transitionTo(PipelineState.RECOGNIZING)
        
        val text = asr.recognize(audioData)
        Timber.d("ASR result: $text")
        
        if (text.isNotBlank()) {
            processIntent(text)
        } else {
            // Silent failure - no feedback
            transitionTo(PipelineState.IDLE)
            startKWSListening()
        }
    }
    
    private suspend fun processIntent(text: String) {
        transitionTo(PipelineState.THINKING)
        
        val response = intentRouter.handle(text)
        
        speak(response)
    }
    
    private suspend fun speak(text: String) {
        transitionTo(PipelineState.SPEAKING)
        
        val audio = tts.synthesize(text)
        
        // Play audio and wait for completion
        // AudioPlayer.play(audio) { onComplete }
        
        // For now, simulate completion
        kotlinx.coroutines.delay(1000)
        
        transitionTo(PipelineState.IDLE)
        startKWSListening()
    }
    
    private fun transitionTo(newState: PipelineState) {
        val oldState = _state.value.state
        _state.value = StateInfo(newState)
        Timber.d("State transition: $oldState -> $newState")
    }
    
    private fun List<FloatArray>.flattenToFloatArray(): FloatArray {
        val size = sumOf { it.size }
        val result = FloatArray(size)
        var index = 0
        for (array in this) {
            for (value in array) {
                result[index++] = value
            }
        }
        return result
    }
}

/**
 * Pipeline configuration
 */
data class PipelineConfig(
    val sampleRate: Int = 16000,
    val frameSize: Int = 320, // 20ms @ 16kHz
    val silenceTimeoutSec: Float = 0.8f,
    val maxRecordingSec: Int = 30,
    val llmTimeoutMs: Long = 30000
)
