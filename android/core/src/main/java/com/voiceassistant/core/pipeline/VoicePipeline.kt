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
    private val asrManager: ASRManager,
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

        var kwsInitSuccess = false
        var vadInitSuccess = false

        try {
            withContext(Dispatchers.IO) {
                try {
                    // Initialize KWS (Keyword Spotter)
                    val kwsResult = kws.initialize(config.modelPath + "/kws")
                    Timber.d("KWS initialized: $kwsResult")
                    kwsInitSuccess = kwsResult
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize KWS - continuing without wake word")
                }

                try {
                    // Initialize VAD (Voice Activity Detector)
                    val vadResult = vad.initialize(config.modelPath + "/vad")
                    Timber.d("VAD initialized: $vadResult")
                    vadInitSuccess = vadResult
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize VAD - continuing without VAD")
                }
            }

            // Only mark as initialized if at least KWS succeeded
            // VAD failure is non-fatal, but KWS is required for wake word
            if (kwsInitSuccess) {
                isCoreInitialized = true
                initJob = null
                Timber.d("Core voice pipeline components initialized successfully")
            } else {
                // KWS failed - cannot use voice pipeline without wake word detection
                initJob = null
                initFailed = true
                Timber.e("KWS initialization failed - voice pipeline disabled")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize core voice pipeline components")
            initJob = null
            initFailed = true
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
            asrManager.ensureInitialized()

            // Initialize TTS
            ensureTtsInitialized()

            Timber.d("All models initialized successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize all models")
            false
        }
    }

    /**
     * Initialize ASR and TTS in the background with user-friendly progress updates.
     * Called on app startup to prepare voice recognition without blocking the UI.
     * This is non-blocking - it reports progress through state and completes asynchronously.
     */
    fun initializeInBackground() {
        // Skip if already initialized or already initializing
        if (asrManager.isLoaded() && isTtsLoaded) {
            return
        }

        // If core failed, don't try to initialize more
        if (initFailed) {
            return
        }

        Timber.d("Starting background initialization of ASR and TTS")
        transitionTo(PipelineState.INITIALIZING, "正在准备语音识别...")

        scope.launch {
            try {
                // Initialize ASR (the heavy one that takes time)
                if (!asrManager.isLoaded()) {
                    _state.value = _state.value.copy(message = "正在加载语音识别...")
                    asrManager.ensureInitialized()
                }

                // Initialize TTS
                if (!isTtsLoaded) {
                    _state.value = _state.value.copy(message = "正在加载语音合成...")
                    ensureTtsInitialized()
                }

                Timber.d("Background initialization complete")
                transitionTo(PipelineState.IDLE, "语音识别已就绪")
            } catch (e: Exception) {
                Timber.e(e, "Background initialization failed")
                transitionTo(PipelineState.IDLE, "语音识别准备就绪")
            }
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

        try {
            audioCapture.start { audioChunk ->
                try {
                    // 收集音频用于最终识别
                    audioBuffer.add(audioChunk)

                    // Simple timeout based on audio buffer size (~5 seconds max)
                    val maxBufferSize = config.sampleRate * 5 / config.frameSize // 5 seconds
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
            // 使用流式识别，实时显示部分结果
            var finalText = ""

            asrManager.recognizeStreaming(audioData, object : SherpaASR.RecognitionListener {
                override fun onPartialResult(text: String) {
                    // 实时更新部分识别结果
                    Timber.d("ASR partial: '$text'")
                    _state.value = _state.value.copy(message = "识别中: $text")
                }

                override fun onFinalResult(text: String) {
                    finalText = text
                    Timber.d("ASR final: '$text'")
                }

                override fun onEndpointDetected() {
                    Timber.d("ASR endpoint detected")
                }
            })

            if (finalText.isNotBlank()) {
                _state.value = _state.value.copy(message = "你说: $finalText")
                processIntent(finalText)
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

        transitionTo(PipelineState.SPEAKING, message = text)

        try {
            // Ensure TTS is initialized
            ensureTtsInitialized()

            // Synthesize text to audio
            val samples = tts.synthesize(text)

            if (samples.isEmpty()) {
                // TTS failed - fall back to text display
                Timber.w("TTS synthesis returned empty, falling back to text display")
                transitionTo(PipelineState.IDLE, message = text)
            } else {
                // Play synthesized audio and wait for completion
                playAudio(samples)

                transitionTo(PipelineState.IDLE, message = "")
            }

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