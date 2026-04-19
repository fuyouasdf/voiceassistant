# Voice Assistant

将旧 Android 手机变成离线语音控制中枢，支持多模型 API 接入、Jellyfin/DLNA 音乐控制。

**技术栈**: Kotlin + MVVM + Clean Architecture + Sherpa-ONNX + Hilt
**SDK**: minSdk 26 | targetSdk 35 | compileSdk 36

---

## 项目结构

```
voice-assistant/
├── app/              # UI 层 + 前台服务 + 模型初始化
├── core/             # 语音管道核心 (KWS/VAD/ASR/TTS)
├── data/             # 数据层 (Room/Retrofit/Jellyfin)
├── domain/           # 领域层 (UseCases/Repository接口)
└── sherpa-onnx-aar/ # Sherpa-ONNX 本地库 (参考)
```

---

## 构建

```bash
cd android

./gradlew assembleDebug      # 调试版
./gradlew installDebug      # 安装到设备
./gradlew :core:testDebugUnitTest  # 单元测试
```

---

## 关键路径

| 组件 | 路径 |
|------|------|
| 模型目录 | `android/app/src/main/assets/` |
| 主服务 | `VoiceAssistantService.kt` |
| 管道控制器 | `VoicePipeline.kt` |
| 音频捕获 | `AudioCapture.kt` (16kHz, mono, PCM_FLOAT) |
| Sherpa ASR | `core/src/main/java/.../sherpa/SherpaASRImpl.kt` |
| Jellyfin | `data/src/main/java/.../remote/JellyfinClient.kt` |

---

## 语音管道状态机

```
INITIALIZING → IDLE → WAKEWORD_DETECTED → LISTENING → RECORDING →
RECOGNIZING → THINKING → SPEAKING → IDLE
```

核心: `VoicePipeline` · `AudioCapture` · Sherpa-ONNX (KWS→VAD→ASR→TTS) · `IntentRouter`

---

## 音频格式

| 阶段 | 格式 | 采样率 |
|------|------|--------|
| 录音输入 | PCM_FLOAT | 16kHz |
| TTS输出 | FloatArray → PCM_16BIT | 22050Hz (花燕模型) |
| 播放 | AudioTrack PCM_16BIT | 22050Hz |

**TTS 播放必须转换**: FloatArray (-1.0~1.0) → ShortArray (-32768~32767)

---

## 已知问题 (必读)

1. **ASR 流式识别** - 循环内检查 `isEndpoint()` 会导致过早触发。正确做法：**先处理完所有音频，再统一检查 endpoint**。
   - `SherpaASRImpl.kt` `recognizeStreaming()`

2. **AudioPlayer** - 必须用 `ENCODING_PCM_16BIT`，SherpaTTS 输出需手动转 ShortArray

3. **TTS 合成** - 花燕模型 (60MB) 优于 vits-melo-tts-zh_en (163MB)

4. **Jellyfin 播放**
   - PlaybackInfo 400: 不发送 DeviceProfile
   - WMA/ASF 不支持: 用 `?Container=mp4&AudioCodec=aac`
   - mediaSourceId 需去 dashes: `itemId.replace("-", "")`

5. **Jellyfin 接口验证** - 用 playwright MCP，地址 `192.168.31.206:8096` (账号: app)

---

## 相关文档

| 文档 | 路径 |
|------|------|
| 项目设计 | `VOICE_ASSISTANT_DESIGN.md` |
| Android 架构 | `ARCHITECTURE_ANDROID.md` |
| 语音管道 | `ARCHITECTURE_VOICE_PIPELINE.md` |
| 快速开始 | `android/QUICKSTART.md` |
| 小爱风格 | `XIAOAI_DESIGN.md` |

always replay with Chinese
