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
使用 Jellyfin REST API 获取音乐库数据，播放目标统一抽象为“播放设备”：可选本机 ExoPlayer 本地播放，或通过 Jellyfin Session API 控制远程 DLNA/客户端设备播放。

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
2. 使用 Jellyfin Session API（`playItem(sessionId, songId)`）投放到已选设备
3. 播放/暂停/继续/停止/上一首/下一首/音量统一通过同一 `sessionId` 调用 Session 命令
   - 播放态命令使用 `POST /Sessions/{sessionId}/Playing/{command}`（如 `Stop/Pause/Unpause/NextTrack/PreviousTrack/Seek/SetVolume`）
4. UI 通过 StateFlow 观察播放状态

语音指令与文本指令（`IntentRouter`）与手动点击播放统一走“播放设备”路由：
- 选择本机时，走 `MusicPlayer` 本地播放与控制。
- 选择远程设备时，走 Jellyfin Session API。
- 不再回退 DLNA SOAP 控制，避免 `Failed to get control URL`。
- 播放列表页点击歌曲时，不直接复用数据库缓存的 `streamUrl`；会先按 `songId` 调 `JellyfinClient.getStreamInfo()` 获取最新 `url/playSessionId/mediaSourceId`，再交给 `MusicPlayer`，避免缓存播放参数过期导致本机不播放。
- 本机播放入队规则统一为“当前列表即当前队列”：浏览页点击歌曲时，会将当前可见歌曲列表整体入队；播放列表页点击歌曲时，会将该播放列表全部歌曲入队，并从点击项开始播放。
- 本机完整播放页采用独立 `NowPlayingActivity`，展示封面、队列位置、流状态、进度条和常用控制（上一首/播放暂停/下一首/随机/循环/队列/收藏）；通知栏点击进入该页面。
- `QueueActivity` 负责展示当前播放队列，支持查看当前曲目、点击切歌、长按拖拽排序，以及将歌曲从当前本机队列移除；该操作只影响当前播放队列，不修改用户保存的播放列表。
- `MusicPlayer` 对同一首歌的点击去重仅在“当前已处于实际播放态”时生效；如果同曲同 URL 但播放器已暂停、报错或停住，再次点击会强制重新拉起播放。
- `MusicPlayer` 使用较低的启动缓冲门槛（低延迟 `LoadControl`）以缩短进入 `STATE_READY` 的时间；播放列表页加载后会预热前 3 首歌的 `PlaybackInfo`，减少点击时的冷启动等待。
- 音频项构建流地址时统一使用 `/Audio/{id}/stream`，本机播放默认追加 `Container=mp4&AudioCodec=aac` 强制转码；已验证当前 Jellyfin 服务端的部分 `DIRECT_PLAY` 音频直链会返回 `200` 但空 body，导致 ExoPlayer 无法识别输入流。

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

### 播放设备
设备选择固定包含“本机”，并可附加从 Jellyfin 会话中发现的远程可控设备。

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

## 首页状态指示逻辑

- 首页顶部 `statusDot/tvStatus` 表示 **LLM 实际连通性**，不再由语音管道状态（IDLE/LISTENING/THINKING）驱动。
- 首页状态栏新增已选播放设备名称展示（`播放设备: xxx`），读取 `SharedPreferences` 中 Jellyfin 设备选择结果。
- `MainActivity.testLlmConnection()` 在 `onResume` 和页面初始化时执行：
  - 未配置（URL/API Key 为空）=> `LLM: 未配置` + 离线指示。
  - 已配置 => 发起一次真实 LLM 请求探测，成功显示 `LLM: 已连接`，失败显示 `LLM: 未连接`。
- 首页前台期间每 30 秒自动轮询一次 LLM 连通状态；页面进入后台时停止轮询。
- 麦克风权限被拒绝仅影响语音功能，不再覆盖 LLM 在线状态指示。

## 首页聊天历史分页（2026-04）

- 聊天记录持久化到 Room 新表 `chat_messages`，字段：
  - `id`（自增主键）
  - `text`（消息内容）
  - `isUser`（用户/助手）
  - `createdAt`（时间戳）
- 首页首次进入时仅加载**最新 20 条**（`ORDER BY createdAt DESC, id DESC LIMIT 20`），渲染时按时间正序显示。
- 上滑到顶部时触发分页加载更早记录，使用 keyset 条件：
  - `createdAt < oldest.createdAt OR (createdAt = oldest.createdAt AND id < oldest.id)`
  - 每页 20 条，避免 offset 在新消息插入后出现跳页/重复。
- 历史消息前插时会保持当前视觉位置，避免加载后列表跳动。
- 数据库版本升级到 `v4`，新增 `MIGRATION_3_4` 创建 `chat_messages` 表和 `(createdAt, id)` 索引。

## 设置页 KWS 自检

- 设置页新增 `KWS 自检` 按钮，显示当前 KWS 运行诊断：
  - 是否已初始化 (`isInitialized`)
  - 是否已启动 (`isStarted`)
  - 是否初始化失败 (`initFailed`)
  - 当前阈值 (`currentThreshold`)
  - 最近触发词、触发时间、触发置信度
- 诊断数据由 `VoicePipeline.getKwsDiagnostics()` 提供，便于快速判断“模型已加载但未触发”与“初始化失败”这两类问题。

## 设置页唤醒词限制（2026-04）

- 由于当前 Sherpa KWS 模型不支持在应用内自由添加任意中文唤醒词，设置页对唤醒词输入增加白名单限制。
- 白名单来源：模型内置 `assets/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/keywords.txt` 中的关键词列表。
- 新增、编辑、保存时都会校验关键词；不在白名单内的词会被拒绝并提示仅支持内置词。
- 设置页仍允许编辑每个唤醒词对应的回应语（如“我在”）。

## LLM 双通道意图协同（2026-04）

- `IntentRouter.handle(text)` 采用混合路由：
  - 本地快速规则先处理高确定性控制指令（`MUSIC/VOLUME/DEVICE`）。
  - `MUSIC` 本地关键词包含 `停止`，因此“停止/停止播放”会直接路由到 `stop`，不会落入聊天分支。
  - 其余输入交给 LLM 路由为 `CHAT` 或 `COMMAND`。
  - `COMMAND` 模式下，LLM 再输出结构化 JSON，由 `IntentRouter` 转换为内部 `Intent` 并执行。
  - `CHAT` 模式下走普通助手对话回复。
- 设置页新增 3 套可配置提示词（都有默认值）：
  - 助手提示词：`llm_system_prompt`
  - 路由提示词：`llm_router_prompt`
  - 命令解析提示词：`llm_command_prompt`
- `LLMRepository` 新增：
  - `routeIntent(message)`：返回 `CHAT/COMMAND`
  - `parseCommandIntent(message)`：返回结构化命令（`type/action/query/value`）
- 解析层容错：
  - 允许 LLM 返回包含附加文本，仓库层会抽取首个 JSON 对象再解析，避免因格式噪音直接失败。

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
