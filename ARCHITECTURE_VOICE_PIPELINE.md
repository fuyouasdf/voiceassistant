# 语音管道架构设计

> 从麦克风输入到语音输出的完整数据流

**版本**: 1.4
**日期**: 2026-03-24

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           语音管道 (VoicePipeline)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │  Audio   │───→│   KWS    │───→│   VAD    │───→│   ASR    │              │
│  │ Capture  │    │(唤醒检测) │    │(端点检测) │    │(语音识别) │              │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘              │
│       ↑                                            │                        │
│       │                                            ↓                        │
│       │                                     ┌──────────┐                   │
│       │                                     │  Intent  │                   │
│       │                                     │  Router  │                   │
│       │                                     └──────────┘                   │
│       │                                            │                        │
│       │                                            ↓                        │
│       │                                     ┌──────────┐                   │
│       │                                     │   LLM    │                   │
│       │                                     │  (远程)  │                   │
│       │                                     └──────────┘                   │
│       │                                            │                        │
│       │                                            ↓                        │
│       │                                     ┌──────────┐                   │
│       └─────────────────────────────────────│   TTS    │←──────────────────┤
│                                             │(语音合成) │                   │
│                                             └──────────┘                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 状态机设计

语音管道是一个状态机，各阶段严格串行：

```
                    ┌─────────────┐
         ┌─────────→│ INITIALIZING│←───────┐
         │          │  (初始化中) │        │
         │          └──────┬──────┘        │
         │                 │ 初始化完成     │
         │                 ↓                │
         │          ┌─────────────┐         │
         │    ┌────→│   IDLE     │─────────┤
         │    │     │  (待机)    │         │
         │    │     └──────┬──────┘         │
         │    │            │ 检测到唤醒词    │
         │    │            ↓                │
         │    │     ┌─────────────┐         │
         │    │     │WAKEWORD_    │         │
         │    │     │DETECTED     │         │
         │    │     └──────┬──────┘         │
         │    │            │ 反馈完成       │
         │    │            ↓                │
         │    │     ┌─────────────┐         │
         │    ├────→│  LISTENING  │         │
         │    │     │  (聆听中)   │         │
         │    │     └──────┬──────┘         │
         │    │            │ VAD检测到语音   │
         │    │            ↓                │
         │    │     ┌─────────────┐         │
         │    │     │  RECORDING  │         │
         │    │     │  (录音中)   │         │
         │    │     └──────┬──────┘         │
         │    │            │ VAD检测到静音   │
         │    │            ↓                │
         │    │     ┌─────────────┐         │
         │    │     │  RECOGNIZING│         │
         │    │     │  (识别中)   │         │
         │    │     └──────┬──────┘         │
         │    │            │ ASR完成        │
         │    │            ↓                │
         │    │     ┌─────────────┐         │
         │    └────→│  THINKING   │─────────┘
         │          │  (处理中)   │  (打断)
         │          └──────┬──────┘
         │                 │ 意图处理完成
         │                 ↓
         │          ┌─────────────┐
         │          │  SPEAKING   │
         │          │  (播报中)   │
         │          └──────┬──────┘
         │                 │ TTS完成/被打断
         └─────────────────┘
```

### 状态说明

| 状态 | 说明 | 可转移 |
|------|------|--------|
| `INITIALIZING` | 正在初始化模型 | `IDLE` |
| `IDLE` | 待机，KWS持续检测 | `LISTENING` |
| `WAKEWORD_DETECTED` | 唤醒词检测到，显示反馈 | `LISTENING` |
| `LISTENING` | 唤醒成功，等待语音 | `RECORDING`, `IDLE`(超时) |
| `RECORDING` | 正在录音 | `RECOGNIZING`, `IDLE`(取消) |
| `RECOGNIZING` | ASR处理中 | `THINKING` |
| `THINKING` | 意图路由（本地规则） | `SPEAKING`, `IDLE`(打断) |
| `SPEAKING` | TTS播报 | `IDLE`, `LISTENING`(打断) |

- 音乐意图解析优先级：`停止/暂停/继续/切歌` 必须先于 `播放` 关键词匹配，避免“停止播放”被误判成“播放”。

### 初始化就绪判定

