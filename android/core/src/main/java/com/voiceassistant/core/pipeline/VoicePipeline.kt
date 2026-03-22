package com.voiceassistant.core.pipeline

import com.voiceassistant.core.audio.AudioCapture
import com.voiceassistant.core.audio.AudioPlayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Main voice pipeline controller
 * Manages the state machine and coordinates all components
 * NOTE: KWS wake word may not work on all devices due to memory constraints
 */
class VoicePipeline(
    private val config: PipelineConfig,
    private val kws: SherpaKWS,
    private val vad: SherpaVAD,
    private val asr: SherpaASR,
    private val tts: SherpaTTS,
    private val intentRouter: IntentRouter,
    private val audioCapture: AudioCapture,
    private val audioPlayer: AudioPlayer = AudioPlayer()
) {
    private val _state = MutableStateFlow(StateInfo(PipelineState.IDLE))
    val state: StateFlow<StateInfo> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJob: Job? = null
    private var initJob: Job? = null

    private val audioBuffer = mutableListOf<FloatArray>()
    private var silenceFrames = 0

    // Track initialization status
    private var isCoreInitialized = false // KWS + VAD
    private var isAsrLoaded = false
    private var isTtsLoaded = false

    // Flag to track if initialization failed
    private var initFailed = false

    fun start() {
        Timber.d("VoicePipeline starting")

        if (initFailed) {
            Timber.w("VoicePipeline initialization previously failed, skipping start")
            _state.value = StateInfo(PipelineState.IDLE, message = "语音功能暂不可用")
            return
        }

        // Initialize on first start
        if (!isCoreInitialized && initJob == null) {
            initJob = scope.launch {
                initializeCoreComponentsSafe()
            }
        }

        currentJob = scope.launch {
            // Wait for core initialization (with timeout)
            var waitCount = 0
            val maxWait = 100 // 10 seconds max
            while (!isCoreInitialized && waitCount < maxWait) {
                delay(100)
                waitCount++
            }

            if (!isCoreInitialized) {
                Timber.e("Core initialization timeout")
                _state.value = StateInfo(PipelineState.IDLE, message = "初始化超时，请重试")
                return@launch
            }

            _state.value = StateInfo(PipelineState.IDLE)
            startKWSListening()
        }
    }

    private suspend fun initializeCoreComponentsSafe() {
        Timber.d("Initializing core voice pipeline components (KWS + VAD)...")

        try {
            withContext(Dispatchers.IO) {
                try {
                    // Initialize KWS (Keyword Spotter)
                    val kwsResult = kws.initialize(config.modelPath + "/kws")
                    Timber.d("KWS initialized: $kwsResult")

                    // Initialize VAD (Voice Activity Detector)
                    val vadResult = vad.initialize(config.modelPath + "/vad")
                    Timber.d("VAD initialized: $vadResult")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize KWS/VAD - continuing without wake word")
                    // Don't throw - allow app to continue without wake word
                }
            }

            isCoreInitialized = true
            initJob = null
            Timber.d("Core voice pipeline components initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize core voice pipeline components")
            initJob = null
            initFailed = true
        }
    }

    /**
     * Lazy load ASR when needed for recognition
     */
    private suspend fun ensureAsrInitialized() {
        if (!isAsrLoaded) {
            try {
                Timber.d("Lazy loading ASR...")
                withContext(Dispatchers.IO) {
                    val asrResult = asr.initialize(config.modelPath + "/asr")
                    Timber.d("ASR initialized: $asrResult")
                    isAsrLoaded = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize ASR")
                throw e
            }
        }
    }

    /**
     * Lazy load TTS when needed for speech synthesis
     */
    private suspend fun ensureTtsInitialized() {
        if (!isTtsLoaded) {
            try {
                Timber.d("Lazy loading TTS...")
                withContext(Dispatchers.IO) {
                    val ttsResult = tts.initialize(config.modelPath + "/tts")
                    Timber.d("TTS initialized: $ttsResult")
                    isTtsLoaded = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize TTS")
                throw e
            }
        }
    }

    /**
     * Public method to initialize all models upfront
     */
    suspend fun initializeAllModels(): Boolean {
        return try {
            Timber.d("Manually initializing all models...")

            // Initialize core components (KWS + VAD)
            if (!isCoreInitialized) {
                initializeCoreComponentsSafe()
            }

            // Initialize ASR
            ensureAsrInitialized()

            // Initialize TTS
            ensureTtsInitialized()

            Timber.d("All models initialized successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize all models")
            false
        }
    }

    fun stop() {
        Timber.d("VoicePipeline stopping")
        currentJob?.cancel()
        initJob?.cancel()
        scope.cancel()
        audioCapture.stop()
    }

    fun interrupt() {
        Timber.d("VoicePipeline interrupted")
        currentJob?.cancel()
        audioCapture.stop()

        when (_state.value.state) {
            PipelineState.SPEAKING -> {
                try {
                    tts.stop()
                } catch (e: Exception) {
                    Timber.e(e, "Error stopping TTS")
                }
                // 停止后直接返回IDLE，不再自动开始录音
                transitionTo(PipelineState.IDLE)
            }
            PipelineState.IDLE -> {
                // Manual trigger - go directly to recording
                Timber.d("Manual trigger - starting recording")
                currentJob = scope.launch {
                    startRecording()
                }
            }
            else -> {
                // 先停止任何正在进行的操作，然后返回IDLE
                transitionTo(PipelineState.IDLE)
            }
        }
    }

    private fun startKWSListening() {
        if (!isCoreInitialized) {
            Timber.w("Cannot start KWS - not initialized")
            return
        }

        // If already recording, stop first
        if (audioCapture.isRecording) {
            Timber.d("AudioCapture is recording, stopping first before KWS")
            audioCapture.stop()
        }

        transitionTo(PipelineState.IDLE)

        // Start wake word detection
        try {
            var wakeWordTriggered = false
            audioCapture.start { audioChunk ->
                try {
                    // Prevent processing after wake word detected
                    if (wakeWordTriggered) return@start

                    // Process audio for wake word detection
                    if (kws.process(audioChunk)) {
                        // Wake word detected
                        wakeWordTriggered = true
                        Timber.d("Wake word detected!")
                        audioCapture.stop()
                        onWakeWordDetected()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in KWS audio processing")
                }
            }
            Timber.d("KWS listening started (wake word detection active)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start KWS listening")
        }
    }

    private fun onWakeWordDetected() {
        currentJob = scope.launch {
            Timber.d("Wake word detected")
            // Show wake word feedback
            transitionTo(PipelineState.WAKEWORD_DETECTED, message = "我在听...")
            // Short delay for visual feedback and audio system settle
            delay(500)
            transitionTo(PipelineState.LISTENING)
            // Ensure audio capture is fully stopped before starting recording
            if (audioCapture.isRecording) {
                Timber.d("Stopping audio capture before recording")
                audioCapture.stop()
                delay(200) // Wait for audio system to settle
            }
            startRecording()
        }
    }

    private fun startRecording() {
        // 如果已经在录音，先停止
        if (audioCapture.isRecording) {
            Timber.d("AudioCapture is recording, stopping first")
            audioCapture.stop()
        }

        transitionTo(PipelineState.RECORDING)

        audioBuffer.clear()
        silenceFrames = 0

        // maxSilenceFrames and maxRecordingFrames for VAD-based recording (currently unused)
        // val maxSilenceFrames = (config.sampleRate * config.silenceTimeoutSec).toInt() / config.frameSize
        // val maxRecordingFrames = (config.sampleRate * config.maxRecordingSec).toInt() / config.frameSize

        try {
            audioCapture.start { audioChunk ->
                try {
                    // Skip VAD for now, just collect all audio
                    // TODO: Re-enable VAD once it's working correctly
                    audioBuffer.add(audioChunk)

                    // Simple timeout based on audio buffer size (~10 seconds max)
                    val maxBufferSize = config.sampleRate * 10 / config.frameSize // 10 seconds
                    if (audioBuffer.size > maxBufferSize) {
                        val speechAudio = audioBuffer.flattenToFloatArray()
                        Timber.d("Recording stopped: max duration reached, audioSize=${speechAudio.size}")
                        scope.launch {
                            startRecognition(speechAudio)
                        }
                        audioCapture.stop()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in recording callback")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
            transitionTo(PipelineState.IDLE)
        }
    }

    private suspend fun startRecognition(audioData: FloatArray) {
        transitionTo(PipelineState.RECOGNIZING, message = "正在识别...")

        try {
            // 确保 ASR 已初始化（懒加载）
            ensureAsrInitialized()

            // 在 Default 调度器上执行 ASR 推理
            val text = withContext(Dispatchers.Default) {
                asr.recognize(audioData)
            }

            Timber.d("ASR result: $text")

            if (text.isNotBlank()) {
                _state.value = _state.value.copy(message = "你说: $text")
                processIntent(text)
            } else {
                // 识别为空，返回待机
                transitionTo(PipelineState.IDLE, message = "没听清，请再说一遍")
                if (isCoreInitialized) {
                    startKWSListening()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "ASR recognition failed")
            transitionTo(PipelineState.IDLE, message = "识别失败，请重试")
            if (isCoreInitialized) {
                startKWSListening()
            }
        }
    }

    private suspend fun processIntent(text: String) {
        transitionTo(PipelineState.THINKING, message = "正在处理...")

        try {
            val response = intentRouter.handle(text)
            speak(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to process intent")
            speak("处理失败，请重试")
        }
    }

    private suspend fun speak(text: String) {
        if (text.isBlank()) {
            transitionTo(PipelineState.IDLE)
            if (isCoreInitialized) {
                startKWSListening()
            }
            return
        }

        // Skip TTS due to memory issues - just show text response
        transitionTo(PipelineState.SPEAKING, message = text)

        try {
            // Simulate TTS playing by waiting a bit
            delay(1500)

            transitionTo(PipelineState.IDLE, message = "")
            if (isCoreInitialized) {
                startKWSListening()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to speak")
            transitionTo(PipelineState.IDLE, message = "播报失败")
            if (isCoreInitialized) {
                startKWSListening()
            }
        }
    }

    /**
     * Play audio using AudioPlayer and suspend until completion
     */
    private suspend fun playAudio(samples: FloatArray) = suspendCancellableCoroutine { cont ->
        try {
            audioPlayer.play(samples) {
                if (cont.isActive) {
                    cont.resumeWith(Result.success(Unit))
                }
            }

            cont.invokeOnCancellation {
                try {
                    audioPlayer.stop()
                } catch (e: Exception) {
                    Timber.e(e, "Error stopping audio player")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio")
            if (cont.isActive) {
                cont.resumeWith(Result.success(Unit))
            }
        }
    }

    private fun transitionTo(newState: PipelineState, message: String = "") {
        val oldState = _state.value.state
        _state.value = StateInfo(newState, message = message)
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
    val frameSize: Int = 512, // 32ms @ 16kHz (must match VAD windowSize)
    val silenceTimeoutSec: Float = 0.8f,
    val maxRecordingSec: Int = 30,
    val llmTimeoutMs: Long = 30000,
    val modelPath: String = "models" // Relative to assets
)