# 语音助手 - 快速开始

## 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- 测试设备: Android 6.0+ (API 23)

## 前提条件

### 1. 构建 Sherpa-ONNX AAR（首次）
需要编译本地 AAR 模块：

```bash
cd android
./gradlew :sherpa-onnx-aar:sherpa_onnx:assembleRelease
```

> **注意**: 需要 NDK 和 CMake，请确保 Android SDK 中已安装。

### 2. 下载模型文件
通过 Gradle 任务自动下载（约 298 MB）：

```bash
cd android
./gradlew :app:downloadModels
```

该任务会下载以下模型到 `app/src/main/assets/`：

| 模型 | 用途 | 大小 |
|------|------|------|
| sherpa-onnx-kws-zipformer-wenetspeech-3.3M | 关键词唤醒 | ~35 MB |
| sherpa-onnx-streaming-zipformer-bilingual-zh-en | 语音识别 | ~200 MB |
| silero_vad.onnx | 语音活动检测 | ~2 MB |
| vits-piper-zh_CN-huayan-medium | 语音合成 | ~61 MB |

## 构建步骤

### 1. 下载模型
```bash
cd android
./gradlew :app:downloadModels
```

### 2. 打开项目
- Android Studio -> Open -> 选择 `android/` 目录
- 等待 Gradle Sync 完成

### 3. 配置服务
首次运行后，在应用中配置：
- 点击主界面右上角「设置」按钮
- 配置 **音乐服务** (Navidrome 地址、用户名、密码)
- 配置 **AI 服务** (LLM API 地址、Key、模型)

配置保存在本地数据库，重启应用后自动加载。

### 4. 运行
- 连接 Android 设备
- Run -> Run 'app'

## 测试流程

1. **首次启动** - 模型已在构建前通过 `downloadModels` 任务下载完毕
2. **主界面** - 显示待机状态
3. **语音唤醒** - 喊唤醒词或点击手动触发
4. **播放音乐** - 说 "播放周杰伦的 Mine Mine"
5. **DLNA 推送** - 自动推送到已配置的音箱

## 项目结构
```
android/
├── app/              # UI + Service（含 downloadModels 任务）
├── core/             # 语音管道 + DLNA
├── data/             # 数据层
├── domain/           # 领域层
└── sherpa-onnx-aar/  # Sherpa-ONNX 本地 AAR 模块
```

## 已知问题
- 模型文件较大，首次下载需要时间
- DLNA 设备需要手动配置
- LLM 需要网络连接
