# Bug Fix Record

> 记录已修复的 bug，防止重复踩坑

---

## 2026-03-22: TTS SIGSEGV 崩溃

### 现象
- LLM 返回响应后，状态从 THINKING → SPEAKING 时发生 SIGSEGV (段错误)
- 日志显示 `State transition: THINKING -> SPEAKING` 后立即崩溃

### 根本原因
Sherpa-ONNX TTS `OfflineTtsConfig` 中 `dataDir` 配置错误：

sherpa-onnx 在 `dataDir` 根目录查找 `phonindex`、`phontab` 等文件，但实际这些文件位于 `phontab/` 子目录中。

日志警告：
```
'/data/user/0/.../phonindex' does not exist. Please check --vits-data-dir
Errors found in config!
```

### 修复
`SherpaTTSImpl.kt:25`:
```kotlin
// 修改前 - 指向模型根目录
dataDir = modelDir.absolutePath

// 修改后 - 指向 phontab 子目录
dataDir = File(modelDir, "phontab").absolutePath
```

### 经验教训
1. native 库的警告（如 `does not exist`）不可忽视，很可能后续触发 SIGSEGV
2. Sherpa-ONNX TTS 模型文件结构与 ASR/KWS 不同，`dataDir` 必须指向包含 `phonindex` 等文件的目录
3. 测试 TTS 必须实际调用 `generate()` 触发完整流程，不能只测初始化

---

## 2026-03-22: TTS 模型路径错误

### 现象
- TTS 初始化后立即崩溃或无法合成音频

### 根本原因
`SherpaTTSImpl.kt` 中模型路径配置错误：
- 代码调用 `copyModelsFromAssets("models/tts")`
- 但 assets 中不存在 `models/tts` 目录
- TTS 模型实际位于 `vits-piper-zh_CN-huayan-medium/`

### 修复
```kotlin
// 修改前
val modelDir = copyModelsFromAssets("models/tts")

// 修改后
val modelDir = copyModelsFromAssets("vits-piper-zh_CN-huayan-medium")
```

### 经验教训
1. 配置路径前先确认 assets 目录实际结构
2. 不同模块（ASR/KWS/TTS）的模型目录结构可能不同，不能假设一致
