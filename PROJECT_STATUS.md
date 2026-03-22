# 项目状态总结

## 更新记录

### 2026-03-22: Sherpa-ONNX 更新到 v1.12.32 ✅
- 下载了最新的 AAR 文件
- 更新 `app/libs/sherpa-onnx-android.aar`
- 更新 `core/libs/sherpa-onnx-android.aar`
- 构建验证成功

## 当前状态

| 功能 | 状态 | 说明 |
|------|------|------|
| 应用启动 | ✅ 正常 | BUILD SUCCESSFUL |
| UI 界面 | ✅ 正常 | - |
| KWS 唤醒 | ✅ 正常 | sherpa-onnx-kws-zipformer |
| VAD 端点检测 | ✅ 正常 | Silero VAD |
| ASR 语音识别 | ⚠️ 待测试 | 需要验证元数据问题 |
| TTS 语音合成 | ⚠️ 待测试 | vits-piper 模型 |

## ASR 元数据问题 (待解决)

**现象**: 日志警告
```
'window_size' does not exist in the metadata
'encoder_output_size' does not exist in the metadata
```

**分析**:
- 代码使用 `OnlineTransducerModelConfig` + `modelType = "zipformer"`
- 模型文件: `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`
- 错误中的字段 (`window_size`, `encoder_output_size`) 是 **Paraformer** 模型专用的
- 可能原因: 模型文件元数据不完整，或库版本验证变化

**建议解决方案**:
1. 重新下载官方模型文件
2. 或使用 Paraformer 模型替代

## 下一步
1. 安装应用到设备测试
2. 验证 ASR 识别是否正常工作
3. 如仍有问题，重新下载 ASR 模型文件