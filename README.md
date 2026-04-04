<!-- PROJECT_NAME_START -->
# 🤖 Voice Assistant

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-blue.svg" alt="Android Version">
  <img src="https://img.shields.io/badge/Kotlin-1.9-orange.svg" alt="Language">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License">
</p>

> 🇨🇳 中文 | [English](README_EN.md)

将旧 Android 手机变成离线语音控制中枢，替代小爱同学，支持多模型 API 接入、Jellyfin/DLNA 音乐控制。

<!-- PROJECT_NAME_END -->

---

## 📚 文档导航

| 文档 | 内容 |
|------|------|
| **[项目状态](PROJECT_STATUS.md)** | 当前进度、已知问题、下一步计划 |
| **[Android 架构](ARCHITECTURE_ANDROID.md)** | 技术栈、模块结构、核心组件 |
| **[语音管道](ARCHITECTURE_VOICE_PIPELINE.md)** | 状态机、音频流、核心类设计 |
| **[快速开始](android/QUICKSTART.md)** | 环境配置、构建步骤、测试流程 |
| **[进度报告](android/PROGRESS.md)** | 版本历史、模块完成情况 |

---

## ✨ 特性

- 🔇 **完全离线** - 语音识别、唤醒词、TTS 全部本地运行
- 🎵 **音乐控制** - 支持 Jellyfin + DLNA 推送播放
- 🧠 **意图路由** - 本地规则匹配 + LLM 对话
- 🎤 **多模型支持** - 基于 Sherpa-ONNX，一站式语音方案
- 📱 **后台运行** - 前台服务 + 电池优化白名单
- 🎨 **现代 UI** - 流体渐变动画 + 直观状态反馈

---

## 🏗️ 架构

详细架构设计请参考：[Android 架构](ARCHITECTURE_ANDROID.md) | [语音管道](ARCHITECTURE_VOICE_PIPELINE.md)

```
┌─────────────────────────────────────────────────────────────┐
│                      Android 客户端                          │
├─────────────────────────────────────────────────────────────┤
│  UI Layer (MVVM)                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  MainActivity│  │SettingsView │  │  Dialogs    │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer (Use Cases)                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │StartVoice│  │SendCmd   │  │ConfigUseCase              │
│  │Pipeline  │  │ToLLM     │  │                      │    │
│  └──────────┘  └──────────┘  └──────────┘                 │
├─────────────────────────────────────────────────────────────┤
│  Core - Voice Pipeline (Sherpa-ONNX)                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │   KWS    │  │   VAD    │  │   ASR    │  │   TTS    │ │
│  │(唤醒检测) │  │(端点检测) │  │(语音识别) │  │(语音合成) │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
├─────────────────────────────────────────────────────────────┤
│  Skills                                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │Jellyfin  │  │ DLNA控制 │  │LLM对话   │  │OpenClaw │  │
│  │  Subsonic│  │  UPnP    │  │ (可选)   │  │消息推送 │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 语音管道状态机

```
INITIALIZING → IDLE → WAKEWORD_DETECTED → LISTENING → RECORDING → RECOGNIZING → THINKING → SPEAKING → IDLE
```

详细状态机说明见 [语音管道文档](ARCHITECTURE_VOICE_PIPELINE.md)

---

## 🚀 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Hedgehog (2023.1.1)+ |
| JDK | 17 |
| Android SDK | 36 (compileSdk) |
| targetSdk | 35 |
| 测试设备 | Android 8.0+ (API 26) |

### 构建步骤

```bash
# 1. 克隆项目
git clone https://github.com/your-repo/voice-assistant.git
cd voice-assistant

# 2. 进入 Android 工程
cd android

# 3. 打开项目
# Android Studio -> Open -> 选择 android/ 目录

# 4. 编译调试版
./gradlew :app:assembleDebug

# 5. 运行
# 连接 Android 设备，Run -> Run 'app'
```

---

## 📖 使用说明

### 交互方式

| 操作 | 功能 |
|------|------|
| 说出唤醒词 | 唤醒语音助手（默认: 你好爪爪） |
| 按住说话 | 按住主按钮录音，松开结束 |
| 上滑打断 | 在按钮上向上滑动可打断当前操作 |
| 双击圆球 | 重复播放上次回复 |
| ⚡ 按钮 | 打断正在播报的回复 |

### 支持的语音命令

```
🎵 播放音乐 - 播放指定歌曲或歌手的作品
💡 开灯/关灯 - 控制智能家居设备
🌤️ 问天气 - 查询当地天气
⏰ 设闹钟 - 设置提醒
❓ 提问 - LLM 对话（需配置 API）
```

### 状态说明

| 状态 | 说明 |
|------|------|
| 🔴 待机中 | 等待唤醒词 |
| 🔵 正在倾听 | 检测到唤醒词，等待语音 |
| 🟠 正在录音 | 正在录制语音 |
| 🟡 识别中 | 正在识别语音 |
| 🟣 思考中 | 处理意图中 |
| 🟢 正在说话 | TTS 播报中 |

---

## 📁 项目结构

```
voice-assistant/
├── android/                      # Android 项目根目录
│   ├── app/                     # 应用层 (UI + Service)
│   ├── core/                    # 核心语音管道
│   ├── data/                    # 数据层
│   ├── domain/                  # 领域层
│   └── sherpa-onnx-aar/        # Sherpa-ONNX 本地 AAR
├── ARCHITECTURE_ANDROID.md      # Android 架构详细文档
├── ARCHITECTURE_VOICE_PIPELINE.md # 语音管道详细文档
├── PROJECT_STATUS.md             # 项目当前状态
└── README.md                    # 本文件
```

详细目录结构请参考：[Android 架构 - 完整目录结构](ARCHITECTURE_ANDROID.md#完整目录结构)

---

## 🔧 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Kotlin 1.9 |
| 框架 | Android Jetpack |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt |
| 语音引擎 | Sherpa-ONNX |
| 网络 | Retrofit + OkHttp |
| 数据库 | Room |
| 异步 | Kotlin Coroutines + Flow |

---

## 📦 模型资源

当前仓库已包含语音模型资源（位于 `android/app/src/main/assets/`），无需额外执行下载任务:

| 模型 | 大小 | 用途 |
|------|------|------|
| sherpa-onnx-kws-zipformer-wenetspeech-3.3M | ~35MB | 唤醒词检测 |
| sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30 | ~200MB | 语音识别 |
| silero_vad.onnx | ~2MB | 端点检测 |
| vits-piper-zh_CN-huayan-medium | ~61MB | 语音合成 |

---

## ⚙️ 配置说明

服务配置在应用内设置界面完成（设置 → 服务配置），配置保存在本地数据库。

详见：[快速开始 - 配置服务](android/QUICKSTART.md#配置服务)

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
```

See [LICENSE](LICENSE) for details.

---

## 🙏 致谢

- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) - 一站式语音方案
- [K2 AI](https://github.com/k2-fsa) - 优秀的开源语音项目

---

## 📞 联系方式

- GitHub Issues: [https://github.com/your-repo/voice-assistant/issues](https://github.com/your-repo/voice-assistant/issues)
- Email: fuyouasdf@gmail.com

---

<p align="center">
  Made with ❤️ by Voice Assistant Team
</p>
