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

## 🎯 Use Cases

**This is a LAN voice control app designed for home music enthusiasts.**

### Typical Flow

```
┌──────────────┐    Voice Command     ┌──────────────────┐    HTTP/DLNA    ┌──────────────┐
│  Old Phone   │ ───────────────────→ │  Voice Assistant │ ──────────────→ │  Jellyfin   │
│  (Voice)     │    "Play music"     │   (Local)        │    Get music    │   (NAS)      │
└──────────────┘                      └──────────────────┘                 └──────────────┘
                                         │
                                         │ DLNA Push
                                         ↓
                               ┌──────────────────┐
                               │   DLNA Player    │
                               │ (Speaker/TV/Box) │
                               └──────────────────┘
```

### Is this for you?

| Your Situation | Recommendation |
|---------------|----------------|
| NAS with music library | ⭐⭐⭐⭐⭐ |
| Jellyfin on NAS | ⭐⭐⭐⭐⭐ |
| DLNA/UPnP device (speaker, amp, TV) | ⭐⭐⭐⭐⭐ |
| Old phone as voice control | ⭐⭐⭐⭐⭐ |
| Pure LAN operation, no internet | ⭐⭐⭐⭐⭐ |
| Keep voice data local | ⭐⭐⭐⭐⭐ |

**Common Use Cases:**

1. **Living Room Speaker Control** - Say "Play Jay Chou" from sofa, music streams from NAS to amp
2. **Bedroom Music Time** - Old phone on nightstand, voice control "next song", "pause"
3. **Study Background Music** - Voice request while working, audio from studio monitors
4. **Party Mode** - "Play upbeat music", party playlist starts

---

### Prerequisites

| Requirement | Description | Required |
|-------------|-------------|----------|
| Android Device | Android 8.0+ (API 26), old phone recommended | ✅ |
| Jellyfin Server | Music library on home NAS, same LAN | ✅ |
| DLNA Player | DLNA/UPnP capable speaker, TV, or player | ✅ |
| LAN | Devices on same WiFi | ✅ |
| Local Models (optional) | Built-in Chinese ASR/TTS, no internet needed | ❌ |

---

### Why choose this?

| Comparison | Commercial (Xiaomi/Baidu) | This Project |
|------------|---------------------------|--------------|
| Privacy | Voice data uploaded to server | 100% local processing |
| Network | Must be online | Fully offline capable |
| Music Control | Own platform only | Jellyfin + DLNA |
| Flexibility | Limited ecosystem | Fully open source |
| Cost | Must buy specific devices | Use old phone |

---

## ✨ Features

- 🔇 **Fully Offline** - ASR, Wake Word, TTS all run locally, no data upload
- 🎵 **Jellyfin Integration** - Play music directly from Jellyfin
- 📻 **DLNA Push** - Push music to DLNA-capable devices
- 🎤 **Local ASR/TTS** - Based on Sherpa-ONNX, no network required
- 📱 **Background Service** - Foreground service with battery optimization whitelist
- 🎨 **XiaoAI-style UI** - Fluid gradient animations + intuitive status feedback

---

## 🏗️ Architecture

For detailed architecture: [Android Architecture](ARCHITECTURE_ANDROID.md) | [Voice Pipeline](ARCHITECTURE_VOICE_PIPELINE.md)

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Client                          │
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
│  │(WakeWord)│  │(VAD)     │  │(ASR)     │  │(TTS)     │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
├─────────────────────────────────────────────────────────────┤
│  Skills                                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │Jellyfin  │  │ DLNA    │  │LLM Chat  │  │OpenClaw  │  │
│  │ Subsonic│  │ UPnP    │  │(Optional)│  │Push      │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Voice Pipeline State Machine

```
INITIALIZING → IDLE → WAKEWORD_DETECTED → LISTENING → RECORDING → RECOGNIZING → THINKING → SPEAKING → IDLE
```

