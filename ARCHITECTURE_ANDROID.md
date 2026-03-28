# Android 客户端整体架构设计

> 目标兼容 Android 6.0 (API 23)，基于 MVVM + Clean Architecture

**版本**: 1.2
**日期**: 2026-03-27
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
│  │  │SherpaONNX   │ │LLM API      │ │Jellyfin     │ │Local DB   │ │        │
│  │  │(Local)      │ │(Remote)     │ │REST API     │ │(Room)     │ │        │
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
│           ├── DLNAManager.kt             # DLNA 投放管理器 (SSDP 发现)
│           └── DLNAPlayer.kt             # DLNA 播放器 (PlayerRepository 实现)
│
├── data/                                  # 数据模块
│   └── src/main/java/com/voiceassistant/data/
│       ├── local/
│       │   ├── AppDatabase.kt
│       │   ├── ConfigDao.kt
│       │   └── ConfigEntity.kt
│       ├── remote/
│       │   ├── LLMApi.kt
│       │   ├── JellyfinClient.kt            # Jellyfin REST API 客户端
│       │   └── DLNAAuthHelper.kt            # Subsonic 参数认证生成器
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

## Jellyfin 音乐播放

### 架构概述
使用 Jellyfin REST API 获取音乐库数据，通过 Jellyfin 内置的 DLNA 投放功能将音乐推送到 DLNA 设备播放。

### 依赖
```gradle
// Retrofit (已有)
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
// OkHttp (已有)
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
// Gson (已有)
implementation 'com.google.code.gson:gson:2.10.1'
```

### Jellyfin API 认证
使用 API Key 认证，通过 `X-Emby-Token` header 传递：

```
GET /Items?parentId={id}&includeMediaTypes=Audio
Headers:
  X-Emby-Token: <api_key>
```

### 核心组件

#### JellyfinClient
- 统一处理所有 Jellyfin API 请求
- 使用 Retrofit + OkHttp
- Gson 反序列化 PascalCase JSON
- 主要端点：
  - `GET /Items` - 获取音乐库项目
  - `GET /Items/{id}/stream` - 获取音频流地址
  - `GET /Artists` - 获取艺术家列表
  - `GET /Users/{userId}/Items` - 获取用户音乐

#### MusicPlayer
- 封装 Jellyfin 音频播放
- 管理播放状态（播放/暂停/上一首/下一首）
- 支持播放列表

#### 播放流程
1. 用户选择歌曲 → MusicViewModel.playSong()
2. JellyfinClient.getStreamUrl(songId) 获取流地址
3. MusicPlayer 播放音频流
4. UI 通过 StateFlow 观察播放状态

### 音乐库浏览
支持多级浏览：
- **专辑视图**：显示所有专辑（GridLayout）
- **专辑详情**：点击专辑后显示该专辑下的歌曲和子专辑
- **艺术家视图**：显示所有艺术家
- **歌曲列表**：显示所有歌曲（LinearLayout）

MusicCategory 枚举：
```kotlin
enum class MusicCategory {
    SONGS, ALBUMS, ARTISTS, FOLDER
}
```

### DLNA 投放
使用自实现的 SSDP 发现协议发现 DLNA 设备，通过 Jellyfin 的 DLNA 功能投放音乐。

#### DLNAManager
- 使用原生 SSDP 协议发现 DLNA 设备 (M-SEARCH 广播)
- 通过 HTTP GET 获取设备描述 XML
- 管理设备列表和连接状态

### 已知问题与解决方案

#### 1. PlaybackInfo 返回 400 Bad Request
**原因**：自定义 DeviceProfile 字段与 Jellyfin 服务端不兼容

**解决**：不发送 DeviceProfile，让 Jellyfin 使用默认配置
```kotlin
// JellyfinClient.kt - getPlaybackInfo()
val playbackInfoDto = PlaybackInfoDto(
    mediaSourceId = mediaSourceId,
    maxStreamingBitrate = 100000000
)
// 不要设置 deviceProfile 字段
```

#### 2. WMA/ASF 格式无法播放 (ExoPlayer "None of the available extractors" 错误)
**原因**：ExoPlayer 不支持 asf/wma 容器，需要 Jellyfin 转码

**解决**：使用 `/Audio/{id}/stream` 端点并强制转码参数
```kotlin
// 不支持格式使用 Audio 端点 + 强制转码
val url = "$baseUrl/Audio/$songId/stream?$apiKeyParam&Container=mp4&AudioCodec=aac"
```

#### 3. mediaSourceId 必须移除 dashes
Jellyfin 服务端通过 `itemId.replace("-", "")` 查找媒体源，必须传递无 dashes 的 ID

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