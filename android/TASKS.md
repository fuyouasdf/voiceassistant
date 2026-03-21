# 语音助手项目任务清单

## 当前进度
- [x] app 模块 - UI + Service 框架
- [x] core 模块 - 语音管道 + 意图路由
- [ ] data 模块 - Repository + 数据库
- [ ] domain 模块 - UseCase
- [ ] 模型下载 - Sherpa-ONNX 模型
- [ ] 集成测试 - 端到端验证

## 执行任务

### 任务1: data 模块 (30分钟)
- 创建 data/build.gradle.kts
- 实现 SettingsRepository (Room)
- 实现 MusicRepository (Navidrome API)
- 实现 LLMRepository (DeepSeek API)

### 任务2: domain 模块 (20分钟)
- 创建 domain/build.gradle.kts
- 定义 Repository 接口
- 实现 UseCase

### 任务3: 依赖注入 (20分钟)
- 创建 AppModule (Hilt)
- 绑定 Repository 实现
- 绑定 core 组件

### 任务4: UI 实现 (30分钟)
- MainActivity 布局
- SettingsFragment
- 状态可视化

### 任务5: 模型准备 (40分钟)
- 下载 Sherpa-ONNX 模型
- 集成到 assets
- 首次运行解压

### 任务6: 集成测试 (30分钟)
- 验证状态机流转
- 测试意图路由
- 检查内存/电量

## 预计总时间: 2.5小时