See [Voice Pipeline Docs](ARCHITECTURE_VOICE_PIPELINE.md) for state machine details.

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

### Build Steps

```bash
# 1. Clone the project
git clone https://github.com/your-repo/voice-assistant.git
cd voice-assistant

# 2. Enter Android project
cd android

# 3. Open in Android Studio
# Android Studio -> Open -> Select android/ directory

# 4. Build debug APK
./gradlew :app:assembleDebug

# 5. Run
# Connect Android device, Run -> Run 'app'
```

---

## 📖 Usage

### Interactions

| Action | Function |
|--------|----------|
| Say wake word | Activate assistant (default: 你好爪爪) |
| Press and hold | Hold to record, release to end |
| Swipe up | Interrupt current operation |
| Double tap | Repeat last response |
| ⚡ Button | Stop ongoing speech |

### Supported Voice Commands

```
🎵 Play music - Play songs by artist or title
💡 Turn on/off lights - Smart home control
🌤️ Weather - Check local weather
⏰ Alarm - Set reminders
❓ Ask anything - LLM chat (requires API config)
```

### Status Indicators

| Status | Description |
|--------|-------------|
| 🔴 Standby | Waiting for wake word |
| 🔵 Listening | Wake word detected, waiting for voice |
| 🟠 Recording | Recording voice |
| 🟡 Recognizing | Processing speech |
| 🟣 Thinking | Intent processing |
| 🟢 Speaking | TTS playback |

---

## 📁 Project Structure

```
voice-assistant/
├── android/                      # Android project root
│   ├── app/                     # App layer (UI + Service)
│   ├── core/                    # Core voice pipeline
│   ├── data/                    # Data layer
│   ├── domain/                  # Domain layer
│   └── sherpa-onnx-aar/        # Sherpa-ONNX AAR
├── ARCHITECTURE_ANDROID.md      # Android architecture docs
├── ARCHITECTURE_VOICE_PIPELINE.md # Voice pipeline docs
├── PROJECT_STATUS.md             # Project status
└── README.md                    # This file (Chinese)
```

For full directory structure: [Android Architecture - Complete Structure](ARCHITECTURE_ANDROID.md#complete-directory-structure)

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

Voice models are included in the repository under `android/app/src/main/assets/`:

| Model | Size | Purpose |
|-------|------|---------|
| sherpa-onnx-kws-zipformer-wenetspeech-3.3M | ~35MB | Wake word detection |
| sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30 | ~200MB | Speech recognition |
| silero_vad.onnx | ~2MB | Voice activity detection |
| vits-piper-zh_CN-huayan-medium | ~61MB | Speech synthesis |

---

## ⚙️ Configuration

Service configuration is done in-app (Settings → Service Configuration), saved to local database.

See: [Quick Start - Service Configuration](android/QUICKSTART.md#configuration)

---

## 📚 Documentation

| Document | Content |
|----------|---------|
| **[Project Status](PROJECT_STATUS.md)** | Current progress, known issues, next steps |
| **[Android Architecture](ARCHITECTURE_ANDROID.md)** | Tech stack, module structure, core components |
| **[Voice Pipeline](ARCHITECTURE_VOICE_PIPELINE.md)** | State machine, audio flow, core class design |
| **[Quick Start](android/QUICKSTART.md)** | Environment setup, build steps, testing |
| **[Progress Report](android/PROGRESS.md)** | Version history, module completion |

---

## 🤝 Contributing

Issues and Pull Requests are welcome!

---

## 📄 License

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
```

See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) - All-in-one voice solution
- [K2 AI](https://github.com/k2-fsa) - Excellent open source voice projects

---

## 📞 Contact

- GitHub Issues: [https://github.com/your-repo/voice-assistant/issues](https://github.com/your-repo/voice-assistant/issues)
- Email: fuyouasdf@gmail.com

---

<p align="center">
  Made with ❤️ by Voice Assistant Team
</p>
