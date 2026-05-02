# 贡献指南

感谢您对 Voice Assistant 项目的关注！本文档将帮助您了解如何为项目做出贡献。

## 开发环境设置

### 环境要求

- **Android Studio** Hedgehog (2024.1) 或更高版本
- **JDK** 17
- **Android SDK** API 35+
- **Gradle** 8.4+

### 构建步骤

```bash
cd android

# 调试版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 运行单元测试
./gradlew :core:testDebugUnitTest
```

### 项目结构

```
voice-assistant/
├── app/              # UI 层 + 前台服务 + 模型初始化
├── core/             # 语音管道核心 (KWS/VAD/ASR/TTS)
├── data/             # 数据层 (Room/Retrofit/Jellyfin)
├── domain/           # 领域层 (UseCases/Repository接口)
└── sherpa-onnx-aar/ # Sherpa-ONNX 本地库
```

## 代码规范

### 基础规则

1. **不许无提示崩溃** — 错误必须可理解，禁止静默失败
2. **外部输入必须校验** — 用户输入、文件、网络数据、API 返回值
3. **严格分层** — 禁止跨层乱写（ui → domain → data）
4. **单一职责** — 一个函数只做一件事，命名清晰
5. **输出前自检** — 验证逻辑正确性
6. **代码修改完需要编译检查是否通过**

### 错误处理

```kotlin
// ✅ 正确：明确错误类型和处理
fun parse(text: String): Result<Data> {
    if (text.isBlank()) return Result.failure(IllegalArgumentException("输入不能为空"))
    return try {
        Result.success(doParse(text))
    } catch (e: SpecificException) {
        Result.failure(e)
    }
}

// ❌ 错误：静默失败或泛泛而谈
fun parse(text: String): Data? {
    if (text.isBlank()) return null
    return try { doParse(text) } catch (e: Exception) { null }
}
```

### 全面屏 (Edge-to-Edge) 适配

新建 Activity 或修改布局文件时：

1. **禁止使用** `android:fitsSystemWindows="true"`
2. **禁止硬编码**状态栏/导航栏高度，使用 WindowInsets 动态计算
3. 根布局添加 id（`rootLayout` 或 `topBar`）

参考页面：`MainActivity`、`SettingsActivity`、`JellyfinBrowseActivity`、`PlaylistActivity`

### 提交前检查

- [ ] 代码编译通过 `./gradlew assembleDebug`
- [ ] 单元测试通过 `./gradlew :core:testDebugUnitTest`
- [ ] 无 hardcoded 敏感信息
- [ ] 新增功能已更新相关文档

## 提交规范

### Commit Message 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| Type | 说明 |
|------|------|
| feat | 新功能 |
| fix | 错误修复 |
| docs | 文档更新 |
| style | 代码格式（不影响功能） |
| refactor | 重构 |
| perf | 性能优化 |
| test | 测试相关 |
| chore | 构建/工具相关 |

### Scope 范围

- `ui` - UI 层
- `core` - 核心语音管道
- `data` - 数据层
- `domain` - 领域层
- `music` - 音乐播放功能
- `jellyfin` - Jellyfin 集成
- `tts` - TTS 相关
- `asr` - ASR 相关

### 示例

```
feat(music): 添加 DLNA 播放状态时长显示

- 修复 DLNA 模式下进度条不更新的问题
- 优化播放状态的回调处理

Closes #123
```

## PR 流程

### 分支命名

```
feature/<feature-name>   # 新功能
fix/<issue-name>         # 错误修复
docs/<doc-name>          # 文档更新
refactor/<scope>         # 重构
```

### PR 检查清单

- [ ] 分支基于 `main` 或 `develop` 创建
- [ ] Commit Message 符合规范
- [ ] 代码通过编译和测试
- [ ] 更新了相关文档（如有需要）
- [ ] PR 描述清晰说明了改动原因

### PR 描述模板

```markdown
## 改动说明

<简要说明改动内容和动机>

## 改动类型

- [ ] 新功能
- [ ] 错误修复
- [ ] 重构
- [ ] 文档更新

## 测试方式

<描述如何测试这个改动>

## 关联 Issue

Closes #<issue-number>
```

## 问题反馈

### Bug 反馈

请在 GitHub Issues 中提交，包含以下信息：

1. **复现步骤** — 清晰的步骤说明
2. **预期行为** — 应该发生什么
3. **实际行为** — 实际发生了什么
4. **环境信息** — Android 版本、设备型号等
5. **日志** — 相关 Logcat 日志

### 功能建议

欢迎提交 Feature Request，请说明：

1. **使用场景** — 这个功能解决什么问题
2. **预期效果** — 功能的预期行为
3. **可行性** — 初步判断是否可行

## 文档更新

代码改动后必须同步更新相关文档：

| 改动类型 | 更新文档 |
|----------|----------|
| 功能逻辑 | `VOICE_ASSISTANT_DESIGN.md` / `ARCHITECTURE_ANDROID.md` |
| 模块结构 | `ARCHITECTURE_ANDROID.md` |
| 语音管道 | `ARCHITECTURE_VOICE_PIPELINE.md` |
| 新增 API | 对应模块的 README |

### 文档要求

- **结构清晰** — Markdown 格式，便于快速扫读
- **表达简洁** — 偏总结而非过程复述
- **最小最新** — 保持文档最小化，只记录当前状态

### 禁止事项

- 不要复制代码注释到文档
- 不要记录已经被废弃的逻辑
- 不要创建无人维护的文档

## 架构参考

### 语音管道状态机

```
INITIALIZING → IDLE → WAKEWORD_DETECTED → LISTENING → RECORDING →
RECOGNIZING → THINKING → SPEAKING → IDLE
```

### 关键组件路径

| 组件 | 路径 |
|------|------|
| 模型目录 | `android/app/src/main/assets/` |
| 主服务 | `VoiceAssistantService.kt` |
| 管道控制器 | `VoicePipeline.kt` |
| 音频捕获 | `AudioCapture.kt` |
| Sherpa ASR | `core/src/main/java/.../sherpa/SherpaASRImpl.kt` |
| Jellyfin | `data/src/main/java/.../remote/JellyfinClient.kt` |

详见 `ARCHITECTURE_VOICE_PIPELINE.md`
