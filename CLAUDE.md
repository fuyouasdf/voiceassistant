# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Voice Assistant - 将旧 Android 手机变成离线语音控制中枢，支持多模型 API 接入、Navidrome/DLNA 音乐控制。

**技术栈**: Kotlin + MVVM + Clean Architecture + Sherpa-ONNX + Hilt
**minSdk**: 26 | **targetSdk**: 34

---

## 构建命令

```bash
cd android

# 完整构建
./gradlew build

# 调试版
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 下载模型文件 (首次构建前必须)
./gradlew :app:downloadModels

# 构建 Sherpa-ONNX AAR (需要 NDK/CMake)
./gradlew :sherpa-onnx-aar:sherpa_onnx:assembleRelease

# 运行单元测试
./gradlew :core:testDebugUnitTest
./gradlew :domain:testDebugUnitTest
```

---

## 项目结构

```
voice-assistant/
├── app/              # UI 层 + 前台服务 + 模型初始化
├── core/             # 语音管道核心 (KWS/VAD/ASR/TTS)
├── data/             # 数据层 (Room/Retrofit/Jellyfin)
├── domain/           # 领域层 (UseCases/Repository接口)
├── sherpa-onnx-aar/  # Sherpa-ONNX 本地 AAR 模块 (语音部分参考)
└── jellyfin-android/ # Jellyfin Android SDK (经过验证，直接使用)
```

---

## 代码优先级 (重要)

### sherpa-onnx-aar/ - 语音部分参考

`android/sherpa-onnx-aar/` 目录包含 **Sherpa-ONNX 本地库**，语音管道部分可参考：

- KWS (唤醒词检测) 实现参考
- VAD (语音活动检测) 实现参考
- ASR (语音识别) 实现参考
- TTS (语音合成) 实现参考

**相关文件**:
- `android/core/src/main/java/com/voiceassistant/core/sherpa/` - 语音引擎接口
- `android/sherpa-onnx-aar/sherpa_onnx/src/main/java/com/k2fsa/sherpa/onnx/` - Sherpa-ONNX Kotlin API

---

## 关键路径

- 模型目录: `android/app/src/main/assets/models/`
- 主服务: `VoiceAssistantService.kt`
- 管道控制器: `VoicePipeline.kt`
- 音频捕获: `AudioCapture.kt` (16kHz, mono, PCM_FLOAT)

---

## 核心架构

### 语音管道状态机

```
IDLE → LISTENING → RECORDING → RECOGNIZING → THINKING → SPEAKING → IDLE
```

**核心组件**:
- `VoicePipeline` - 状态机控制器
- `AudioCapture` - 麦克风输入
- Sherpa-ONNX: KWS → VAD → ASR → TTS
- `IntentRouter` - 意图路由

### 音频格式 (重要)

| 阶段 | 格式 | 采样率 |
|------|------|--------|
| 录音输入 | PCM_FLOAT | 16kHz |
| TTS输出 | FloatArray → PCM_16BIT | 22050Hz (花燕模型) |
| 播放 | AudioTrack PCM_16BIT | 22050Hz |

**TTS播放必须转换**: FloatArray (-1.0~1.0) → ShortArray (-32768~32767)

---

## 重要已知问题 (必读)

### 1. ASR 流式识别状态管理
在循环内检查 `r.isEndpoint(stream)` 会导致过早触发。正确做法：**先处理完所有音频，再统一检查 endpoint**。

**相关文件**: `android/core/src/main/java/com/voiceassistant/core/sherpa/SherpaASRImpl.kt`

### 2. AudioPlayer PCM 格式
必须使用 `ENCODING_PCM_16BIT`，不能使用 `ENCODING_PCM_FLOAT`。SherpaTTS 输出 FloatArray 需要手动转换为 ShortArray。

### 3. TTS 合成速度
vits-melo-tts-zh_en 模型过大(163MB)，合成慢。花燕模型(vits-piper-zh_CN-huayan-medium, 60MB)是更好的选择。

### 4. Jellyfin 播放问题
**PlaybackInfo 400 错误**：不要发送 DeviceProfile，让 Jellyfin 使用默认配置。

**WMA/ASF 无法播放**：ExoPlayer 不支持 asf 容器，需使用 `/Audio/{id}/stream?Container=mp4&AudioCodec=aac` 强制转码。

**mediaSourceId 必须去 dashes**：Jellyfin 服务端通过 `itemId.replace("-", "")` 查找媒体源。

**相关文件**: `android/data/src/main/java/com/voiceassistant/data/remote/JellyfinClient.kt`
jellyfin 的接口必须要使用 playwright mcp来进行验证接口格式
地址192.168.31.206:8096
账号：app
密码：qweqwe
---

## 文档链接

| 主题 | 文档 |
|------|------|
| 项目概述 | [VOICE_ASSISTANT_DESIGN.md](VOICE_ASSISTANT_DESIGN.md) |
| Android 架构 | [ARCHITECTURE_ANDROID.md](ARCHITECTURE_ANDROID.md) |
| 语音管道 | [ARCHITECTURE_VOICE_PIPELINE.md](ARCHITECTURE_VOICE_PIPELINE.md) |
| 快速开始 | [android/QUICKSTART.md](android/QUICKSTART.md) |
| 进度追踪 | [android/PROGRESS.md](android/PROGRESS.md) |
| 小爱风格设计 | [XIAOAI_DESIGN.md](XIAOAI_DESIGN.md) |

---

## 文档同步规则

每次修改代码后，必须同步更新相关文档：

| 修改类型 | 更新文档 |
|----------|----------|
| 功能逻辑 | `ARCHITECTURE_ANDROID.md` 或 `VOICE_ASSISTANT_DESIGN.md` |
| 模块结构 | `ARCHITECTURE_ANDROID.md` |
| 语音管道 | `ARCHITECTURE_VOICE_PIPELINE.md` |
| 新增 API/接口 | 对应模块的 README |

---

## 编码规则

1. **不许无提示崩溃** — 错误必须可理解，禁止静默失败
2. **外部输入必须校验** — 用户输入、文件、网络数据、API返回值
3. **严格分层** — 禁止跨层乱写（ui → domain → data）
4. **单一职责** — 一个函数只做一件事
5. **代码修改后必须编译检查**
