<!-- PROJECT_NAME_START -->
# 🤖 Voice Assistant

<p align="center">
  <img src="https://img.shields.io/badge/Android-6.0%2B-blue.svg" alt="Android Version">
  <img src="https://img.shields.io/badge/Kotlin-1.9-orange.svg" alt="Language">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License">
  <img src="https://img.shields.io/github/stars/your-repo/voice-assistant?style=social" alt="GitHub Stars">
</p>

> 🇨🇳 中文 | [English](README_EN.md)

将旧 Android 手机变成离线语音控制中枢，替代小爱同学，支持多模型 API 接入、Navidrome/DLNA 音乐控制。

<!-- PROJECT_NAME_END -->

---

## ✨ 特性

- 🔇 **完全离线** - 语音识别、唤醒词、TTS 全部本地运行
- 🎵 **音乐控制** - 支持 Navidrome + DLNA 推送播放
- 🧠 **意图路由** - 本地规则匹配 + LLM 对话
- 🎤 **多模型支持** - 基于 Sherpa-ONNX，一站式语音方案
- 📱 **后台运行** - 前台服务 + 电池优化白名单
- 🎨 **现代 UI** - 流体渐变动画 + 直观状态反馈

---

## 🏗️ 架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Android 客户端                          │
├─────────────────────────────────────────────────────────────┤
│  UI Layer                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  MainActivity│  │SettingsView │  │  Dialogs    │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
└─────────┼────────────────┼────────────────┼────────────────┘
          │                │                │
┌─────────┴────────────────┴────────────────┴────────────────┐
│  Domain Layer                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │StartVoice│  │SendCmd   │  │ConfigUseCase              │
│  │Pipeline  │  │ToLLM     │  │                      │    │
│  └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
          │
┌─────────┴────────────────────────────────────────────────┐
│  Core - Voice Pipeline                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │   KWS    │  │   VAD    │  │   ASR    │  │   TTS    │ │
│  │(唤醒检测) │  │(端点检测) │  │(语音识别) │  │(语音合成) │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
│                    Sherpa-ONNX Engine                      │
└─────────────────────────────────────────────────────────────┘
          │
┌─────────┴────────────────────────────────────────────────┐
│  Skills                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │Navidrome │  │ DLNA控制 │  │LLM对话   │  │OpenClaw │  │
│  │  Subsonic│  │  UPnP    │  │ (可选)   │  │消息推送 │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 语音管道状态机

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

---

## 🚀 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Hedgehog (2023.1.1)+ |
| JDK | 17 |
| Android SDK | 34 |
| 测试设备 | Android 6.0+ (API 23) |

### 构建步骤

```bash
# 1. 克隆项目
git clone https://github.com/your-repo/voice-assistant.git
cd voice-assistant

# 2. 下载模型文件
cd android
./gradlew :app:downloadModels

# 3. 打开项目
# Android Studio -> Open -> 选择 android/ 目录

# 4. 配置 Navidrome (可选)
# 修改 app/build.gradle.kts 中的配置
buildConfigField("String", "NAVIDROME_URL", "\"http://192.168.1.x:4533/\"")
buildConfigField("String", "NAVIDROME_USERNAME", "\"admin\"")
buildConfigField("String", "NAVIDROME_PASSWORD", "\"password\"")

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
│   ├── app/                     # 应用层
│   │   └── src/main/
│   │       ├── java/com/voiceassistant/app/
│   │       │   ├── ui/main/     # 主界面
│   │       │   ├── service/     # 前台服务
│   │       │   └── di/          # 依赖注入
│   │       ├── res/             # 资源文件
│   │       └── assets/models/   # 语音模型
│   ├── core/                    # 核心语音管道
│   │   └── src/main/java/com/voiceassistant/core/
│   │       ├── audio/           # 音频采集
│   │       ├── sherpa/          # Sherpa-ONNX 封装
│   │       ├── pipeline/        # 语音管道
│   │       └── intent/          # 意图路由
│   ├── data/                    # 数据层
│   │   └── src/main/java/com/voiceassistant/data/
│   │       ├── repository/     # 仓库实现
│   │       └── remote/          # API 接口
│   ├── domain/                  # 领域层
│   │   └── src/main/java/com/voiceassistant/domain/
│   │       ├── repository/      # 仓库接口
│   │       └── usecase/        # 用例
│   └── sherpa-onnx-aar/        # Sherpa-ONNX 本地 AAR
├── VOICE_ASSISTANT_DESIGN.md    # 设计文档
├── ARCHITECTURE_ANDROID.md      # 架构文档
├── ARCHITECTURE_VOICE_PIPELINE.md # 语音管道文档
└── README.md                    # 本文件
```

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

## 📦 模型下载

项目使用以下模型（首次启动自动下载）:

| 模型 | 大小 | 用途 |
|------|------|------|
| Sherpa-ONNX KWS | ~3MB | 唤醒词检测 |
| Paraformer ASR | ~80MB | 语音识别 |
| Piper TTS | ~120MB | 语音合成 |

---

## ⚙️ 配置说明

### BuildConfig 配置

```kotlin
// app/build.gradle.kts
buildConfigField("String", "NAVIDROME_URL", "\"http://192.168.1.x:4533/\"")
buildConfigField("String", "NAVIDROME_USERNAME", "\"admin\"")
buildConfigField("String", "NAVIDROME_PASSWORD", "\"password\"")

// LLM 配置 (可选)
buildConfigField("String", "LLM_API_KEY", "\"your-api-key\"")
buildConfigField("String", "LLM_BASE_URL", "\"https://api.deepseek.com\"")
```

### 唤醒词修改

在模型配置文件中修改 `wake_word` 参数。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/xxx`)
3. 提交更改 (`git commit -m 'Add xxx'`)
4. 推送分支 (`git push origin feature/xxx`)
5. 创建 Pull Request

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
- Email: your-email@example.com

---

<p align="center">
  Made with ❤️ by Voice Assistant Team
</p>