# 语音助手实现计划

> 基于 Sherpa-ONNX 的离线语音助手，分3级目标渐进实现

**版本**: 1.0  
**日期**: 2026-03-19  
**技术栈**: Sherpa-ONNX + Android + Kotlin

---

## 目录

1. [总体架构](#总体架构)
2. [一级目标：基础语音](#一级目标基础语音)
3. [二级目标：智能对话](#二级目标智能对话)
4. [三级目标：全屋智能](#三级目标全屋智能)
5. [技术细节](#技术细节)

---

## 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    一级目标：基础语音                        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│  │唤醒(KWS)│ → │识别(ASR)│ → │播报(TTS)│                 │
│  │Sherpa   │    │Sherpa   │    │Sherpa   │                 │
│  └─────────┘    └─────────┘    └─────────┘                 │
│                      ↓                                      │
│                 ┌─────────┐                                 │
│                 │LLM API  │                                 │
│                 │(远程)   │                                 │
│                 └─────────┘                                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    二级目标：智能对话                        │
│  新增：意图识别 + 上下文管理 + 技能路由                        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│  │意图识别 │ → │技能路由 │ → │LLM对话  │                 │
│  │(规则+AI)│    │(分发器) │    │(上下文) │                 │
│  └─────────┘    └─────────┘    └─────────┘                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    三级目标：全屋智能                        │
│  新增：Navidrome + DLNA + OpenClaw                          │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│  │音乐控制 │    │设备控制 │    │消息推送 │                 │
│  │Navidrome│    │DLNA     │    │OpenClaw │                 │
│  └─────────┘    └─────────┘    └─────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 一级目标：基础语音

### 目标描述
实现最基本的语音控制功能：唤醒→识别→意图处理→播报

### 功能清单
- [x] 语音唤醒（关键词）
- [x] 语音识别（中文→文字）
- [x] 语音合成（文字→中文语音）
- [x] 意图路由（音乐、设备控制）
- [ ] 基础UI（显示对话记录）

### 二级目标：智能对话（LLM）
- [ ] 接入 LLM API（DeepSeek/月之暗面）
- [ ] 对话上下文管理
- [ ] 工具调用（让 LLM 控制设备）

### 技术实现

#### 1.1 项目初始化
```bash
# Android Studio 新建项目
- Project: VoiceAssistant
- Package: com.voiceassistant
- minSdk: 26
- targetSdk: 34
- Language: Kotlin
```

**目录结构**:
```
app/
├── src/main/
│   ├── java/com/voiceassistant/
│   │   ├── audio/              # 音频管理
│   │   ├── sherpa/             # Sherpa-ONNX封装
│   │   ├── llm/                # LLM接口
│   │   ├── ui/                 # 界面
│   │   └── service/            # 后台服务
│   └── assets/models/          # 模型文件
```

#### 1.2 核心代码

```kotlin
// VoiceService.kt - 主服务
class VoiceService : Service() {
    private lateinit var kws: SherpaKWS
    private lateinit var asr: SherpaASR
    private lateinit var tts: SherpaTTS
    private lateinit var llm: LLMClient
    
    override fun onCreate() {
        kws.initialize("models/kws")
        asr.initialize("models/asr")
        tts.initialize("models/tts")
        startWakeWordDetection()
    }
    
    private fun onWakeWordDetected() {
        val speechAudio = recordUntilSilence()
        val text = asr.recognize(speechAudio)
        val response = runBlocking { llm.chat(text) }
        tts.speak(response) { audio -> AudioPlayer.play(audio) }
    }
}
```

### 验收标准
- [ ] 说"你好爪爪"能唤醒
- [ ] 唤醒后说话能被识别成文字
- [ ] LLM能正确回答
- [ ] 回答能被播报出来

### 预计时间
**1-2周**

---

## 二级目标：智能对话

### 目标描述
让对话更智能：理解意图、记住上下文、调用技能

### 新增功能
- [ ] 意图识别（规则+轻量AI）
- [ ] 对话上下文管理
- [ ] 技能路由系统
- [ ] 打断唤醒（说话中再次唤醒）
- [ ] 语音设置界面

### 技术实现

#### 2.1 意图识别

```kotlin
// IntentClassifier.kt
class IntentClassifier {
    
    fun classify(text: String): Intent {
        // 规则匹配
        return when {
            text.contains("播放") || text.contains("听歌") -> 
                Intent(IntentType.MUSIC, extractQuery(text))
            text.contains("天气") -> 
                Intent(IntentType.WEATHER, null)
            text.contains("提醒") || text.contains("闹钟") -> 
                Intent(IntentType.REMINDER, null)
            else -> Intent(IntentType.CHAT, null)
        }
    }
}
```

#### 2.2 技能路由

```kotlin
// SkillRouter.kt
class SkillRouter {
    private val skills = mapOf(
        IntentType.MUSIC to MusicSkill(),
        IntentType.WEATHER to WeatherSkill(),
        IntentType.CHAT to ChatSkill()
    )
    
    suspend fun handle(intent: Intent, context: DialogContext): String {
        return skills[intent.type]?.execute(intent, context) 
            ?: llm.chat(context.toPrompt())
    }
}
```

#### 2.3 上下文管理

```kotlin
// DialogContext.kt
class DialogContext {
    private val history = mutableListOf<Turn>()
    
    fun addUserMessage(text: String) {
        history.add(Turn("user", text))
    }
    
    fun addAssistantMessage(text: String) {
        history.add(Turn("assistant", text))
    }
    
    fun toPrompt(): String {
        return history.takeLast(5).joinToString("\n") { 
            "${it.role}: ${it.text}" 
        }
    }
}
```

### 验收标准
- [ ] 说"播放周杰伦的歌"能识别为音乐意图
- [ ] 连续对话能记住上下文
- [ ] 说话中可以被新唤醒打断
- [ ] 支持音量、语速等语音设置

### 预计时间
**1-2周**

---

## 三级目标：全屋智能

### 目标描述
接入智能家居：音乐控制、设备控制、消息推送

### 新增功能
- [x] Navidrome音乐控制（搜索、播放、切歌）
- [x] DLNA设备控制（发现、推送、音量）
- [x] OpenClaw消息推送
- [x] 局域网设备发现
- [x] 配置管理界面

### 技术实现

#### 3.1 Navidrome控制

```kotlin
// NavidromeSkill.kt
class NavidromeSkill {
    private val api: NavidromeApi = createApi()
    
    suspend fun play(query: String): String {
        val songs = api.search(query)
        val song = songs.firstOrNull() 
            ?: return "没找到${query}的歌曲"
        
        val dlna = DLNAManager.getDefaultRenderer()
        dlna.play(song.streamUrl)
        
        return "正在播放 ${song.title} - ${song.artist}"
    }
}
```

#### 3.2 DLNA控制

```kotlin
// DLNAManager.kt
class DLNAManager {
    private val clingService: AndroidUpnpService
    
    fun discoverRenderers(): List<DLNARenderer> {
        return clingService.registry.getDevices(DeviceType.DMR)
            .map { DLNARenderer(it) }
    }
}
```

#### 3.3 OpenClaw推送

```kotlin
// OpenClawClient.kt
class OpenClawClient(baseUrl: String) {
    private val client = OkHttpClient()
    
    fun sendMessage(text: String) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/messages")
            .post(jsonBody(mapOf(
                "type" to "voice_command",
                "text" to text,
                "source" to "voice_assistant"
            )))
            .build()
        client.newCall(request).execute()
    }
}
```

### 验收标准
- [ ] 说"播放周杰伦的晴天"能搜到并播放
- [ ] 说"音量调到50"能控制DLNA设备
- [ ] 说"问下OpenClaw明天有什么安排"能推送消息
- [ ] 局域网设备能自动发现

### 预计时间
**1-2周**

---

## 技术细节

### 模型清单

| 模块 | 模型 | 大小 | 下载地址 |
|------|------|------|----------|
| KWS | sherpa-onnx-kws-zipformer-wenetspeech | 3.3M | GitHub Releases |
| ASR | sherpa-onnx-paraformer-zh | 100M+ | GitHub Releases |
| TTS | piper-zh_CN-huayan-medium | 120M | GitHub Releases |

### 依赖配置

**项目级别** (`settings.gradle.kts`):
```gradle
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://clojars.org/repo") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/releases/") }
}
```

**核心依赖**:
```gradle
dependencies {
    // Sherpa-ONNX (本地 AAR)
    implementation(files("libs/sherpa-onnx-android.aar"))

    // 依赖注入 (Hilt 2.50)
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // 网络
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 本地数据库 (Room 2.6.1)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // 加密存储
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 日志
    implementation("com.jakewharton.timber:timber:5.0.1")

    // AndroidX 核心
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
```

### 权限配置

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## 开发里程碑

| 里程碑 | 时间 | 产出 |
|--------|------|------|
| M1 | Week 1 | 一级目标完成，基础对话跑通 |
| M2 | Week 2 | 二级目标完成，智能对话可用 |
| M3 | Week 3 | 三级目标完成，全屋智能接入 |
| M4 | Week 4 | 测试优化，打包发布 |

---

## 风险控制

### 技术风险

| 风险 | 可能性 | 影响 | 应对 |
|------|--------|------|------|
| Sherpa-ONNX Android集成困难 | 中 | 高 | 提前做POC验证，准备备选方案 |
| 旧手机性能不足 | 高 | 中 | 选用tiny模型，降低采样率 |
| 唤醒词误触发 | 中 | 中 | 调整敏感度，增加二次确认 |

### 进度风险

| 风险 | 可能性 | 影响 | 应对 |
|------|--------|------|------|
| 模型下载慢 | 高 | 低 | 提前下载好，本地备份 |
| LLM API不稳定 | 中 | 中 | 准备多个API备用 |
| DLNA设备兼容性 | 中 | 中 | 先用模拟器测试 |

### 质量风险

| 风险 | 可能性 | 影响 | 应对 |
|------|--------|------|------|
| 语音识别准确率低 | 中 | 高 | 选用更好的模型，优化音频质量 |
| TTS音质机械 | 低 | 中 | 选用medium/high质量模型 |
| 延迟过高 | 中 | 高 | 流式处理，异步加载 |

---

## 下一步行动

### 立即开始（今天）
1. [ ] Android Studio新建项目
2. [ ] 下载Sherpa-ONNX Android示例
3. [ ] 下载KWS/ASR/TTS模型

### 本周完成
1. [ ] Sherpa-ONNX集成
2. [ ] 基础唤醒+识别+合成跑通
3. [ ] LLM API接入

### 下周完成
1. [ ] 一级目标验收
2. [ ] 开始二级目标开发

---

*计划版本: 1.0 | 最后更新: 2026-03-19*