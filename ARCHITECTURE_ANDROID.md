# Android 客户端整体架构设计

> 目标兼容 Android 6.0 (API 23)，基于 MVVM + Clean Architecture

**版本**: 1.0  
**日期**: 2026-03-20  
**minSdk**: 23 (Android 6.0)  
**targetSdk**: 34

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
├── app/
│   ├── src/main/
│   │   ├── java/com/voiceassistant/app/
│   │   │   ├── VoiceAssistantApp.kt
│   │   │   ├── di/
│   │   │   │   ├── AppModule.kt
│   │   │   │   └── ViewModelModule.kt
│   │   │   ├── ui/
│   │   │   │   ├── main/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   └── MainViewModel.kt
│   │   │   │   ├── voice/
│   │   │   │   │   ├── VoiceFragment.kt
│   │   │   │   │   ├── VoiceViewModel.kt
│   │   │   │   │   └── VoiceUiState.kt
│   │   │   │   ├── settings/
│   │   │   │   │   ├── SettingsFragment.kt
│   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   └── service/
│   │   │   │       ├── VoiceAssistantService.kt
│   │   │   │       ├── KeepAliveManager.kt
│   │   │   │       └── NotificationHelper.kt
│   │   │   └── receiver/
│   │   │       ├── BootReceiver.kt
│   │   │       └── PowerReceiver.kt
│   │   ├── res/
│   │   └── assets/models/
│   └── build.gradle.kts
│
├── core/
│   ├── src/main/java/com/voiceassistant/core/
│   │   ├── audio/
│   │   │   ├── AudioCapture.kt
│   │   │   ├── AudioPlayer.kt
│   │   │   └── AudioPreprocessor.kt
│   │   ├── sherpa/
│   │   │   ├── SherpaKWS.kt
│   │   │   ├── SherpaASR.kt
│   │   │   ├── SherpaTTS.kt
│   │   │   └── SherpaVAD.kt
│   │   ├── pipeline/
│   │   │   ├── VoicePipeline.kt
│   │   │   └── PipelineState.kt
│   │   ├── intent/
│   │   │   ├── IntentClassifier.kt
│   │   │   ├── IntentRouter.kt
│   │   │   └── SkillManager.kt
│   │   └── dlna/
│   │       ├── DLNAManager.kt
│   │       └── DLNARenderer.kt
│   └── src/main/cpp/
│       ├── sherpa_jni.cpp
│       └── CMakeLists.txt
│
├── data/
│   ├── src/main/java/com/voiceassistant/data/
│   │   ├── repository/
│   │   │   ├── VoiceEngineRepositoryImpl.kt
│   │   │   ├── LLMRepositoryImpl.kt
│   │   │   └── MusicRepositoryImpl.kt
│   │   ├── local/
│   │   │   ├── SettingsDao.kt
│   │   │   └── AppDatabase.kt
│   │   └── remote/
│   │       ├── LLMApiService.kt
│   │       └── NavidromeApiService.kt
│   └── build.gradle.kts
│
├── domain/
│   ├── src/main/java/com/voiceassistant/domain/
│   │   ├── repository/
│   │   │   ├── VoiceEngineRepository.kt
│   │   │   ├── LLMRepository.kt
│   │   │   └── MusicRepository.kt
│   │   ├── model/
│   │   │   ├── VoiceState.kt
│   │   │   ├── Intent.kt
│   │   │   └── Command.kt
│   │   └── usecase/
│   │       ├── StartVoicePipelineUseCase.kt
│   │       └── SendCommandUseCase.kt
│   └── build.gradle.kts
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