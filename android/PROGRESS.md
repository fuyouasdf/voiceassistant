# 项目进度报告

## 当前版本
**版本**: 1.4  
**日期**: 2026-04-04  
**minSdk**: 26 (Android 8.0)  
**targetSdk**: 35  
**compileSdk**: 36  
**Sherpa-ONNX**: 本地模块集成（`sherpa-onnx-aar:sherpa_onnx`）

---

## 构建与测试基线（2026-04-04）

| 检查项 | 结果 | 备注 |
|------|------|------|
| `:app:assembleDebug` | ✅ 通过 | 调试包可编译 |
| `:core:testDebugUnitTest` | ✅ 通过 | 现有 core 单测通过 |
| `:domain:testDebugUnitTest` | ✅ 通过 | 当前为 `NO-SOURCE` |

---

## 已完成模块

### 1. app 模块 ✅
- `VoiceAssistantApp`（应用初始化）
- `VoiceAssistantService`（前台服务）
- `MainActivity`（主界面与状态展示）
- `SettingsActivity`（Jellyfin/LLM/语音参数配置）
- `AppModule`（Hilt 依赖注入）

### 2. core 模块 ✅
- `VoicePipeline`（状态机 + 管线编排）
- `PipelineState`（含 `INITIALIZING` 与 `WAKEWORD_DETECTED`）
- `AudioCapture`（16kHz、mono、PCM_FLOAT）
- `AudioPlayer`（FloatArray -> PCM_16BIT 播放）
- Sherpa KWS / VAD / ASR / TTS 实现
- `IntentRouter`（音乐、问答、闲聊、音量、设备意图）

### 3. data 模块 ✅
- `AppDatabase`（Room，版本 3）
- `SettingsRepositoryImpl`（配置持久化）
- `PlaylistRepositoryImpl`（播放列表 JSON 持久化）
- `JellyfinClient`（检索、流地址、播放上报）
- `LLMApi` + `LLMRepositoryImpl`

### 4. domain 模块 ✅
- 领域模型（Intent / Song / Playlist）
- Repository 接口定义
- UseCase 集合（聊天、音乐、设备、音量等）

---

## 当前状态

| 功能 | 状态 | 说明 |
|------|------|------|
| 模型初始化 | ✅ 完成 | 模型随仓库 assets 提供 |
| 语音管道状态流转 | ✅ 完成 | 初始化/唤醒/录音/识别/播报 |
| 唤醒词检测 | ✅ 完成 | 支持动态唤醒词与灵敏度 |
| 语音识别 | ✅ 完成 | Sherpa 流式 ASR |
| 语音合成 | ✅ 完成 | Sherpa TTS + PCM16 播放 |
| 意图路由 | ✅ 完成 | 音乐与 LLM 问答链路打通 |
| Jellyfin 控制 | ✅ 完成 | 搜索、流地址、会话上报 |
| DLNA 播放 | ⚠️ 进行中 | 依赖设备环境验证 |
| 自动化测试覆盖 | ⚠️ 不足 | 仅 core 有少量单测 |

---

## 近期重点

1. 提升测试覆盖（`data` 与 `app` 关键路径）
2. 继续验证 DLNA 多设备兼容性
3. 提升配置异常场景的用户提示质量
