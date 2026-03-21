# 模型文件

## 需要下载的模型

### 1. KWS (唤醒词检测)
- 文件名: `sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2`
- 大小: 3.3MB
- 下载: https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/

### 2. ASR (语音识别)
- 文件名: `sherpa-onnx-paraformer-zh-2024-03-09.tar.bz2`
- 大小: 100MB+
- 下载: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/

### 3. TTS (语音合成)
- 文件名: `piper-zh_CN-huayan-medium.tar.bz2`
- 大小: 120MB
- 下载: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/

## 放置位置
```
assets/models/
├── kws/
│   └── model.onnx
├── asr/
│   └── model.onnx
└── tts/
    ├── model.onnx
    └── config.json
```

## 首次运行
应用会自动解压模型到私有目录
