# Sherpa-ONNX 集成文档

> 基于官方 SherpaOnnxAar 项目的 AAR 集成方式

**版本**: v1.12.32
**日期**: 2026-03-22

---

## 1. 集成方式

### 官方推荐：AAR 模块方式

使用 `sherpa-onnx-aar` 模块构建本地 AAR，包含：
- Kotlin API 源码 (`com.k2fsa.sherpa.onnx`)
- JNI 原生库 (libonnxruntime.so, libsherpa-onnx-*.so)

### 架构

```
android/
├── sherpa-onnx-aar/                    # Sherpa-ONNX 库模块
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── sherpa_onnx/                    # 库子模块
│       ├── build.gradle.kts
│       └── src/main/
│           ├── java/com/k2fsa/sherpa/onnx/   # Kotlin API
│           │   ├── OnlineRecognizer.kt
│           │   ├── OnlineStream.kt
│           │   ├── Vad.kt
│           │   ├── KeywordSpotter.kt
│           │   ├── Tts.kt
│           │   └── ...
│           └── jniLibs/                      # 原生库
│               ├── arm64-v8a/
│               ├── armeabi-v7a/
│               ├── x86/
│               └── x86_64/
├── app/                                 # 应用模块
├── core/                                # 核心模块
├── data/                                # 数据模块
└── domain/                              # 领域模块
```

---

## 2. 模块配置

### settings.gradle.kts

```kotlin
include(":app")
include(":core")
include(":data")
include(":domain")
include(":sherpa-onnx-aar")
include(":sherpa-onnx-aar:sherpa_onnx")
```

### core/build.gradle.kts

```kotlin
dependencies {
    // Sherpa-ONNX (AAR 模块)
    implementation(project(":sherpa-onnx-aar:sherpa_onnx"))

    // 其他依赖...
}
```

### sherpa-onnx-aar/build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
```

### sherpa-onnx-aar/sherpa_onnx/build.gradle.kts

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.k2fsa.sherpa.onnx"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}
```

---

## 3. Kotlin API 概览

| 类 | 用途 | 关键方法 |
|---|------|----------|
| `OnlineRecognizer` | 流式 ASR | `createStream()`, `acceptWaveform()`, `isReady()`, `decode()`, `getResult()` |
| `OnlineStream` | 流式输入 | `acceptWaveform()`, `release()` |
| `Vad` | 语音活动检测 | `acceptWaveform()`, `isSpeechDetected()`, `clear()` |
| `KeywordSpotter` | 关键词唤醒 | `createStream()`, `isReady()`, `decode()`, `getResult()` |
| `Tts` | 文本转语音 | `textToSpeech()` |

---

## 4. 组件实现

### 4.1 KWS (Keyword Spotter)

```kotlin
// SherpaKWSImpl.kt
class SherpaKWSImpl(private val context: Context) : SherpaKWS {

    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    override fun initialize(modelPath: String): Boolean {
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelPath/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    decoder = "$modelPath/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    joiner = "$modelPath/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                ),
                tokens = "$modelPath/tokens.txt",
                numThreads = 2,
                provider = "cpu"
            ),
            keywordsFile = "$modelPath/keywords.txt",
            keywordsThreshold = 0.5f
        )
        kws = KeywordSpotter(assetManager = null, config = config)
        stream = kws?.createStream("")
        return true
    }

    override fun process(audio: FloatArray): Boolean {
        val s = stream ?: return false
        val k = kws ?: return false

        s.acceptWaveform(audio, 16000)
        while (k.isReady(s)) {  // 官方模式：循环处理所有就绪数据
            k.decode(s)
        }
        val result = k.getResult(s)
        if (result.keyword.isNotEmpty()) {
            k.reset(s)
            return true
        }
        return false
    }
}
```

### 4.2 VAD (Voice Activity Detection)

