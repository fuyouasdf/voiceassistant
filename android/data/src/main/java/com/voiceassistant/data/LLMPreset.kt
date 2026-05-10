package com.voiceassistant.data

/**
 * LLM 预设配置数据类
 *
 * @param name 显示名称，如 "DeepSeek"
 * @param baseUrl Base URL
 * @param apiPath API 路径
 * @param defaultModel 默认模型
 */
data class LLMPreset(
    val name: String,
    val baseUrl: String,
    val apiPath: String,
    val defaultModel: String
) {
    companion object {
        /**
         * 预定义的 LLM 预设列表
         *
         * 分组: 国际 | 国内 | 自部署
         */
        val PRESETS = listOf(
            // === 自定义 ===
            LLMPreset(
                name = "自定义",
                baseUrl = "",
                apiPath = "/v1/chat/completions",
                defaultModel = ""
            ),

            // === 国际主流 ===
            LLMPreset(
                name = "OpenAI",
                baseUrl = "https://api.openai.com",
                apiPath = "/v1/chat/completions",
                defaultModel = "gpt-4o-mini"
            ),
            LLMPreset(
                name = "Anthropic",
                baseUrl = "https://api.anthropic.com",
                apiPath = "/v1/messages",
                defaultModel = "claude-3-5-haiku-20241022"
            ),
            LLMPreset(
                name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                apiPath = "/chat/completions",
                defaultModel = "llama-3.1-70b-versatile"
            ),
            LLMPreset(
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                apiPath = "/models",
                defaultModel = "gemini-1.5-flash"
            ),

            // === 国内主流 ===
            LLMPreset(
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                apiPath = "/v1/chat/completions",
                defaultModel = "deepseek-chat"
            ),
            LLMPreset(
                name = "MiniMax",
                baseUrl = "https://api.minimaxi.com",
                apiPath = "/v1/text/chatcompletion_v2",
                defaultModel = "MiniMax-Text-01"
            ),
            LLMPreset(
                name = "智谱 GLM",
                baseUrl = "https://open.bigmodel.cn",
                apiPath = "/api/agent-runtime/v1/chat/completions",
                defaultModel = "glm-4-flash"
            ),
            LLMPreset(
                name = "阿里通义",
                baseUrl = "https://dashscope.aliyuncs.com",
                apiPath = "/compatible-mode/v1/chat/completions",
                defaultModel = "qwen-plus"
            ),
            LLMPreset(
                name = "腾讯混元",
                baseUrl = "https://hunyuan.cloud.tencent.com",
                apiPath = "/v1/chat/completions",
                defaultModel = "hunyuan-pro"
            ),
            LLMPreset(
                name = "字节豆包",
                baseUrl = "https://ark.cn-beijing.volces.com",
                apiPath = "/api/v3/chat/completions",
                defaultModel = "doubao-pro-32k"
            ),

            // === 自部署 ===
            LLMPreset(
                name = "Ollama (本地)",
                baseUrl = "http://localhost:11434",
                apiPath = "/v1/chat/completions",
                defaultModel = "llama3.2"
            )
        )
    }
}