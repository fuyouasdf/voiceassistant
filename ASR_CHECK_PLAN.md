# ASR 集成检查与修复计划

## 目标
验证 voice-assistant 项目的 ASR 集成是否正确，定位并修复问题。

---

## 步骤 1：检查实际模型文件
**执行者**：subagent
**命令**：
```bash
ls -la C:/Users/qweqwe/AndroidStudioProjects/voice-assistant/android/app/src/main/assets/
ls -la C:/Users/qweqwe/AndroidStudioProjects/voice-assistant/android/app/src/main/assets/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/ 2>/dev/null || echo "目录不存在"
ls -la C:/Users/qweqwe/AndroidStudioProjects/voice-assistant/android/app/src/main/assets/silero_vad.onnx 2>/dev/null || echo "VAD文件不存在"
```

**预期**：获取实际存在的模型文件名列表

---

## 步骤 2：检查 sherpa-onnx 官方 SherpaOnnx 项目的模型文件
**执行者**：subagent
**命令**：
```bash
ls -la C:/Users/qweqwe/AndroidStudioProjects/sherpa-onnx-master/android/SherpaOnnx/app/src/main/assets/ 2>/dev/null | head -20
find C:/Users/qweqwe/AndroidStudioProjects/sherpa-onnx-master/android/SherpaOnnx/app/src/main/assets -name "*.onnx" 2>/dev/null | head -20
```

**预期**：获取官方项目使用的模型文件列表

---

## 步骤 3：对比代码配置与实际文件
**执行者**：subagent
**读取文件**：
- `C:\Users\qweqwe\AndroidStudioProjects\voice-assistant\android\core\src\main\java\com\voiceassistant\core\sherpa\SherpaASRImpl.kt`
- `C:\Users\qweqwe\AndroidStudioProjects\voice-assistant\android\core\src\main\java\com\voiceassistant\core\sherpa\SherpaVADImpl.kt`

**对比点**：
1. SherpaASRImpl.kt 中 encoder/decoder/joiner 路径是否与实际文件匹配
2. VAD 模型路径是否正确
3. modelType 是否正确（zipformer vs paraformer）

**输出**：问题清单

---

## 步骤 4：生成修复方案
**执行者**：subagent
基于步骤 1-3 的结果，如果发现问题，生成具体的代码修改建议。

---

## 步骤 5：执行修复（如需要）
**执行者**：subagent
根据步骤 4 的方案，修改 SherpaASRImpl.kt 和 SherpaVADImpl.kt 中的路径配置。

---

## 成功标准
- 模型文件路径与代码配置一致
- VAD 配置正确
- 如果需要修改，完成代码修改并验证语法正确
