# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android voice assistant app that runs offline on old phones, using Sherpa-ONNX for wake word detection (KWS), speech recognition (ASR), and text-to-speech (TTS). Integrates with remote LLM APIs, Navidrome music server, and DLNA devices.

## Build Commands

```bash
# Build the project (from android/ directory)
cd android
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK to connected device
./gradlew installDebug

# Run tests
./gradlew test

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Project Structure

Multi-module Android project using Clean Architecture:

- **app/** - UI layer (Activities, Fragments, ViewModels, Services)
- **core/** - Voice pipeline implementation (Sherpa-ONNX wrappers, audio processing)
- **data/** - Repository implementations, API services, local database
- **domain/** - Business logic interfaces, use cases, domain models

## Architecture

**Voice Pipeline State Machine**: IDLE → LISTENING → RECORDING → RECOGNIZING → THINKING → SPEAKING → IDLE

Key components:
- `VoicePipeline` - Main state machine controller
- `AudioCapture` - Microphone input handling
- Sherpa-ONNX engines: KWS (wake word), VAD (voice activity), ASR (speech-to-text), TTS (text-to-speech)
- `IntentRouter` - Routes recognized text to appropriate handlers (LLM, music control, DLNA)

## Key Technologies

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34
- **DI**: Hilt/Dagger
- **Async**: Coroutines + Flow
- **Database**: Room
- **Network**: Retrofit + OkHttp
- **Voice**: Sherpa-ONNX (via JNI)
- **DLNA**: Cling library

## Model Files

Voice models stored in `android/app/src/main/assets/models/`:
- `kws/` - Wake word detection models
- `asr/` - Speech recognition models
- `tts/` - Text-to-speech models

Models are initialized on first app launch via `ModelInitializer`.

## Important Notes

- Voice processing runs in foreground service (`VoiceAssistantService`) for reliability
- Audio capture uses 16kHz, mono, PCM_FLOAT format
- Pipeline uses circular buffer to preserve pre-wake-word audio
- All file paths must use absolute Windows paths with backslashes (e.g., `C:\Users\...`)
