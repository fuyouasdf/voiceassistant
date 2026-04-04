<!-- PROJECT_NAME_START -->
# 🤖 Voice Assistant

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-blue.svg" alt="Android Version">
  <img src="https://img.shields.io/badge/Kotlin-1.9-orange.svg" alt="Language">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License">
</p>

> 🇺🇸 English | [中文](README.md)

Turn your old Android phone into an offline voice control hub with Sherpa-ONNX, supporting Jellyfin/DLNA music control.

---

## ✨ Features

- 🔇 **Fully Offline** - ASR, Wake Word, TTS all run locally
- 🎵 **Music Control** - Jellyfin + DLNA push playback
- 🧠 **Intent Routing** - Local rules + LLM chat
- 🎤 **Multi-model Support** - Sherpa-ONNX based
- 📱 **Background Service** - Foreground service with battery optimization
- 🎨 **Modern UI** - Fluid gradient animations

---

## 🚀 Quick Start

### Requirements

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1.1)+ |
| JDK | 17 |
| Android SDK | 36 (compileSdk) |
| targetSdk | 35 |
| Device | Android 8.0+ (API 26) |

### Build

```bash
# Clone
git clone https://github.com/your-repo/voice-assistant.git
cd voice-assistant

# Enter Android project
cd android

# Build debug APK
./gradlew :app:assembleDebug

# Open in Android Studio
# File -> Open -> Select android/ directory

# Run on device
```

---

## 📖 Usage

### Voice Commands

```
🎵 Play music - Play songs by artist or title
💡 Turn on/off lights - Smart home control
🌤️ Weather - Check local weather
⏰ Alarm - Set reminders
❓ Ask anything - LLM chat (requires API config)
```

### Interaction

| Action | Function |
|--------|----------|
| Say wake word | "你好爪爪" to activate |
| Press and hold | Hold button to record |
| Swipe up | Interrupt current operation |
| Double tap | Repeat last response |

---

## 🏗️ Architecture

```
UI Layer (ViewBinding/MVVM)
         ↓
Domain Layer (Use Cases)
         ↓
Core - Voice Pipeline
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│  KWS   │ │  VAD   │ │  ASR   │ │  TTS   │
│(Wake)  │ │(VAD)   │ │(ASR)   │ │(TTS)   │
└────────┘ └────────┘ └────────┘ └────────┘
      Sherpa-ONNX Engine
         ↓
Skills: Jellyfin | DLNA | LLM | OpenClaw
```

---

## 🔧 Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 1.9 |
| Framework | Android Jetpack |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Voice | Sherpa-ONNX |
| Network | Retrofit + OkHttp |
| Database | Room |
| Async | Coroutines + Flow |

---

## 📦 Models

Models are already included in the repository under `android/app/src/main/assets/`:

| Model | Size | Purpose |
|-------|------|---------|
| sherpa-onnx-kws-zipformer-wenetspeech-3.3M | ~35MB | Wake word detection |
| sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30 | ~200MB | Speech recognition |
| silero_vad.onnx | ~2MB | Voice activity detection |
| vits-piper-zh_CN-huayan-medium | ~61MB | Speech synthesis |

---

## 🤝 Contributing

1. Fork the repo
2. Create feature branch
3. Commit your changes
4. Push to branch
5. Create Pull Request

---

## 📄 License

```
Licensed under the Apache License, Version 2.0
```

---

<p align="center">
  Made with ❤️
</p>