- `start()` 的“核心就绪”判定以 **KWS 初始化成功** 为准，避免在 VAD/StatefulVAD 尚在初始化时误报“初始化超时”。
- `start()` 在进入待机前会等待核心初始化最多 **30 秒**（`delay(100ms) * 300`），超时后返回“初始化超时，请重试”。
- VAD 与 StatefulVAD 属于增强能力，继续在后台完成初始化；其失败不会阻断唤醒待机进入 `IDLE`。

### 录音结束策略

- `RECORDING` 阶段优先使用 `StatefulVad` 进行端点检测：检测到“语音结束”后立即进入 `RECOGNIZING`。
- 保留“最大录音时长”作为兜底，防止极端环境下无法检测端点导致长时间不返回。
- 唤醒词触发后会清空 pre-wake 缓冲，避免唤醒词本身被带入 ASR 文本。

---

## 核心类设计

### 1. VoicePipeline (主控制器)

```kotlin
class VoicePipeline(
    private val config: PipelineConfig,
    private val kws: KWSEngine,
    private val vad: VADEngine,
    private val asr: ASREngine,
    private val tts: TTSEngine,
    private val intentRouter: IntentRouter,
    private val audioCapture: AudioCapture,
    private val ttsEnabledProvider: () -> Boolean = { true }  // TTS 开关
) {
    private val _state = MutableStateFlow(PipelineState.IDLE)
    val state: StateFlow<PipelineState> = _state.asStateFlow()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJob: Job? = null
    
    // 音频环形缓冲区，用于保存唤醒词前的音频（可选）
    private val audioBuffer = CircularAudioBuffer(
        size = config.sampleRate * config.bufferSeconds
    )
    
    fun start() {
        scope.launch {
            _state.value = PipelineState.IDLE
            startKWSListening()
        }
    }
    
    fun stop() {
        currentJob?.cancel()
        scope.cancel()
        audioCapture.stop()
    }
    
    // 打断当前流程，回到IDLE或LISTENING
    fun interrupt() {
        currentJob?.cancel()
        when (_state.value) {
            PipelineState.SPEAKING -> {
                tts.stop()
                _state.value = PipelineState.LISTENING
                startRecording()
            }
            else -> {
                _state.value = PipelineState.IDLE
                startKWSListening()
            }
        }
    }
    
    private suspend fun startKWSListening() {
        audioCapture.start { audioChunk ->
            audioBuffer.write(audioChunk)
            
            if (kws.process(audioChunk)) {
                // 检测到唤醒词
                onWakeWordDetected()
            }
        }
    }
    
    private fun onWakeWordDetected() {
        currentJob = scope.launch {
            _state.value = PipelineState.LISTENING
            
            // 播放提示音（可选）
            playBeep()
            
            // 开始录音流程
            startRecording()
        }
    }
    
    private suspend fun startRecording() {
        _state.value = PipelineState.RECORDING
        
        val speechBuffer = mutableListOf<FloatArray>()
        var silenceFrames = 0
        val maxSilenceFrames = config.sampleRate / config.frameSize * config.silenceTimeoutSec
        val maxRecordingFrames = config.sampleRate / config.frameSize * config.maxRecordingSec
        
        audioCapture.start { audioChunk ->
            // VAD检测
            val isSpeech = vad.process(audioChunk)
            
            if (isSpeech) {
                speechBuffer.add(audioChunk)
                silenceFrames = 0
            } else {
                silenceFrames++
            }
            
            // 结束条件：静音超时 或 录音过长
            if (silenceFrames > maxSilenceFrames || speechBuffer.size > maxRecordingFrames) {
                val speechAudio = speechBuffer.flatten()
                startRecognition(speechAudio)
                return@start // 停止录音
            }
        }
    }
    
    private suspend fun startRecognition(audioData: FloatArray) {
        _state.value = PipelineState.RECOGNIZING
        
        val text = withContext(Dispatchers.Default) {
            asr.recognize(audioData)
        }
        
        if (text.isNotBlank()) {
            processIntent(text)
        } else {
            // 识别为空，回到待机
            _state.value = PipelineState.IDLE
            startKWSListening()
        }
    }
    
    private suspend fun processIntent(text: String) {
        _state.value = PipelineState.THINKING
        
        val response = intentRouter.handle(text)
        
        speak(response)
    }
    
    private suspend fun speak(text: String) {
        // TTS 开关：关闭时跳过语音合成，直接显示文字
        if (!ttsEnabledProvider()) {
            _state.value = PipelineState.IDLE
            startKWSListening()
            return
        }

        _state.value = PipelineState.SPEAKING

        val audio = tts.synthesize(text)

        AudioPlayer.play(audio) {
            // 播放完成回调
            _state.value = PipelineState.IDLE
            startKWSListening()
        }
    }
}

// 配置数据类
data class PipelineConfig(
    val sampleRate: Int = 16000,
    val frameSize: Int = 320,  // 20ms @ 16kHz
    val bufferSeconds: Int = 1,  // 唤醒前缓存1秒
    val silenceTimeoutSec: Float = 0.8f,  // 静音0.8秒结束录音
    val maxRecordingSec: Int = 30,  // 最大录音30秒
    val enablePreBuffer: Boolean = true  // 是否使用唤醒前音频
)

// 状态枚举
enum class PipelineState {
    IDLE, LISTENING, RECORDING, RECOGNIZING, THINKING, SPEAKING
}
```

