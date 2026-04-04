# 项目状态总结

## 更新记录

### 2026-04-04: 文档与构建基线同步 ✅
- 确认 `:app:assembleDebug` 构建成功
- 确认 `:core:testDebugUnitTest` 通过
- 同步文档中的 SDK 与模型说明（移除 `downloadModels` 旧描述）

### 2026-04-04: 默认敏感配置清理 ✅
- 清理默认 LLM Base URL（改为空）
- 清理默认 LLM API Key（改为空）
- 默认模型改为通用值 `deepseek-chat`

### 2026-04-04: 前台服务启动入口收敛 ✅
- 移除 `VoiceAssistantApp` 中的服务自动拉起
- 保留 `MainActivity` 作为服务启动入口
- 增加“服务已运行则跳过重复启动”保护逻辑

### 2026-04-04: VoicePipeline 启动幂等优化 ✅
- `VoicePipeline.start()` 增加重复调用保护
- 初始化失败/超时场景下可恢复为可重试状态

## 当前状态

| 功能 | 状态 | 说明 |
|------|------|------|
| 应用启动 | ✅ 正常 | BUILD SUCCESSFUL |
| UI 界面 | ✅ 正常 | - |
| KWS 唤醒 | ✅ 正常 | sherpa-onnx-kws-zipformer |
| VAD 端点检测 | ✅ 正常 | Silero VAD + 兜底能量阈值 |
| ASR 语音识别 | ✅ 正常 | streaming zipformer zh int8 |
| TTS 语音合成 | ✅ 正常 | vits-piper-zh_CN-huayan-medium |
| Jellyfin 音乐 | ✅ 正常 | 搜索/浏览/播放链路可用 |
| DLNA 控制 | ⚠️ 联调中 | 需实机设备验证稳定性 |

## 下一步
1. 补齐 `domain`/`data` 模块测试覆盖
2. 完成 DLNA 多设备稳定性联调
3. 继续完善异常提示与可观测性日志
