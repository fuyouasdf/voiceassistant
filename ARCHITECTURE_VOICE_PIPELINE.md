# 语音管道架构设计

> 从麦克风输入到语音输出的完整数据流

**版本**: 1.0  
**日期**: 2026-03-20

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
         ┌─────────→│   IDLE      │←────────┐
         │          │  (待机)     │         │
         │          └──────┬──────┘         │
         │                 │ 检测到唤醒词    │
         │                 ↓                │
         │          ┌─────────────┐         │
         │    ┌────→│  LISTENING  │         │
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
| `IDLE` | 待机，KWS持续检测 | `LISTENING` |
| `LISTENING` | 唤醒成功，等待语音 | `RECORDING`, `IDLE`(超时) |
| `RECORDING` | 正在录音 | `RECOGNIZING`, `IDLE`(取消) |
| `RECOGNIZING` | ASR处理中 | `THINKING` |
| `THINKING` | 意图路由（本地规则） | `SPEAKING`, `IDLE`(打断) |
| `SPEAKING` | TTS播报 | `IDLE`, `LISTENING`(打断) |

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
    private val audioCapture: AudioCapture
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

### 3. 环形音频缓冲区 (CircularAudioBuffer)

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

### 5. VAD引擎接口

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
         │ FloatArray
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

## 下一步

1. **实现 VoicePipeline 核心类**
2. **集成 Sherpa-ONNX 引擎**
3. **实现状态机单元测试**
4. **性能基准测试**
