# 项目进度报告

## 当前版本
**版本**: 1.3
**日期**: 2026-03-22
**minSdk**: 23 (Android 6.0)
**targetSdk**: 34
**Sherpa-ONNX**: v1.12.32 (最新)

---

## 已完成模块

### 1. app 模块 ✅
- VoiceAssistantApp (Application)
- VoiceAssistantService (前台服务)
- MainActivity + 布局
- ModelDownloadActivity (模型初始化)
- AndroidManifest (权限 + 服务声明)
- AppModule (Hilt 依赖注入)
- ModelInitializer (从 assets 复制模型)
- ModelInfo / ModelType / ModelStatus

### 2. core 模块 ✅
- VoicePipeline (状态机 + AudioPlayer 集成)
- PipelineState (6 种状态)
- AudioCapture (API 23/26 兼容)
- AudioPlayer (TTS 音频播放) ⭐ 新增
- SherpaKWS / SherpaASR / SherpaVAD / SherpaTTS (完整实现)
- IntentRouter (支持 Music/LLM) ⭐ 更新

### 3. data 模块 ✅
- AppDatabase (Room)
- ConfigEntity + ConfigDao
- SettingsRepositoryImpl
- NavidromeApi (Retrofit)
- LLMApi (DeepSeek API) ⭐ 新增
- MusicRepositoryImpl ⭐ 新增
- LLMRepositoryImpl ⭐ 新增

### 4. domain 模块 ✅
- ConfigModels
- StartVoicePipelineUseCase
- MusicRepository 接口 ⭐ 新增
- LLMRepository 接口 ⭐ 新增

---

## 项目结构
```
android/
├── app/          # UI + Service + 模型初始化
├── core/         # 语音管道核心
├── data/         # 数据层
├── domain/       # 领域层
└── build.gradle  # 根构建
```

---

## 本次更新 (v1.2)

### 新增功能 ✅

1. **AudioPlayer** - TTS 音频播放
   - 文件: `core/audio/AudioPlayer.kt`
   - 使用 AudioTrack 流式播放 FloatArray 音频
   - 支持暂停/停止

2. **Silero VAD** - 端点检测
   - 文件: `core/sherpa/SherpaVADImpl.kt`
   - 优先尝试加载 Silero VAD 模型
   - 降级到能量阈值检测（备选方案）

3. **MusicRepository** - Navidrome 音乐服务
   - 文件: `data/repository/Repositories.kt`
   - 搜索歌曲
   - 获取播放 URL

4. **LLMRepository** - 智能对话
   - 文件: `data/repository/Repositories.kt`
   - 接入 DeepSeek API
   - 支持系统提示词

5. **IntentRouter 更新** - 实际执行功能
   - 调用 Navidrome API 搜索歌曲
   - 调用 LLM API 进行对话
   - 支持 MUSIC/QUERY/CHAT 意图处理

---

## 当前状态

| 功能 | 状态 | 说明 |
|------|------|------|
| 模型初始化 | ✅ 完成 | 从 assets 复制，无需下载 |
| 语音管道 | ✅ 完成 | 6 状态流转正常 |
| 唤醒词检测 | ✅ 完成 | 8 个唤醒词可用 |
| 语音识别 | ✅ 完成 | Sherpa ASR 集成 |
| 语音合成 | ✅ 完成 | Sherpa TTS 集成 |
| TTS 播放 | ✅ 完成 | AudioPlayer 流式播放 |
| VAD 端点检测 | ✅ 完成 | Silero VAD + 能量阈值 |
| 意图路由 | ✅ 完成 | 音乐搜索 + LLM 对话 |
| Navidrome 音乐 | ✅ 完成 | 搜索歌曲获取 URL |
| LLM 对话 | ⚠️ 待配置 | 需要 API 密钥 |
| DLNA 控制 | ⏸️ 待实现 | 需要 DLNA 库 |

---

## 技术栈
- Kotlin 1.9.20
- Hilt 2.50 (DI)
- Room 2.6 (DB)
- Retrofit 2.9 (HTTP)
- Sherpa-ONNX (语音识别/合成)
- ConstraintLayout (UI)

---

## 下一步计划
1. 配置 LLM API 密钥
2. 实现 DLNA 设备控制
3. 测试完整语音交互流程