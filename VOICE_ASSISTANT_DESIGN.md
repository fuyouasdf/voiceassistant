# 语音助手设计方案

> 利用旧手机实现离线语音控制中枢，替代小爱同学，支持多模型API接入、Navidrome/DLNA控制、OpenClaw消息推送

**版本**: 1.0  
**日期**: 2026-03-19  
**状态**: 持续优化中

---

## 目录

1. [项目概述](#项目概述)
2. [整体架构](#整体架构)
3. [功能模块](#功能模块)
4. [技术选型](#技术选型)
5. [实施方案](#实施方案)
6. [音频优化](#音频优化)
7. [配置说明](#配置说明)
8. [开发计划](#开发计划)
9. [附录](#附录)

---

## 项目概述

### 目标

构建一个完全离线的语音控制中枢，部署在旧Android手机上，实现：

1. **语音唤醒** - 离线识别唤醒词
2. **语音识别** - 离线ASR转文字
3. **TTS播报** - 离线语音合成
4. **本地意图处理** - 音乐播放、设备控制
5. **音乐控制** - 控制Navidrome和DLNA设备
6. **消息推送** - 向OpenClaw发送消息

### 核心约束

- 语音相关模块必须离线运行
- LLM（可选二期接入）
- 全部功能在局域网内完成
- 适配中低端旧手机（4GB+内存）

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        旧手机（Android）                         │
│                                                                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐  │
│  │  语音唤醒    │ →  │  语音识别    │ →  │      意图路由        │  │
│  │ (Porcupine/ │    │ (Whisper/   │    │                     │  │
│  │  OpenWakeWord│   │  SenseVoice)│    │  · 音乐 → Navidrome  │  │
│  └─────────────┘    └─────────────┘    │  · 音乐 → Navidrome  │  │
│         ↑                              │  · 设备 → DLNA       │  │
│         │                              │  · 消息 → OpenClaw   │  │
│  ┌─────────────┐                       └─────────────────────┘  │
│  │  麦克风优化  │                              │                 │
│  │ · 降噪      │                              ↓                 │
│  │ · AGC       │                       ┌──────────────┐         │
│  │ · VAD       │                       │    TTS播报    │         │
│  └─────────────┘                       │  (Piper/Coqui)│         │
│                                        └──────────────┘         │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                      技能执行层                              ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    ││
│  │  │Navidrome │  │ DLNA控制 │  │  本地规则 │  │ OpenClaw │    ││
│  │  │  Subsonic│  │  UPnP    │  │  远程调用│  │ HTTP/WS  │    ││
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘    ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## 功能模块

### 1. 语音唤醒模块

| 方案 | 大小 | 准确率 | 延迟 | 功耗 | 适用场景 |
|------|------|--------|------|------|----------|
| **Porcupine** ⭐ | ~50KB | ⭐⭐⭐⭐⭐ | <100ms | 低 | 首选，限3个唤醒词 |
| **OpenWakeWord** | 2-5MB | ⭐⭐⭐⭐ | 200ms | 中 | 需多唤醒词 |
| **Snowboy** | ~50KB | ⭐⭐⭐⭐ | <150ms | 低 | 已停止维护 |
| **PaddleSpeech KWS** | 10MB+ | ⭐⭐⭐⭐⭐ | 300ms | 中高 | 中文优化好 |
| **Sherpa-ONNX KWS** | 1-5MB | ⭐⭐⭐⭐⭐ | 150ms | 低 | 2024推荐，中文支持好 |
| **Whisper.cpp VAD** | 39MB | ⭐⭐⭐⭐ | 300ms | 中 | 统一用Whisper，简化架构 |

**2024更新推荐**: 
- **Sherpa-ONNX KWS** - 新一代开源方案，支持流式识别，中文模型丰富
- **Whisper.cpp VAD** - 如果已用Whisper做ASR，可直接复用做语音端点检测

**推荐**: Porcupine（简单）/ Sherpa-ONNX（开源首选）/ OpenWakeWord（多词）

### 2. 语音识别模块

| 方案 | 模型大小 | 实时率 | 中文准确率 | 资源占用 | 推荐场景 |
|------|---------|--------|-----------|---------|----------|
| **Whisper tiny** | 39MB | 0.3x | ⭐⭐⭐ | 低 | 极速，低端机 |
| **Whisper base** ⭐ | 74MB | 0.6x | ⭐⭐⭐⭐ | 中 | 平衡首选 |
| **Whisper small** | 244MB | 1.2x | ⭐⭐⭐⭐⭐ | 高 | 准确率优先 |
| **SenseVoice** | 200MB+ | 0.8x | ⭐⭐⭐⭐⭐ | 中高 | 中文特化 |
| **Paraformer** | 100MB+ | 0.5x | ⭐⭐⭐⭐⭐ | 中 | 流式识别 |
| **Vosk** | 50MB-1GB | 0.4x | ⭐⭐⭐⭐ | 低 | 多语言支持 |
| **Sherpa-ONNX ASR** | 30-80MB | 0.5x | ⭐⭐⭐⭐⭐ | 低 | 2024推荐，流式+中文 |
| **Faster-Whisper** | 同Whisper | 2-3x | ⭐⭐⭐⭐⭐ | 中 | CTranslate2加速 |
| **WhisperX** | 同Whisper | 4x | ⭐⭐⭐⭐⭐ | 高 | 带说话人分离 |

**2024更新推荐**:
- **Sherpa-ONNX** - 新一代开源方案，支持流式识别，延迟更低
- **Faster-Whisper** - 用CTranslate2优化，速度提升2-3倍
- **WhisperX** - 如果需要说话人分离（识别不同人说话）

**推荐组合**:
- 低端机: Whisper tiny / Sherpa-ONNX small
- 一般场景: Faster-Whisper base
- 中文优先: SenseVoice / Sherpa-ONNX paraformer
- 实时流式: Sherpa-ONNX（支持chunk-based推理）

### 3. TTS模块

| 方案 | 模型大小 | 音质 | 速度 | 中文支持 | 推荐场景 |
|------|---------|------|------|---------|----------|
| **Piper low** | ~30MB | ⭐⭐⭐ | 很快 | ✅ | 资源受限 |
| **Piper medium** ⭐ | ~120MB | ⭐⭐⭐⭐ | 快 | ✅ | 首选推荐 |
| **Piper high** | ~400MB | ⭐⭐⭐⭐⭐ | 中 | ✅ | 音质优先 |
| **Coqui TTS** | 100-400MB | ⭐⭐⭐⭐⭐ | 中 | ✅ | 声音克隆 |
| **ChatTTS** | 3GB+ | ⭐⭐⭐⭐⭐ | 慢 | ✅ | 效果最佳 |
| **PaddleSpeech** | 100MB+ | ⭐⭐⭐⭐ | 中 | ✅ | 稳定可靠 |
| **MeloTTS** | 150MB | ⭐⭐⭐⭐⭐ | 快 | ✅ | 2024推荐，中文自然 |
| **GPT-SoVITS** | 1GB+ | ⭐⭐⭐⭐⭐ | 慢 | ✅ | 5秒克隆声音 |
| **F5-TTS** | 500MB+ | ⭐⭐⭐⭐⭐ | 中 | ✅ | 2024新方案，零样本克隆 |
| **Kokoro** | 300MB | ⭐⭐⭐⭐⭐ | 很快 | ✅ | ONNX格式，移动端友好 |

**2024更新推荐**:
- **MeloTTS** - 中文效果比Piper更自然，速度也快
- **Kokoro** - ONNX格式，ONNX Runtime直接跑，无需额外依赖
- **GPT-SoVITS/F5-TTS** - 如果要克隆自己的声音

**推荐**: 
- 简单场景: Piper medium
- 中文优先: MeloTTS
- 移动端优化: Kokoro (ONNX原生)
- 声音克隆: GPT-SoVITS

### 4. 组合方案

#### 方案A：轻量极速（低端机）
```
唤醒: Porcupine (50KB)
ASR:  Whisper tiny (39MB)
TTS:  Piper low (30MB)
总计: ~120MB
适合: 骁龙660/4GB内存
```

#### 方案B：平衡实用（推荐）⭐
```
唤醒: Porcupine (50KB)
ASR:  Whisper base (74MB)
TTS:  Piper medium (120MB)
总计: ~250MB
适合: 骁龙865/6GB内存
```

#### 方案C：中文特化
```
唤醒: PaddleSpeech (10MB)
ASR:  SenseVoice (200MB)
TTS:  PaddleSpeech (100MB)
总计: ~310MB
适合: 中文场景多
```

#### 方案D：效果优先（高端机）
```
唤醒: OpenWakeWord (5MB)
ASR:  Whisper small (244MB)
TTS:  Coqui TTS (200MB)
总计: ~450MB
适合: 骁龙888+/8GB内存
```

---

## 技术选型

### 核心依赖

| 模块 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 网络 | OkHttp | 4.x | HTTP客户端 |
| 协程 | Kotlin Coroutines | 1.7+ | 异步处理 |
| 序列化 | Gson | 2.10+ | JSON处理 |
| 音频 | AudioRecord | API 26+ | 录音API |
| ML推理 | ONNX Runtime | 1.16+ | 模型推理 |
| JNI | NDK | 25+ | 原生库调用 |

### 第三方库

```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.16.0'
    implementation 'ai.picovoice:porcupine-android:3.0.0'
}
```

---

## 实施方案

### Phase 1: 基础框架（Week 1）

#### Step 1.1 项目初始化
```
Android Studio 新建项目:
- minSdk: 26 (Android 8.0)
- targetSdk: 34
- 语言: Kotlin
- 架构: MVVM + Repository
```

**目录结构**:
```
app/
├── src/main/
│   ├── java/com/voiceassistant/
│   │   ├── audio/          # 音频处理
│   │   ├── wake/           # 唤醒引擎
│   │   ├── asr/            # 语音识别
│   │   ├── tts/            # 语音合成
│   │   ├── nlp/            # 意图识别
│   │   ├── skills/         # 技能实现
│   │   ├── api/            # API接口
│   │   ├── service/        # 后台服务
│   │   └── ui/             # 界面
│   └── assets/models/      # 模型文件
└── jni/                    # JNI代码
```

#### Step 1.2 语音唤醒实现

```kotlin
interface WakeWordEngine {
    fun initialize(): Boolean
    fun process(audioData: ShortArray): Int
    fun release()
    val name: String
}

class PorcupineEngine(context: Context) : WakeWordEngine {
    private var porcupine: Porcupine? = null
    
    override fun initialize(): Boolean {
        return try {
            porcupine = Porcupine.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_KEY)
                .setKeywordPaths(arrayOf("assets/models/wakeup.ppn"))
                .setSensitivities(floatArrayOf(0.7f))
                .build(context)
            true
        } catch (e: Exception) {
            Log.e("WakeWord", "初始化失败", e)
            false
        }
    }
    
    override fun process(audioData: ShortArray): Int {
        return porcupine?.process(audioData) ?: -1
    }
    
    override fun release() {
        porcupine?.delete()
    }
    
    override val name = "Porcupine"
}
```

#### Step 1.3 语音识别实现

```kotlin
interface ASREngine {
    fun initialize(modelPath: String): Boolean
    fun transcribe(audioData: FloatArray, language: String = "zh"): String
    fun release()
    val name: String
}

class WhisperEngine : ASREngine {
    private var context: Long = 0
    
    init {
        System.loadLibrary("whisper")
    }
    
    override fun initialize(modelPath: String): Boolean {
        context = nativeInit(modelPath)
        return context != 0
    }
    
    override fun transcribe(audioData: FloatArray, language: String): String {
        return nativeTranscribe(context, audioData, language)
    }
    
    override fun release() {
        nativeFree(context)
    }
    
    override val name = "Whisper"
    
    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(context: Long, audio: FloatArray, lang: String): String
    private external fun nativeFree(context: Long)
}
```

#### Step 1.4 TTS实现

```kotlin
interface TTSEngine {
    fun initialize(modelPath: String, configPath: String? = null): Boolean
    fun synthesize(text: String, speakerId: Int = 0): FloatArray
    fun speak(text: String, audioTrack: AudioTrack)
    fun release()
    val name: String
}

class PiperEngine : TTSEngine {
    private var synthesizer: PiperSynthesizer? = null
    
    override fun initialize(modelPath: String, configPath: String?): Boolean {
        return try {
            synthesizer = PiperSynthesizer(modelPath, configPath)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun synthesize(text: String, speakerId: Int): FloatArray {
        return synthesizer?.synthesize(text, speakerId) ?: floatArrayOf()
    }
    
    override fun speak(text: String, audioTrack: AudioTrack) {
        val pcm = synthesize(text)
        audioTrack.write(pcm, 0, pcm.size)
    }
    
    override fun release() {
        synthesizer?.close()
    }
    
    override val name = "Piper"
}
```

### Phase 2: 音频优化（Week 2）

#### Step 2.1 音频采集优化

```kotlin
class AudioCapture {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        const val BUFFER_SIZE = 3200
    }
    
    private var audioRecord: AudioRecord? = null
    
    fun initialize() {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT)
        
        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(FORMAT)
                .setChannelMask(CHANNEL)
                .build())
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
    }
}
```

#### Step 2.2 音频预处理管道

```kotlin
class AudioPreprocessor {
    
    // 预加重
    fun preEmphasis(audio: FloatArray, coeff: Float = 0.97f): FloatArray {
        val result = FloatArray(audio.size)
        result[0] = audio[0]
        for (i in 1 until audio.size) {
            result[i] = audio[i] - coeff * audio[i - 1]
        }
        return result
    }
    
    // VAD检测
    fun vadDetect(audio: FloatArray, threshold: Float = 0.01f): Boolean {
        val energy = audio.map { it * it }.average()
        val zcr = calculateZCR(audio)
        return energy > threshold && zcr in 10..100
    }
    
    private fun calculateZCR(audio: FloatArray): Int {
        var zcr = 0
        for (i in 1 until audio.size) {
            if ((audio[i] >= 0) != (audio[i-1] >= 0)) zcr++
        }
        return zcr * 100 / audio.size
    }
    
    // 归一化
    fun normalize(audio: FloatArray): FloatArray {
        val maxAmp = audio.maxOf { kotlin.math.abs(it) }
        return if (maxAmp > 1.0f) {
            audio.map { it / maxAmp * 0.95f }.toFloatArray()
        } else audio
    }
}
```

#### Step 2.3 AGC自动增益控制

```kotlin
class AutomaticGainControl {
    private var targetLevel = 0.3f
    private var maxGain = 10f
    private var currentGain = 1f
    private var envelope = 0f
    
    fun process(audio: FloatArray): FloatArray {
        return audio.map { sample ->
            val absSample = kotlin.math.abs(sample)
            envelope = if (absSample > envelope) {
                envelope * 0.99f + absSample * 0.01f
            } else {
                envelope * 0.9f + absSample * 0.1f
            }
            
            val desiredGain = if (envelope > 0.001f) {
                (targetLevel / envelope).coerceIn(1f / maxGain, maxGain)
            } else 1f
            
            currentGain += (desiredGain - currentGain) * 0.1f
            (sample * currentGain).coerceIn(-1f, 1f)
        }.toFloatArray()
    }
}
```

### Phase 3: 技能实现（Week 3）

#### Step 3.1 Navidrome控制

```kotlin
interface NavidromeApi {
    @GET("rest/ping")
    suspend fun ping(): Response<SubsonicResponse>
    
    @GET("rest/search3")
    suspend fun search(@Query("query") query: String): Response<SearchResult>
    
    @GET("rest/stream")
    suspend fun stream(@Query("id") songId: String): Response<ResponseBody>
}

class MusicSkill(private val api: NavidromeApi) {
    
    suspend fun play(query: String, dlnaRenderer: DLNARenderer) {
        // 1. 搜索歌曲
        val result = api.search(query)
        val song = result.body()?.searchResult?.songs?.firstOrNull()
            ?: throw Exception("未找到歌曲")
        
        // 2. 获取播放URL
        val streamUrl = "${BuildConfig.NAVIDROME_URL}/rest/stream?id=${song.id}"
        
        // 3. 推送到DLNA设备
        dlnaRenderer.play(streamUrl, song.title, song.artist)
    }
}
```

#### Step 3.2 DLNA控制

```kotlin
class DLNAController {
    private val clingService: AndroidUpnpService
    
    fun discoverRenderers(): List<DLNARenderer> {
        // 使用Cling库发现UPnP设备
        return clingService.registry.getDevices(DeviceType.DMR).map { device ->
            DLNARenderer(device)
        }
    }
}

class DLNARenderer(private val device: Device) {
    
    fun play(url: String, title: String, artist: String) {
        val avTransport = device.findService(ServiceType.AVTransport)
        
        // 1. 设置URI
        avTransport?.getAction("SetAVTransportURI")?.let { action ->
            action.setArgumentValue("InstanceID", 0)
            action.setArgumentValue("CurrentURI", url)
            action.setArgumentValue("CurrentURIMetaData", buildMetadata(title, artist))
            action.postControlAction()
        }
        
        // 2. 播放
        avTransport?.getAction("Play")?.let { action ->
            action.setArgumentValue("InstanceID", 0)
            action.setArgumentValue("Speed", "1")
            action.postControlAction()
        }
    }
}
```

#### Step 3.3 OpenClaw消息推送

```kotlin
class OpenClawClient(baseUrl: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    fun sendMessage(text: String, source: String = "voice_assistant") {
        val message = OpenClawMessage(
            type = "voice_command",
            text = text,
            source = source,
            timestamp = System.currentTimeMillis()
        )
        
        val request = Request.Builder()
            .url("$baseUrl/api/v1/messages")
            .post(gson.toJson(message).toRequestBody("application/json".toMediaType()))
            .build()
            
        client.newCall(request).execute()
    }
    
    data class OpenClawMessage(
        val type: String,
        val text: String,
        val source: String,
        val timestamp: Long
    )
}
```

#### Step 3.4 意图路由

```kotlin
class IntentRouter(
    private val llmClient: LLMClient,
    private val musicSkill: MusicSkill,
    private val dlnaController: DLNAController,
    private val openClawClient: OpenClawClient
) {
    
    suspend fun handle(text: String): String {
        val intent = classifyIntent(text)
        
        return when (intent.type) {
            IntentType.MUSIC -> handleMusicIntent(intent, text)
            IntentType.DEVICE -> handleDeviceIntent(intent)
            IntentType.OPENCLAW -> handleOpenClawIntent(text)
            IntentType.CHAT -> handleChatIntent(text)
        }
    }
    
    private fun classifyIntent(text: String): Intent {
        // 规则+关键词匹配
        return when {
            text.contains("播放") || text.contains("听歌") -> 
                Intent(IntentType.MUSIC, extractSongQuery(text))
            text.contains("音量") || text.contains("暂停") || text.contains("下一首") -> 
                Intent(IntentType.DEVICE, null)
            text.contains("问下") || text.contains("告诉") || text.contains("通知") -> 
                Intent(IntentType.OPENCLAW, null)
            else -> Intent(IntentType.CHAT, null)
        }
    }
    
    private suspend fun handleMusicIntent(intent: Intent, text: String): String {
        val query = intent.data ?: text.replace("播放", "").trim()
        return try {
            musicSkill.play(query, dlnaController.getDefaultRenderer())
            "正在播放 $query"
        } catch (e: Exception) {
            "播放失败: ${e.message}"
        }
    }
    
    private suspend fun handleChatIntent(text: String): String {
        return llmClient.chat(text)
    }
}

enum class IntentType { MUSIC, DEVICE, OPENCLAW, CHAT }
data class Intent(val type: IntentType, val data: String?)
```

### Phase 4: 后台服务与保活（Week 4）

#### Step 4.1 前台服务

```kotlin
class VoiceAssistantService : Service() {
    
    private lateinit var wakeWordEngine: WakeWordEngine
    private lateinit var audioCapture: AudioCapture
    private lateinit var audioPipeline: AudioPipeline
    
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        initializeEngines()
        startWakeWordDetection()
    }
    
    private fun startWakeWordDetection() {
        // 持续录音检测唤醒词
        audioCapture.start { audioData ->
            if (wakeWordEngine.process(audioData) >= 0) {
                onWakeWordDetected()
            }
        }
    }
    
    private fun onWakeWordDetected() {
        // 1. 播放提示音
        playBeep()
        
        // 2. 开始语音识别
        val speechAudio = recordUntilSilence()
        
        // 3. ASR转文字
        val text = asrEngine.transcribe(speechAudio)
        
        // 4. 处理意图
        val response = intentRouter.handle(text)
        
        // 5. TTS播报
        ttsEngine.speak(response, audioTrack)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
```

#### Step 4.2 保活策略

```kotlin
class KeepAliveManager(context: Context) {
    
    fun enable() {
        // 1. 前台服务
        startForegroundService()
        
        // 2. 唤醒锁
        acquireWakeLock()
        
        // 3. 电池优化白名单
        requestIgnoreBatteryOptimizations()
        
        // 4. 自启动
        enableAutoStart()
        
        // 5. 定时心跳
        startHeartbeat()
    }
    
    private fun startHeartbeat() {
        val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
```

---

## 音频优化

### 降噪方案

| 方案 | 延迟 | 效果 | 资源占用 | 推荐度 |
|------|------|------|---------|--------|
| **RNNoise** | 10ms | ⭐⭐⭐⭐⭐ | 低 | ⭐ 首选 |
| **WebRTC AEC3** | 20ms | ⭐⭐⭐⭐ | 中 | 有回声场景 |
| **SpeexDSP** | 15ms | ⭐⭐⭐⭐ | 低 | 轻量需求 |
| **谱减法** | 5ms | ⭐⭐⭐ | 极低 | 资源受限 |
| **DFNet (DeepFilterNet)** | 10ms | ⭐⭐⭐⭐⭐ | 低 | 2024推荐，比RNNoise更好 |
| **Apple ALS (限iOS)** | 5ms | ⭐⭐⭐⭐⭐ | 低 | iOS设备首选 |

**2024更新推荐**:
- **DeepFilterNet** - 比RNNoise效果更好，同样轻量
- **PercepNet** - Xiph.org新方案，Opus编码器同款降噪

### 音频Profile配置

```kotlin
data class AudioProfile(
    val name: String,
    val sampleRate: Int = 16000,
    val enableDenoise: Boolean = true,
    val enableAGC: Boolean = true,
    val vadThreshold: Float = 0.01f,
    val silenceTimeoutMs: Int = 800
) {
    companion object {
        val QUIET = AudioProfile("安静", enableDenoise = false, vadThreshold = 0.005f)
        val NORMAL = AudioProfile("普通")
        val NOISY = AudioProfile("嘈杂", vadThreshold = 0.02f, silenceTimeoutMs = 600)
    }
}
```

### 硬件优化

```kotlin
class HardwareOptimizer(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    fun optimize() {
        // 关闭内置降噪避免双重处理
        audioManager.setParameters("noise_suppression=off")
        
        // 设置麦克风增益
        audioManager.setParameters("mic_gain=80")
        
        // 请求音频焦点
        requestAudioFocus()
    }
    
    fun getMicPositionHint(): String {
        return when {
            Build.MANUFACTURER.contains("Samsung") -> "底部麦克风，说话时对准底部"
            Build.MANUFACTURER.contains("Xiaomi") -> "底部+顶部双麦，建议底部对准声源"
            Build.MANUFACTURER.contains("Huawei") -> "底部主麦，顶部降噪麦"
            else -> "通常麦克风在手机底部"
        }
    }
}
```

---

## 配置说明

### config.yaml

```yaml
# 基础配置
app:
  name: "语音助手"
  version: "1.0.0"
  debug: false

# 唤醒配置
wake:
  engine: "Porcupine"  # Porcupine / OpenWakeWord
  keyword: "你好爪爪"
  sensitivity: 0.7

# ASR配置
asr:
  engine: "Whisper"    # Whisper / SenseVoice / Paraformer
  model: "base"        # tiny / base / small
  language: "zh"

# TTS配置
tts:
  engine: "Piper"      # Piper / Coqui
  quality: "medium"    # low / medium / high
  speed: 1.0

# LLM配置
llm:
  provider: "openai"   # openai / deepseek / local
  api_key: "${API_KEY}"
  base_url: "https://api.deepseek.com"
  model: "deepseek-chat"
  temperature: 0.7

# Navidrome配置
navidrome:
  url: "http://192.168.1.x:4533"
  username: "admin"
  password: "password"

# DLNA配置
dlna:
  default_renderer: "客厅音箱"
  discovery_timeout: 5000

# OpenClaw配置
openclaw:
  enabled: true
  url: "http://192.168.1.x:8080"
  token: "your-token"

# 音频配置
audio:
  profile: "normal"    # quiet / normal / noisy
  sample_rate: 16000
  buffer_ms: 200
```

---

## 开发计划

| 阶段 | 时间 | 任务 | 产出 |
|------|------|------|------|
| Phase 1 | Week 1 | 基础框架 | 唤醒+ASR+TTS跑通 |
| Phase 2 | Week 2 | 音频优化 | 降噪+AGC+VAD |
| Phase 3 | Week 3 | 技能实现 | Navidrome+DLNA+OpenClaw |
| Phase 4 | Week 4 | 服务保活 | 后台服务+自动启动 |
| 测试 | Week 5 | 集成测试 | Bug修复+优化 |
| 部署 | Week 6 | 打包发布 | APK+文档 |

---

## 附录

### 模型下载地址

```bash
# Whisper
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

# Piper中文
wget https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/zh/zh_CN/huayan/medium/zh_CN-huayan-medium.onnx
wget https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/zh/zh_CN/huayan/medium/zh_CN-huayan-medium.onnx.json

# SenseVoice
wget https://huggingface.co/funasr/FunASR-SenseVoice-ONNX

# Porcupine唤醒词
# 通过Picovoice Console生成: https://console.picovoice.ai/
```

### 硬件要求

| 配置 | 能否运行 | 推荐方案 |
|------|---------|----------|
| 4GB RAM + 骁龙660 | ✅ 3B模型 | 方案A |
| 6GB RAM + 骁龙865 | ✅ 流畅 | 方案B |
| 8GB RAM + 骁龙888+ | ✅ 优秀 | 方案C/D |

### 存储需求

| 方案 | 模型大小 | 总存储 |
|------|---------|--------|
| 方案A | ~120MB | ~200MB |
| 方案B | ~250MB | ~400MB |
| 方案C | ~310MB | ~500MB |
| 方案D | ~450MB | ~700MB |

---

## 2024新方案调研建议

### 已验证的项目

#### 1. **Sherpa-ONNX** ⭐⭐⭐ 强烈推荐 - 最终选择
- **GitHub**: https://github.com/k2-fsa/sherpa-onnx
- **官方文档**: https://k2-fsa.github.io/sherpa/onnx/index.html
- **Stars**: 10.9k
- **特点**:
  - 一站式语音方案（KWS+ASR+TTS+VAD+降噪+说话人分离）
  - 支持Android/iOS/鸿蒙/树莓派/RISC-V/多种NPU
  - 支持12种编程语言（Java/Kotlin/Swift/C++/Python等）
  - 完全离线，无需网络
  - Apache-2.0 license，商用友好
- **Android支持**:
  - 有专门的Android文档
  - Java API / Kotlin API
  - 预编译库可直接使用
  - 有示例代码
- **中文资源**:
  - 中文文档齐全
  - 2024-10-09【基于sherpa的本地智能语音助手入门-Java Api版】
  - 2024-06-10 SherpaOnnxTtsEngine - Android本地TTS引擎
  - 2023-08-08 snowboy+sherpa-onnx实现离线语音识别

#### 2. 其他备选（暂不使用）
- **Faster-Whisper**: CTranslate2加速，但Sherpa-ONNX已内置Whisper支持
- **MeloTTS**: Sherpa-ONNX已内置TTS，无需额外集成
- **DeepFilterNet**: Sherpa-ONNX已内置降噪

### 最终架构（基于Sherpa-ONNX）

```
┌─────────────────────────────────────────┐
│           Android 旧手机                 │
│  ┌─────────────────────────────────────┐│
│  │      Sherpa-ONNX 语音引擎            ││
│  │  ┌─────────┐ ┌─────────┐ ┌────────┐││
│  │  │KWS唤醒  │ │ASR识别  │ │TTS合成 │││
│  │  │(关键词) │ │(Whisper│ │(Piper) │││
│  │  │         │ │/Para)  │ │        │││
│  │  └─────────┘ └─────────┘ └────────┘││
│  │  ┌─────────┐ ┌─────────────────┐   ││
│  │  │VAD检测  │ │Speaker Diarization│  ││
│  │  │(端点)   │ │(说话人分离)      │   ││
│  │  └─────────┘ └─────────────────┘   ││
│  └─────────────────────────────────────┘│
│              ↓                          │
│  ┌─────────────────────────────────────┐│
│  │         意图路由 + LLM API          ││
│  └─────────────────────────────────────┘│
│              ↓                          │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐  ││
│  │Navidrome│ │DLNA控制 │ │OpenClaw  │  ││
│  └─────────┘ └─────────┘ └──────────┘  ││
└─────────────────────────────────────────┘
```

### Sherpa-ONNX Android 集成要点

```kotlin
// 1. 添加依赖
implementation 'com.github.k2-fsa:sherpa-onnx-android:1.10.0'

// 2. 初始化ASR
val config = OnlineRecognizerConfig(
    featConfig = FeatureExtractorConfig(...),
    modelConfig = OnlineModelConfig(
        transducer = OnlineTransducerModelConfig(...),
        tokens = "tokens.txt",
        numThreads = 4
    )
)
val recognizer = OnlineRecognizer(config)

// 3. 实时识别
val stream = recognizer.createStream()
stream.acceptWaveform(samples)
val result = recognizer.getResult(stream)

// 4. TTS合成
val ttsConfig = OfflineTtsConfig(
    model = OfflineTtsModelConfig(...),
    dataDir = "tts-model"
)
val tts = OfflineTts(ttsConfig)
val audio = tts.generate("你好", sid=0)
```

### 模型下载

```bash
# 中文ASR (Paraformer)
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-2024-03-09.tar.bz2

# 中文TTS (Piper)
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/piper-zh_CN-huayan-medium.tar.bz2

# 唤醒词模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
```

### 参考资源

- **GitHub**: https://github.com/k2-fsa/sherpa-onnx
- **文档**: https://k2-fsa.github.io/sherpa/onnx/index.html
- **Android示例**: https://github.com/k2-fsa/sherpa-onnx/tree/master/android
- **中文教程**: 搜索 "sherpa-onnx 中文资料"

### 开发计划调整

| 阶段 | 任务 | 说明 |
|------|------|------|
| Week 1 | 集成Sherpa-ONNX | 替换Porcupine+Whisper+Piper组合 |
| Week 2 | 语音优化 | VAD+降噪（内置） |
| Week 3 | 技能实现 | Navidrome+DLNA+OpenClaw |
| Week 4 | 服务保活 | 后台服务+自动启动 |

## 待优化项

- [ ] 多唤醒词支持
- [ ] 流式ASR（边说边识别）
- [ ] 说话人识别
- [ ] 情感分析
- [ ] 本地LLM支持
- [ ] 更多智能家居协议
- [ ] 语音克隆
- [ ] 自适应音频Profile
- [ ] 调研Sherpa-ONNX统一方案
- [ ] 测试Faster-Whisper速度提升
- [ ] 评估MeloTTS/Kokoro替代Piper

---

*文档版本: 1.0 | 最后更新: 2026-03-19*