```kotlin
// SherpaVADImpl.kt
class SherpaVADImpl(private val context: Context) : SherpaVAD {

    private var vad: Vad? = null

    override fun initialize(modelPath: String): Boolean {
        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.3f,
                minSilenceDuration = 0.5f,
                minSpeechDuration = 0.25f,
                windowSize = 512,
            ),
            sampleRate = 16000,
            numThreads = 1,
            provider = "cpu"
        )
        vad = Vad(assetManager = context.assets, config = config)
        return true
    }

    override fun process(audio: FloatArray): Boolean {
        vad?.acceptWaveform(audio)
        return vad?.isSpeechDetected() ?: false
    }
}
```

### 4.3 ASR (Automatic Speech Recognition)

```kotlin
// SherpaASRImpl.kt
class SherpaASRImpl(private val context: Context) : SherpaASR {

    private var recognizer: OnlineRecognizer? = null

    override fun initialize(modelPath: String): Boolean {
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelPath/encoder-epoch-99-avg-1.int8.onnx",
                    decoder = "$modelPath/decoder-epoch-99-avg-1.onnx",
                    joiner = "$modelPath/joiner-epoch-99-avg-1.onnx"
                ),
                tokens = "$modelPath/tokens.txt",
                numThreads = 2,
                provider = "cpu",
                modelType = "zipformer"
            ),
            endpointConfig = EndpointConfig(),
            enableEndpoint = true,
            decodingMethod = "greedy_search"
        )
        recognizer = OnlineRecognizer(assetManager = context.assets, config = config)
        return true
    }

    override suspend fun recognize(audio: FloatArray): String {
        val r = recognizer ?: return ""
        val stream = r.createStream()

        // 流式输入音频
        val bufferSize = (0.1 * 16000).toInt() // 100ms chunks
        var offset = 0
        while (offset < audio.size) {
            val end = minOf(offset + bufferSize, audio.size)
            stream.acceptWaveform(audio.copyOfRange(offset, end), sampleRate = 16000)
            while (r.isReady(stream)) {
                r.decode(stream)
            }
            offset = end
        }

        val text = r.getResult(stream).text ?: ""
        stream.release()
        return text
    }
}
```

---

## 5. 音频参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 采样率 | 16000 Hz | 必须 16kHz |
| 通道 | Mono | 单声道 |
| 编码 | PCM 16-bit | Float 需要 /32768.0f 转换 |
| KWS chunk | 512 samples (32ms) | AudioCapture bufferSize |
| ASR chunk | 1600 samples (100ms) | 推荐 100ms 块处理 |

---

## 6. 模型文件

### 6.1 KWS 模型
- 目录: `sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/`
- 必含文件: encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx, decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx, joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx, tokens.txt, keywords.txt
- 从 assets 复制到内部存储使用

### 6.2 ASR 模型
- 目录: `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/`
- 必含文件: encoder-epoch-99-avg-1.int8.onnx, decoder-epoch-99-avg-1.onnx, joiner-epoch-99-avg-1.onnx, tokens.txt
- 直接从 assets 加载

### 6.3 VAD 模型
- 文件: `silero_vad.onnx`
- 直接从 assets 加载

### 6.4 TTS 模型
- 目录: `vits-piper-zh_CN-huayan-medium/`
- 必含文件: zh_CN-huayan-medium.onnx, tokens.txt
- 直接从 assets 加载

---

## 7. 构建命令

```bash
cd android

# 下载语音模型（约 298 MB）
./gradlew :app:downloadModels

# 构建 AAR（如尚未构建）
./gradlew :sherpa-onnx-aar:sherpa_onnx:assembleRelease

# 构建完整项目
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

---

## 8. 注意事项

1. **版本匹配**: Kotlin API 源码版本必须与 jniLibs 原生库版本匹配
2. **内存管理**: 完整模型较大，注意内存使用
3. **流式处理**: ASR 必须按官方模式循环调用 `isReady()` + `decode()`
4. **音频格式**: 16-bit PCM，转换 Float 需要 /32768.0f
5. **线程调度**: 语音处理在 `Dispatchers.Default` 执行，避免阻塞主线程

---

## 9. 更新日志

### 2026-03-22
- 从官方下载 v1.12.32 Android release 包
- 集成 kotlin-api 源码 (22 个 .kt 文件)
- 更新 jniLibs 原生库到 v1.12.32
- 修复 SherpaKWSImpl 流式处理逻辑 (添加 `while (isReady)` 循环)
