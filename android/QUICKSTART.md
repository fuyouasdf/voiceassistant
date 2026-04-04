# 快速开始

> 本文档为 Android 快速构建指南。项目概述和架构详见根目录 [README.md](../README.md)。

## 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 36（compileSdk）
- 目标系统：Android 8.0+（minSdk 26）

## 前提说明

### 1. 模型文件
当前仓库已包含模型文件（`app/src/main/assets/`），不需要额外执行 `:app:downloadModels`。

### 2. Sherpa-ONNX 模块
当前项目默认直接依赖本地模块 `:sherpa-onnx-aar:sherpa_onnx`。  
如需更新底层 AAR 或 JNI，可单独执行：

```bash
cd android
./gradlew :sherpa-onnx-aar:sherpa_onnx:assembleRelease
```

## 构建步骤

### 1. 打开项目
- Android Studio -> Open -> 选择 `android/` 目录
- 等待 Gradle Sync 完成

### 2. 编译调试包
```bash
cd android
./gradlew :app:assembleDebug
```

### 3. 配置服务
首次运行后，在应用中配置：
- 点击主界面右上角「设置」按钮
- 配置 **Jellyfin 服务**（地址、API Key）
- 配置 **LLM 服务**（Base URL、API Key、模型）

配置保存在本地数据库，保存后立即生效。

### 4. 安装到设备（可选）
```bash
cd android
./gradlew :app:installDebug
```

## 测试流程

1. 首次启动应用，确认主界面状态显示正常
2. 在设置页测试 Jellyfin 与 LLM 连通性
3. 语音唤醒或手动触发录音
4. 执行一句完整指令（例如“播放周杰伦的 Mine Mine”）
5. 观察状态流转：`INITIALIZING → IDLE → ... → SPEAKING`

## 常用命令

```bash
cd android

# 构建
./gradlew :app:assembleDebug

# 单元测试
./gradlew :core:testDebugUnitTest
./gradlew :domain:testDebugUnitTest
```

## 项目结构

```text
android/
├── app/              # UI + Service + 模型初始化
├── core/             # 语音管道 + DLNA
├── data/             # 数据层
├── domain/           # 领域层
└── sherpa-onnx-aar/  # Sherpa-ONNX 本地 AAR 模块
```

## 已知说明
- `:domain:testDebugUnitTest` 当前为 `NO-SOURCE`（暂无单测文件）
- LLM 功能需要可用的 API 配置
- DLNA 设备需要在局域网中可达
