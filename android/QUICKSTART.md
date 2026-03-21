# 语音助手 - 快速开始

## 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- 测试设备: Android 6.0+ (API 23)

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
