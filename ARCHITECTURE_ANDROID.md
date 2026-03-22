# Android 客户端整体架构设计

> 目标兼容 Android 6.0 (API 23)，基于 MVVM + Clean Architecture

**版本**: 1.1
**日期**: 2026-03-22
**minSdk**: 26
**targetSdk**: 34

---

## 技术栈与依赖

### 核心框架
| 库 | 版本 | 用途 |
|-----|------|------|
| Kotlin | 1.9.x | 主语言 |
| Hilt | 2.50 | 依赖注入 |
| Coroutines | 1.7.3 | 异步处理 |

### AndroidX
| 库 | 版本 |
|-----|------|
| core-ktx | 1.12.0 |
| appcompat | 1.6.1 |
| material | 1.11.0 |
| lifecycle | 2.7.0 |
| room | 2.6.1 |
| security-crypto | 1.1.0-alpha06 |

### 网络与数据
| 库 | 版本 |
|-----|------|
| Retrofit | 2.9.0 |
| OkHttp | 4.12.0 |
| Timber | 5.0.1 |

### 语音引擎
| 组件 | 来源 |
|------|------|
| Sherpa-ONNX | sherpa-onnx-aar 模块 (v1.12.32) |

### 构建配置
```gradle
compileSdk = 34
minSdk = 26
targetSdk = 34
sourceCompatibility = JavaVersion.VERSION_17
```

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              UI Layer (表现层)                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  MainActivity│  │VoiceFragment│  │SettingsFrag │  │  Dialogs    │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────────────┘        │
│         │                │                │                                  │
│         └────────────────┴────────────────┘                                  │
│                          │                                                   │
│                          ▼                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                      ViewModel (状态管理)                        │        │
│  │  · VoiceViewModel - 语音交互状态                                │        │
│  │  · SettingsViewModel - 配置管理                                 │        │
│  │  · DeviceViewModel - 设备发现                                   │        │
│  └─────────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Domain Layer (领域层)                              │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                      Use Cases (用例)                            │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │        │
│  │  │StartVoice│ │StopVoice │ │SendCmd   │ │UpdateCfg │           │        │
│  │  │Pipeline  │ │Pipeline  │ │ToLLM     │ │          │           │        │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                      Repository Interfaces                       │        │
│  │  · VoiceEngineRepository                                         │        │
│  │  · LLMRepository                                                 │        │
│  │  · MusicRepository                                               │        │
│  │  · SettingsRepository                                            │        │
│  └─────────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Data Layer (数据层)                               │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                      Repositories (实现)                         │        │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │        │
│  │  │VoiceEngineRepo│ │LLMRepoImpl   │ │MusicRepoImpl │            │        │
│  │  │Impl          │ │              │ │              │            │        │
│  │  └──────────────┘ └──────────────┘ └──────────────┘            │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                      Data Sources                                │        │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────┐ │        │
│  │  │SherpaONNX   │ │LLM API      │ │Navidrome    │ │Local DB   │ │        │
│  │  │(Local)      │ │(Remote)     │ │API          │ │(Room)     │ │        │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └───────────┘ │        │
│  └─────────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 完整目录结构

```
voice-assistant-android/
├── app/                                    # 应用模块 (UI + Service)
│   └── src/main/
│       ├── java/com/voiceassistant/app/
│       │   ├── VoiceAssistantApp.kt
│       │   ├── di/
│       │   │   ├── AppModule.kt
│       │   │   └── ConfigHolder.kt
│       │   ├── model/
│       │   │   ├── ModelInfo.kt
│       │   │   └── ModelInitializer.kt
│       │   ├── service/
│       │   │   └── VoiceAssistantService.kt
│       │   └── ui/
│       │       ├── main/
│       │       │   ├── MainActivity.kt
│       │       │   ├── MainViewModel.kt
│       │       │   └── FluidGradientView.kt
│       │       ├── settings/
│       │       │   └── SettingsActivity.kt
│       │       ├── splash/
│       │       │   └── ModelDownloadActivity.kt
│       │       └── util/
│       │           └── ErrorHandler.kt
│       ├── res/
│       └── assets/models/                  # 模型文件目录
│
├── core/                                   # 核心模块 (语音管道)
│   └── src/main/java/com/voiceassistant/core/
│       ├── audio/
│       │   ├── AudioCapture.kt            # 音频录制
│       │   └── AudioPlayer.kt             # 音频播放
│       ├── sherpa/                        # Sherpa-ONNX 实现
│       │   ├── SherpaKWS.kt / Impl       # 关键词唤醒
│       │   ├── SherpaASR.kt / Impl       # 语音识别
│       │   ├── SherpaTTS.kt / Impl       # 语音合成
│       │   ├── SherpaVAD.kt / Impl       # 语音活动检测
│       │   └── ModelConfig.kt             # 模型配置
│       ├── pipeline/
│       │   ├── VoicePipeline.kt           # 语音管道控制器
│       │   └── PipelineState.kt           # 管道状态
│       ├── intent/
│       │   └── IntentRouter.kt            # 意图路由
│       └── dlna/
│           ├── DLNAManager.kt            # DLNA 管理器
│           └── DLNAController.kt          # DLNA 控制器
│
├── data/                                  # 数据模块
│   └── src/main/java/com/voiceassistant/data/
│       ├── local/
│       │   ├── AppDatabase.kt
│       │   ├── ConfigDao.kt
│       │   └── ConfigEntity.kt
│       ├── remote/
│       │   ├── LLMApi.kt
│       │   └── NavidromeApi.kt
│       └── repository/
│           ├── Repositories.kt
│           ├── SettingsRepositoryImpl.kt
│           └── MusicRepositoryImpl.kt
│
├── domain/                                # 领域模块
│   └── src/main/java/com/voiceassistant/domain/
│       ├── model/
│       │   ├── ConfigModels.kt
│       │   └── Song.kt
│       ├── repository/
│       │   ├── LLMRepository.kt
│       │   └── MusicRepository.kt
│       └── usecase/
│           └── StartVoicePipelineUseCase.kt
│
├── sherpa-onnx-aar/                       # Sherpa-ONNX 库模块 (v1.12.32)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   └── sherpa_onnx/                       # 库子模块
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/k2fsa/sherpa/onnx/  # Kotlin API
│           │   ├── OnlineRecognizer.kt
│           │   ├── OnlineStream.kt
│           │   ├── Vad.kt
│           │   ├── KeywordSpotter.kt
│           │   ├── Tts.kt
│           │   └── ...
│           └── jniLibs/                    # 原生库
│               ├── arm64-v8a/
│               ├── armeabi-v7a/
│               ├── x86/
│               └── x86_64/
│
└── build.gradle.kts
```

---

## 核心组件代码

### 1. Application 类

```kotlin
// app/VoiceAssistantApp.kt
@HiltAndroidApp
class VoiceAssistantApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // 初始化模型（首次启动解压）
        ModelInitializer.initialize(this)
        
        // 启动后台服务
        startVoiceService()
    }
    
    private fun startVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
```

### 2. 前台服务

```kotlin
// app/service/VoiceAssistantService.kt
class VoiceAssistantService : Service() {
    
    @Inject lateinit var voicePipeline: VoicePipeline
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var keep