### 2. 音频采集 (AudioCapture)

```kotlin
class AudioCapture(
    private val sampleRate: Int = 16000,
    private val bufferSize: Int = 320
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun start(onAudioChunk: (FloatArray) -> Unit) {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        
        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build())
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
        
        audioRecord?.startRecording()
        
        recordingJob = scope.launch {
            val buffer = FloatArray(bufferSize)
            
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, bufferSize, AudioRecord.READ_BLOCKING) ?: 0
                if (read > 0) {
                    onAudioChunk(buffer.copyOf(read))
                }
            }
        }
    }
    
    fun stop() {
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
```

### 3. 音频播放器 (AudioPlayer)

> **重要**: TTS 输出为 FloatArray (22050Hz)，AudioTrack 需使用 PCM 16-bit 格式播放

```kotlin
class AudioPlayer {
    private var audioTrack: AudioTrack? = null

    fun play(samples: FloatArray, sampleRate: Int = 22050, onComplete: () -> Unit) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT  // 使用 16-bit 而非 FLOAT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        Thread {
            // Float (-1.0 ~ 1.0) → Short (-32768 ~ 32767)
            val shortSamples = ShortArray(samples.size) { i ->
                (samples[i] * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            audioTrack?.write(shortSamples, 0, shortSamples.size)

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            onComplete()
        }.start()
    }
}
```

### 4. 环形音频缓冲区 (CircularAudioBuffer)

```kotlin
class CircularAudioBuffer(size: Int) {
    private val buffer = FloatArray(size)
    private var writeIndex = 0
    private val lock = Any()
    
    fun write(data: FloatArray) {
        synchronized(lock) {
            for (sample in data) {
                buffer[writeIndex] = sample
                writeIndex = (writeIndex + 1) % buffer.size
            }
        }
    }
    
    // 读取最近N秒的音频（用于唤醒前音频拼接）
    fun readLast(seconds: Float, sampleRate: Int): FloatArray {
        synchronized(lock) {
            val samples = (seconds * sampleRate).toInt()
            val result = FloatArray(min(samples, buffer.size))
            
            for (i in result.indices) {
                val index = (writeIndex - samples + i + buffer.size) % buffer.size
                result[i] = buffer[index]
            }
            
            return result
        }
    }
    
    fun clear() {
        synchronized(lock) {
            buffer.fill(0f)
            writeIndex = 0
        }
    }
}
```

### 4. 音频预处理器 (AudioPreprocessor)

