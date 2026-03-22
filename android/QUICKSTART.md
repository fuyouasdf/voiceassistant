# 语音助手 - 快速开始

## 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- 测试设备: Android 6.0+ (API 23)

## 前提条件

### 1. 下载 Sherpa-ONNX 库
本项目使用 Sherpa-ONNX 作为语音识别/合成引擎，需要下载 AAR 文件：

**下载页面**: https://github.com/k2-fsa/sherpa-onnx/releases
**当前版本**: v1.12.32 (2026-03-22)

**步骤**：
1. 打开上方链接，找到最新版本的 Android AAR 文件
2. 下载 `sherpa-onnx-android-aar-{version}.aar`
3. 重命名为 `sherpa-onnx-android.aar`
4. 复制到以下位置：
   - `app/libs/sherpa-onnx-android.aar`
   - `core/libs/sherpa-onnx-android.aar`

### 2. 下载模型文件（可选）
如需本地模型，请运行：
```bash
cd android
./scripts/download_models.sh
```

## 构建步骤

### 1. 下载模型文件
```bash
cd android
./scripts/download_models.sh
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

1. **首次启动** - 自动下载模型（约 230MB）
2. **主界面** - 显示待机状态
3. **语音唤醒** - 喊唤醒词或点击手动触发
4. **播放音乐** - 说 "播放周杰伦的 Mine Mine"
5. **DLNA 推送** - 自动推送到已配置的音箱

## 项目结构
```
android/
├── app/          # UI + Service
├── core/         # 语音管道 + DLNA
├── data/         # 数据层
├── domain/       # 领域层
└── scripts/      # 工具脚本
```

## 已知问题
- 模型文件较大，首次下载需要时间
- DLNA 设备需要手动配置
- LLM 需要网络连接
