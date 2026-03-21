<!-- PROJECT_NAME_START -->
# 🤖 Voice Assistant

<p align="center">
  <img src="https://img.shields.io/badge/Android-6.0%2B-blue.svg" alt="Android Version">
  <img src="https://img.shields.io/badge/Kotlin-1.9-orange.svg" alt="Language">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License">
</p>

> 🇺🇸 English | [中文](README.md)

Turn your old Android phone into an offline voice control hub with Sherpa-ONNX, supporting Navidrome/DLNA music control.

---

## ✨ Features

- 🔇 **Fully Offline** - ASR, Wake Word, TTS all run locally
- 🎵 **Music Control** - Navidrome + DLNA push playback
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
| Android SDK | 34 |
| Device | Android 6.0+ (API 23) |

### Build

```bash
# Clone
git clone https://github.com/your-repo/voice-assistant.git
cd voice-assistant

# Download models
cd android
./scripts/download_models.sh

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
UI Layer (Jetpack Compose/MVVM)
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
Skills: Navidrome | DLNA | LLM | OpenClaw
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

Models auto-download on first run (~200MB total):

| Model | Size | Purpose |
|-------|------|---------|
| Sherpa KWS | ~3MB | Wake word detection |
| Paraformer ASR | ~80MB | Speech recognition |
| Piper TTS | ~120MB | Speech synthesis |

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