```kotlin
class AudioPreprocessor(
    private val config: PreprocessorConfig
) {
    // 降噪器（可选）
    private val denoiser: Denoiser? = if (config.enableDenoise) {
        RNNoiseDenoiser()
    } else null
    
    // AGC自动增益
    private val agc = AutomaticGainControl(
        targetLevel = config.targetLevel,
        maxGain = config.maxGain
    )
    
    // 预加重滤波器
    private var preEmphasisState = 0f
    
    fun process(audio: FloatArray): FloatArray {
        var result = audio
        
        // 1. 降噪
        if (denoiser != null) {
            result = denoiser.process(result)
        }
        
        // 2. AGC
        if (config.enableAGC) {
            result = agc.process(result)
        }
        
        // 3. 预加重（提升高频）
        if (config.enablePreEmphasis) {
            result = preEmphasis(result)
        }
        
        // 4. 归一化
        result = normalize(result)
        
        return result
    }
    
    private fun preEmphasis(audio: FloatArray): FloatArray {
        val result = FloatArray(audio.size)
        result[0] = audio[0] - 0.97f * preEmphasisState
        for (i in 1 until audio.size) {
            result[i] = audio[i] - 0.97f * audio[i - 1]
        }
        preEmphasisState = audio.last()
        return result
    }
    
    private fun normalize(audio: FloatArray): FloatArray {
        val maxAmp = audio.maxOf { abs(it) }
        return if (maxAmp > 1.0f) {
            audio.map { it / maxAmp * 0.95f }.toFloatArray()
        } else audio
    }
}

data class PreprocessorConfig(
    val enableDenoise: Boolean = true,
    val enableAGC: Boolean = true,
    val enablePreEmphasis: Boolean = true,
    val targetLevel: Float = 0.3f,
    val maxGain: Float = 10f
)
```

### 5. ASR 模块

#### 5.1 接口定义 (SherpaASR.kt)

```kotlin
interface SherpaASR {
    interface RecognitionListener {
        fun onPartialResult(text: String)      // 实时中间结果
        fun onFinalResult(text: String)       // 最终识别结果
        fun onEndpointDetected()               // 端点检测到（用户停止说话）
    }

    fun initialize(modelPath: String, provider: String = "cpu"): Boolean
    suspend fun recognize(audio: FloatArray): String
    suspend fun recognizeStreaming(audio: FloatArray, listener: RecognitionListener)
    fun reset()
    fun release()
}
```

#### 5.2 ASRManager 生命周期管理

```kotlin
class ASRManager(
    private val asr: SherpaASR,
    private val modelPath: String
) {
    private var isLoaded = false
    private val provider: String = "cpu"

    suspend fun ensureInitialized() {
        if (isLoaded) return
        withContext(Dispatchers.IO) {
            asr.initialize(modelPath, provider)
            isLoaded = true
        }
    }

    suspend fun recognize(audioData: FloatArray): String {
        ensureInitialized()
        return withContext(Dispatchers.Default) {
            asr.recognize(audioData)
        }
    }

    suspend fun recognizeStreaming(
        audioData: FloatArray,
        listener: SherpaASR.RecognitionListener
    ) {
        ensureInitialized()
        withContext(Dispatchers.Default) {
            asr.recognizeStreaming(audioData, listener)
        }
    }
}
```

#### 5.3 流式识别实现 (SherpaASRImpl.kt)

使用 **Sherpa-ONNX OnlineRecognizer** 进行流式识别，模型为 `zipformer2`：

```kotlin
class SherpaASRImpl(private val context: Context) : SherpaASR {

    private var recognizer: OnlineRecognizer? = null

    override fun initialize(modelPath: String, provider: String): Boolean {
        val modelDir = modelPath.substringAfterLast("/")

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder.int8.onnx",
                    decoder = "$modelDir/decoder.onnx",
                    joiner = "$modelDir/joiner.int8.onnx"
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 4,
                provider = provider,
                modelType = "zipformer2"
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 4.0f, 0.0f),   // 非语音连续超时
                rule2 = EndpointRule(true, 2.5f, 0.0f),   // 语音段落后静音
                rule3 = EndpointRule(false, 0.0f, 30.0f)  // 最大30秒
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search"
        )

        recognizer = OnlineRecognizer(assetManager = context.assets, config = config)
        return true
    }

    override suspend fun recognizeStreaming(
        audio: FloatArray,
        listener: SherpaASR.RecognitionListener
    ) {
        val r = recognizer ?: return
        val stream = r.createStream()
        r.reset(stream)

        val bufferSize = (0.1 * 16000).toInt() // 100ms chunk

        // 1. 处理所有音频数据
        var offset = 0
        while (offset < audio.size) {
            val end = minOf(offset + bufferSize, audio.size)
            val chunk = audio.copyOfRange(offset, end)
            stream.acceptWaveform(chunk, sampleRate = 16000)
            while (r.isReady(stream)) { r.decode(stream) }
            offset = end
        }

        // 2. 检查endpoint并添加尾部填充
        if (r.isEndpoint(stream)) {
            val tailPaddings = FloatArray((0.8 * 16000).toInt())
            stream.acceptWaveform(tailPaddings, sampleRate = 16000)
            while (r.isReady(stream)) { r.decode(stream) }
        }

        // 3. 获取最终结果
        val text = r.getResult(stream).text ?: ""
        if (text.isNotEmpty()) listener.onFinalResult(text)

        // 4. 清理
        r.reset(stream)
        stream.release()
    }
}
```

