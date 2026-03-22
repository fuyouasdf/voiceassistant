# CLAUDE.md - Voice Assistant Project

> **渐进式加载文档结构**：本文件为入口，详细信息见下方链接文档

---

## 快速参考

| 主题 | 文档 |
|------|------|
| 项目概述 | [VOICE_ASSISTANT_DESIGN.md](VOICE_ASSISTANT_DESIGN.md) |
| Android 架构 | [ARCHITECTURE_ANDROID.md](ARCHITECTURE_ANDROID.md) |
| 语音管道 | [ARCHITECTURE_VOICE_PIPELINE.md](ARCHITECTURE_VOICE_PIPELINE.md) |
| 实现计划 | [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) |
| 快速开始 | [android/QUICKSTART.md](android/QUICKSTART.md) |
| 进度追踪 | [android/PROGRESS.md](android/PROGRESS.md) |

---

## 核心信息（必读）

### 构建命令
```bash
cd android
./gradlew build          # 完整构建
./gradlew assembleDebug  # 调试版
./gradlew installDebug   # 安装到设备
```

### 项目结构
```
voice-assistant/
├── app/     # UI 层
├── core/    # 语音管道核心
├── data/    # 数据层
└── domain/  # 领域层
```

### 关键路径
- 模型目录：`android/app/src/main/assets/models/`
- 主服务：`VoiceAssistantService.kt`
- 管道控制器：`VoicePipeline.kt`
- 音频捕获：`AudioCapture.kt`
- 模型初始化：`ModelInitializer.kt`

---

## 重要规则
planmode只设计不修改
### 代码修改
- 所有文件路径使用 **绝对 Windows 路径**（如 `C:\Users\qweqwe\...`）
- 语音处理在前台服务中运行
- 音频捕获：16kHz, mono, PCM_FLOAT

### 文档同步规则
> **每次修改代码后，必须同步更新相关文档**

| 修改类型 | 更新文档 |
|----------|----------|
| 功能逻辑 | `ARCHITECTURE_ANDROID.md` 或 `VOICE_ASSISTANT_DESIGN.md` |
| 模块结构 | `ARCHITECTURE_ANDROID.md` |
| 语音管道 | `ARCHITECTURE_VOICE_PIPELINE.md` |
| 新增 API/接口 | 对应模块的 README |

---

## 语音管道状态机

```
IDLE → LISTENING → RECORDING → RECOGNIZING → THINKING → SPEAKING → IDLE
```

**核心组件**：
- `VoicePipeline` - 状态机控制器
- `AudioCapture` - 麦克风输入
- Sherpa-ONNX: KWS → VAD → ASR → TTS
- `IntentRouter` - 意图路由

---

*详细架构和设计文档请查阅上方链接文件*