#### 5.4 部分结果过滤器 (PartialResultFilter)

用于过滤重复的中间识别结果，避免UI每字一行更新：

```kotlin
class PartialResultFilter {
    private var lastText = ""

    fun shouldNotify(newText: String): Boolean {
        // 只有新增文字时才通知
        if (newText.isNotEmpty() && newText.length > lastText.length) {
            lastText = newText
            return true
        }
        return false
    }

    fun reset() { lastText = "" }
}
```

#### 5.5 模型配置 (ModelConfig.kt)

从 `assets/models/model_config.json` 加载模型配置：

```kotlin
class ModelConfig(private val context: Context) {
    fun getModelDir(type: ModelType): File
    fun getModelFile(type: ModelType, fileKey: String): String
    fun isModelReady(type: ModelType): Boolean
}

enum class ModelType { KWS, ASR, TTS }

// ASR 模型信息
// 名称: sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30
// 下载: https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.30/...
```

### 7. VAD引擎接口

```kotlin
interface VADEngine {
    fun initialize(modelPath: String): Boolean
    fun process(audio: FloatArray): Boolean  // 返回是否检测到语音
    fun reset()
    fun release()
}

// Sherpa-ONNX VAD实现
class SherpaVADEngine : VADEngine {
    private var vad: SherpaOnnxVoiceActivityDetector? = null
    
    override fun initialize(modelPath: String): Boolean {
        return try {
            val config = VadModelConfig(
                sileroVadModel = SileroVadModelConfig(
                    model = "$modelPath/silero_vad.onnx"
                ),
                sampleRate = 16000,
                numThreads = 1
            )
            vad = SherpaOnnxVoiceActivityDetector(config, bufferSizeInSeconds = 30)
            true
        } catch (e: Exception) {
            Log.e("VAD", "初始化失败", e)
            false
        }
    }
    
    override fun process(audio: FloatArray): Boolean {
        vad?.acceptWaveform(audio)
        return vad?.isSpeechDetected() ?: false
    }
    
    override fun reset() {
        vad?.reset()
    }
    
    override fun release() {
        vad?.close()
        vad = null
    }
}

// 简单能量阈值VAD（备用方案）
class EnergyVADEngine(
    private val threshold: Float = 0.01f,
    private val minSpeechFrames: Int = 5
) : VADEngine {
    private var speechFrames = 0
    
    override fun initialize(modelPath: String) = true
    
    override fun process(audio: FloatArray): Boolean {
        val energy = sqrt(audio.map { it * it }.average().toFloat())
        val isSpeech = energy > threshold
        
        if (isSpeech) {
            speechFrames++
        } else {
            speechFrames = max(0, speechFrames - 1)
        }
        
        return speechFrames >= minSpeechFrames
    }
    
    override fun reset() {
        speechFrames = 0
    }
    
    override fun release() {}
}
```

---

## 音频流处理流程

### 完整数据流

```
麦克风输入
    │
    ▼
┌─────────────────┐
│ AudioRecord     │  ← 16kHz, 16bit, 单声道
│ (原始PCM)       │
└────────┬────────┘
         │ FloatArray
         ▼
┌─────────────────┐
│ AudioPreprocessor│
│ · 降噪 (可选)   │
│ · AGC           │
│ · 预加重        │
│ · 归一化        │
└────────┬────────┘
         │ 处理后音频
         ▼
┌─────────────────┐     否      ┌─────────────┐
│ KWS检测         │────────────→│ 继续监听    │
│ (唤醒词?)       │             │ (IDLE状态)  │
└────────┬────────┘             └─────────────┘
         │ 是
         ▼
┌─────────────────┐
│ 播放提示音      │
│ (可选)          │
└────────┬────────┘
         ▼
┌─────────────────┐
│ VAD检测         │
│ (语音开始?)     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     否      ┌─────────────┐
│ 录音缓冲区      │────────────→│ 静音超时    │
│ (累积音频)      │             │ 结束录音    │
└────────┬────────┘             └─────────────┘
         │ 是 (持续有语音)
         ▼
┌─────────────────┐
│ ASR识别         │
│ (音频→文字)     │
└────────┬────────┘
         │ String
         ▼
┌─────────────────┐
│ 意图路由        │
│ + LLM处理       │
└────────┬────────┘
         │ String
         ▼
┌─────────────────┐
│ TTS合成         │
│ (文字→音频)     │
└────────┬────────┘
         │ FloatArray (16000Hz, Float samples)
         ▼
┌─────────────────────────────────────┐
│ AudioPlayer                         │
│ · PCM 16-bit 输出 (非 FLOAT)        │
│ · Float → Short 转换                │
│ · SherpaTTS sample rate: 16000 Hz   │
└─────────────────┬───────────────────┘
                  ▼
┌─────────────────┐
│ AudioTrack      │
│ (播放)          │
└─────────────────┘
```

### 关键时序

```
时间轴 →

KWS:    ──────────────────●─────────────────────────────
                        唤醒

VAD:    ────────────────────────┬───────────┬───────────
                               语音开始    语音结束

ASR:    ────────────────────────────────────┬───────────
                                           识别中

TTS:    ────────────────────────────────────────────┬───
                                                   播报

状态:   [IDLE]        [LISTENING] [RECORDING] [THINKING] [SPEAKING] [IDLE]
```

---

## 并发与线程模型

```
主线程 (UI)
    │
    ├── 状态更新 (StateFlow)
    ├── UI渲染
    └── 配置变更

IO线程池
    │
    ├── AudioCapture (录音)
    ├── 模型加载
    └── 网络请求 (LLM API)

Default线程池
    │
    ├── KWS推理
    ├── VAD推理
    ├── ASR推理
    └── TTS推理

音频专用线程
    │
    └── AudioTrack (播放)
```

### 协程作用域

```kotlin
class VoicePipeline {
    // 主作用域 - 生命周期绑定
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 音频处理作用域
    private val audioScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + 
        CoroutineName("AudioProcessing")
    )
    
    // IO作用域
    private val ioScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
        CoroutineName("IOOperations")
    )
    
    // 取消时全部清理
    fun stop() {
        mainScope.cancel()
        audioScope.cancel()
        ioScope.cancel()
    }
}
```

---

## 错误处理策略

| 错误场景 | 处理策略 | 状态转移 |
|----------|----------|----------|
| KWS初始化失败 | 降级为手动触发模式 | IDLE → 等待按钮 |
| ASR识别为空 | 提示"没听清"，回到待机 | RECORDING → IDLE |
| ASR识别失败 | 提示"识别出错"，重试 | RECOGNIZING → IDLE |
| LLM请求超时 | 提示"网络慢"，使用本地回复 | THINKING → SPEAKING |
| TTS合成失败 | 文字显示，不播报 | THINKING → IDLE |
| 录音权限被拒 | 提示开启权限，暂停服务 | 任意 → STOPPED |

```kotlin
sealed class PipelineError {
    object WakeWordInitFailed : PipelineError()
    object ASREmptyResult : PipelineError()
    object ASRFailed : PipelineError()
    object LLMTimeout : PipelineError()
    object TTSFailed : PipelineError()
    object PermissionDenied : PipelineError()
}

// 错误处理
private suspend fun handleError(error: PipelineError) {
    when (error) {
        is PipelineError.ASREmptyResult -> {
            speak("抱歉，我没听清楚，请再说一遍")
        }
        is PipelineError.LLMTimeout -> {
            speak("网络有点慢，稍后再试吧")
        }
        // ... 其他错误处理
    }
}
```

---

## 性能优化

### 1. 内存优化

```kotlin
// 对象池复用
class AudioBufferPool(private val bufferSize: Int) {
    private val pool = ArrayDeque<FloatArray>()
    
    fun acquire(): FloatArray {
        return pool.removeFirstOrNull() ?: FloatArray(bufferSize)
    }
    
    fun release(buffer: FloatArray) {
        if (pool.size < 10) {
            pool.addLast(buffer)
        }
    }
}

// 避免频繁创建
class VoicePipeline {
    private val bufferPool = AudioBufferPool(320)
    
    private fun processAudioChunk() {
        val buffer = bufferPool.acquire()
        // 使用...
        bufferPool.release(buffer)
    }
}
```

### 2. 延迟优化

| 环节 | 优化前 | 优化后 | 手段 |
|------|--------|--------|------|
| 唤醒响应 | 300ms | 100ms | 模型量化、线程亲和性 |
| 录音到ASR | 800ms | 200ms | 流式ASR、VAD快速触发 |
| ASR推理 | 500ms | 200ms | 模型选择、GPU加速 |
| TTS合成 | 300ms | 150ms | 首包优化、缓存 |
| **总延迟** | **1900ms** | **650ms** | - |

### 3. 功耗优化

```kotlin
// 根据状态调整采样率
class PowerManager {
    fun onStateChanged(state: PipelineState) {
        when (state) {
            PipelineState.IDLE -> {
                // KWS模式：低功耗
                setCpuFrequency(Low)
                audioCapture.setBufferSize(Small)
            }
            PipelineState.RECORDING -> {
                // 录音模式：正常
                setCpuFrequency(Normal)
            }
            PipelineState.RECOGNIZING -> {
                // ASR模式：高性能
                setCpuFrequency(High)
            }
            else -> {}
        }
    }
}
```

---

## 配置示例

```yaml
# pipeline_config.yaml

audio:
  sample_rate: 16000
  frame_size: 320  # 20ms
  buffer_seconds: 1

pipeline:
  silence_timeout_sec: 0.8
  max_recording_sec: 30
  enable_pre_buffer: true
  enable_interruption: true

preprocessor:
  enable_denoise: true
  enable_agc: true
  enable_pre_emphasis: true
  target_level: 0.3

kws:
  model: "sherpa-onnx-kws-zipformer-wenetspeech-3.3M"
  sensitivity: 0.7
  
asr:
  model: "sherpa-onnx-paraformer-zh"
  num_threads: 4
  
tts:
  model: "piper-zh_CN-huayan-medium"
  speed: 1.0
```

---

## 唤醒灵敏度优化（2026-04-04）

### 1. 灵敏度语义统一

- UI 暴露 `wakeSensitivity`（0.0~1.0），语义为“数值越大越灵敏”。
- 运行时映射为 KWS 阈值：`threshold = 0.85 - sensitivity * 0.7`，并限制到 `[0.15, 0.85]`。
- 目的：避免过去“数值越大越迟钝”的反直觉行为，同时避免极端阈值。

### 2. 保存后立即生效

- 设置页保存后调用 `VoicePipeline.applyWakeSensitivity()`，不再依赖重启语音管线。
- `ConfigHolder.reload()` 后可直接热更新 KWS 阈值。

### 3. 检测链路恢复二次过滤

- KWS 原始结果不再直接触发。
- 现在统一经过 `WakeWordDetector.process()`，启用：
  - 阈值过滤
  - cooldown 防抖（防止短时间重复触发）

### 4. 唤醒词热重载与阈值一致

- `reloadWakeWords()` 时显式传入当前灵敏度映射阈值。
- 保证“换唤醒词”不会覆盖或丢失当前灵敏度配置。

---

## 下一步

1. **实现 VoicePipeline 核心类**
2. **集成 Sherpa-ONNX 引擎**
3. **实现状态机单元测试**
4. **性能基准测试**

---

## 附录：推荐模型

> 基于 [sherpa-onnx 预训练模型](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html)

### 模型组合

| 组件 | 模型 | 语言 | 备注 |
|------|------|------|------|
| **ASR** | `sherpa-onnx-streaming-paraformer-bilingual-zh-en` | 中英双语 | 流式 Paraformer |
| **TTS** | `vits-piper-zh_CN-huayan-medium` | 中文 | 位于 `assets/vits-piper-zh_CN-huayan-medium/` |
| **VAD** | `silero-vad` | 通用 | Silero VAD |
| **KWS** | `sherpa-onnx-streaming-zipformer-en-20m` | 英文 | 20M 参数轻量模型 |

### 下载链接

| 组件 | 地址 |
|------|------|
| ASR | https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models |
| VAD | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx |
| TTS | https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models |
| KWS | https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models |
