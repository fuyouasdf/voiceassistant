/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.data.repository

import com.google.gson.Gson
import com.voiceassistant.data.remote.ErrorResponse
import com.voiceassistant.data.remote.LLMApi
import com.voiceassistant.data.remote.LLMRequest
import com.voiceassistant.data.remote.LLMResponse
import com.voiceassistant.data.remote.MessageItem
import com.voiceassistant.domain.repository.LLMParsedIntent
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.LLMRouteDecision
import com.voiceassistant.domain.repository.LLMRouteMode
import com.voiceassistant.domain.repository.ModelNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

/**
 * Implementation of LLMRepository
 * Connects to DeepSeek or similar LLM API
 * Reads config from SettingsRepository at runtime
 */
class LLMRepositoryImpl(
    private val api: LLMApi,
    private val settingsRepository: SettingsRepository,
    private val httpClient: OkHttpClient,
    private val messageRepository: com.voiceassistant.domain.repository.MessageRepository,
    private val maxContextCount: Int = 5
) : LLMRepository {

    override fun chatStream(message: String): Flow<String> = callbackFlow {
        val model = settingsRepository.getLLMModel()
        val systemPrompt = settingsRepository.getLLMSystemPrompt()
        val baseUrl = settingsRepository.getLLMBaseUrl().ifEmpty { "http://localhost:1234" }
        val apiKey = settingsRepository.getLLMApiKey()
        val apiPath = settingsRepository.getLLMApiPath()

        val contextHistory = buildContextString()

        // MiniMax 等 OpenAI 兼容 API 使用 messages 格式
        val isOpenAIFormat = baseUrl.contains("minimaxi", ignoreCase = true) ||
            apiPath.contains("chatcompletion", ignoreCase = true)

        val requestBody = if (isOpenAIFormat) {
            val messages = mutableListOf<MessageItem>()
            if (systemPrompt.isNotBlank()) {
                messages.add(MessageItem(role = "system", content = systemPrompt))
            }
            messages.addAll(buildMessagesFromHistory(contextHistory))
            messages.add(MessageItem(role = "user", content = message))
            Gson().toJson(
                LLMRequest(
                    model = model,
                    messages = messages,
                    temperature = 0.7,
                    max_tokens = 1024,
                    stream = true
                )
            )
        } else {
            Gson().toJson(
                LLMRequest(
                    model = model,
                    input = buildString {
                        if (systemPrompt.isNotBlank()) {
                            append("System: $systemPrompt\n")
                        }
                        if (contextHistory.isNotBlank()) {
                            append("$contextHistory\n")
                        }
                        append("User: $message")
                    },
                    temperature = 0.7,
                    max_tokens = 1024,
                    stream = true
                )
            )
        }

        val request = Request.Builder()
            .url("$baseUrl$apiPath")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .apply {
                if (apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }
            .build()

        Timber.d("LLM request URL: $baseUrl$apiPath")
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        close(IOException("HTTP ${it.code}: ${it.message}"))
                        return
                    }

                    it.body?.let { body ->
                        body.source().let { source ->
                            while (true) {
                                val line = source.readUtf8Line() ?: break

                                // SSE data line format: data: {...}
                                if (line.startsWith("data: ")) {
                                    val data = line.removePrefix("data: ").trim()
                                    if (data.isNotEmpty()) {
                                        try {
                                            val json = JSONObject(data)
                                            // Handle different event types
                                            val eventType = json.optString("type")
                                            if (eventType == "response.output_text.delta") {
                                                val delta = json.optString("delta", "")
                                                if (delta.isNotEmpty()) {
                                                    trySend(delta)
                                                }
                                            } else if (eventType == "response.done") {
                                                break
                                            }
                                        } catch (e: Exception) {
                                            Timber.w(e, "Failed to parse SSE data: $data")
                                        }
                                    }
                                }

                                // End of event marker - SSE uses blank line between events
                            }
                        }
                    }
                    close()
                }
            }
        })

        awaitClose { }
    }

    override suspend fun chat(message: String): Result<String> = withContext(Dispatchers.IO) {
        val systemPrompt = settingsRepository.getLLMSystemPrompt()
        requestChat(message = message, systemPrompt = systemPrompt, temperature = 0.7, maxTokens = 1024)
    }

    override suspend fun heartbeat(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val model = settingsRepository.getLLMModel()
            val baseUrl = settingsRepository.getLLMBaseUrl().ifEmpty { "http://localhost:1234" }
            val apiPath = settingsRepository.getLLMApiPath()
            Timber.d("LLM heartbeat URL: $baseUrl$apiPath")
            val request = LLMRequest(
                model = model,
                input = "ok",
                temperature = 0.0,
                max_tokens = 1
            )
            val response = api.chat(apiPath, request)
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun routeIntent(message: String): Result<LLMRouteDecision> = withContext(Dispatchers.IO) {
        val systemPrompt = settingsRepository.getLLMRouterPrompt()
        requestChat(message = message, systemPrompt = systemPrompt, temperature = 0.0, maxTokens = 256).fold(
            onSuccess = { content ->
                parseRouteDecision(content)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun parseCommandIntent(message: String): Result<LLMParsedIntent> = withContext(Dispatchers.IO) {
        val systemPrompt = settingsRepository.getLLMCommandPrompt()
        requestChat(message = message, systemPrompt = systemPrompt, temperature = 0.0, maxTokens = 256).fold(
            onSuccess = { content ->
                parseCommand(content)
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun requestChat(
        message: String,
        systemPrompt: String,
        temperature: Double,
        maxTokens: Int
    ): Result<String> {
        return try {
            // Read config from settings at runtime
            val model = settingsRepository.getLLMModel()
            val baseUrl = settingsRepository.getLLMBaseUrl().ifEmpty { "http://localhost:1234" }
            val apiPath = settingsRepository.getLLMApiPath()
            Timber.d("LLM chat URL: $baseUrl$apiPath")
            val contextHistory = buildContextString()

            // MiniMax 等 OpenAI 兼容 API 使用 messages 格式
            val isOpenAIFormat = baseUrl.contains("minimaxi", ignoreCase = true) ||
                apiPath.contains("chatcompletion", ignoreCase = true)

            val request = if (isOpenAIFormat) {
                // 使用 messages 数组格式 (OpenAI/MiniMax/Groq/DeepSeek 等)
                val messages = mutableListOf<MessageItem>()
                if (systemPrompt.isNotBlank()) {
                    messages.add(MessageItem(role = "system", content = systemPrompt))
                }
                messages.addAll(buildMessagesFromHistory(contextHistory))
                messages.add(MessageItem(role = "user", content = message))
                LLMRequest(
                    model = model,
                    messages = messages,
                    temperature = temperature,
                    max_tokens = maxTokens
                )
            } else {
                // 使用 input 字符串格式
                LLMRequest(
                    model = model,
                    input = buildString {
                        if (systemPrompt.isNotBlank()) {
                            append("System: $systemPrompt\n")
                        }
                        if (contextHistory.isNotBlank()) {
                            append("$contextHistory\n")
                        }
                        append("User: $message")
                    },
                    temperature = temperature,
                    max_tokens = maxTokens
                )
            }

            val response = api.chat(apiPath, request)
            val isMiniMax = baseUrl.contains("minimaxi", ignoreCase = true)

            if (response.isSuccessful) {
                val bodyString = response.body()?.string() ?: ""
                val content = parseLlmResponse(bodyString)
                if (content != null) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("Empty response from LLM"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorDetail = try {
                    Gson().fromJson(errorBody, ErrorResponse::class.java)?.error
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse error response")
                    null
                }
                val errorMessage = errorDetail?.message ?: ""
                val errorCode = errorDetail?.code ?: ""

                Timber.e("LLM API error: ${response.code()} ${response.message()} | body: $errorBody | error: code=$errorCode, message=$errorMessage")

                // Check for model not found error (增强检测 "No models loaded" 类错误)
                val isModelNotFound = errorCode == "model_not_found" ||
                    errorMessage.contains("does not exist", ignoreCase = true) ||
                    errorMessage.contains("not found", ignoreCase = true) ||
                    errorMessage.contains("model", ignoreCase = true) && errorMessage.contains("not", ignoreCase = true) ||
                    errorMessage.contains("no model", ignoreCase = true) ||
                    errorMessage.contains("no models", ignoreCase = true) ||
                    errorMessage.contains("load a model", ignoreCase = true) ||
                    errorMessage.contains("model not loaded", ignoreCase = true)

                if (isModelNotFound) {
                    Result.failure(ModelNotFoundException(model))
                } else {
                    Result.failure(Exception("LLM API error: ${response.code()} - ${errorMessage.ifEmpty { response.message() }}"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "LLM chat failed")
            Result.failure(e)
        }
    }

    private fun parseRouteDecision(content: String): Result<LLMRouteDecision> {
        return try {
            val json = extractJson(content)
            val mode = when (json.optString("mode").uppercase()) {
                "COMMAND" -> LLMRouteMode.COMMAND
                else -> LLMRouteMode.CHAT
            }
            Result.success(
                LLMRouteDecision(
                    mode = mode,
                    reason = json.optString("reason").ifBlank { null }
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("路由解析失败: ${e.message}", e))
        }
    }

    private fun parseCommand(content: String): Result<LLMParsedIntent> {
        return try {
            val json = extractJson(content)
            val type = json.optString("type").uppercase().ifBlank { "UNKNOWN" }
            val action = json.optString("action").ifBlank { null }
            val query = json.optString("query").ifBlank { null }
            val value = if (json.has("value") && !json.isNull("value")) {
                when (val raw = json.get("value")) {
                    is Number -> raw.toInt()
                    is String -> raw.toIntOrNull()
                    else -> null
                }
            } else {
                null
            }
            Result.success(
                LLMParsedIntent(
                    type = type,
                    action = action,
                    query = query,
                    value = value
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("命令解析失败: ${e.message}", e))
        }
    }

    /**
     * Build context string from message history.
     * Uses the same format as core's ConversationContextManager.buildContextString().
     */
    private suspend fun buildContextString(): String {
        return try {
            val messages = messageRepository.getLatestMessages(maxContextCount * 2)
            if (messages.isEmpty()) {
                return ""
            }
            // Reverse to get chronological order (oldest first)
            val chronologicalMessages = messages.reversed()
            buildString {
                chronologicalMessages.forEach { message ->
                    val role = if (message.isUser) "User" else "Assistant"
                    appendLine("$role: ${message.text}")
                }
            }.trimEnd()
        } catch (e: Exception) {
            Timber.e(e, "Failed to build context string")
            ""
        }
    }

    /**
     * Build messages list from context history for OpenAI-format APIs.
     */
    private suspend fun buildMessagesFromHistory(contextHistory: String): List<MessageItem> {
        if (contextHistory.isBlank()) return emptyList()
        val messages = mutableListOf<MessageItem>()
        try {
            val lines = contextHistory.split("\n")
            for (line in lines) {
                if (line.startsWith("User: ")) {
                    messages.add(MessageItem(role = "user", content = line.removePrefix("User: ")))
                } else if (line.startsWith("Assistant: ")) {
                    messages.add(MessageItem(role = "assistant", content = line.removePrefix("Assistant: ")))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to build messages from history")
        }
        return messages
    }

    /**
     * Send chat with explicit config (used for testing without saving settings).
     */
    override suspend fun chatWithConfig(
        message: String,
        baseUrl: String,
        apiPath: String,
        apiKey: String,
        model: String
    ): Result<String> {
        val isMiniMax = baseUrl.contains("minimaxi", ignoreCase = true)
        val isOpenAIFormat = isMiniMax || apiPath.contains("chatcompletion", ignoreCase = true)

        val request = if (isOpenAIFormat) {
            val messages = listOf(MessageItem(role = "user", content = message))
            LLMRequest(
                model = model,
                messages = messages,
                temperature = 0.7,
                max_tokens = 32  // 测试时限制响应长度，加快返回
            )
        } else {
            LLMRequest(
                model = model,
                input = message,
                temperature = 0.7,
                max_tokens = 32  // 测试时限制响应长度，加快返回
            )
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl$apiPath")
            .post(Gson().toJson(request).toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .apply {
                if (apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }

        return doChatWithConfig(requestBuilder.build())
    }

    private suspend fun doChatWithConfig(request: Request): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("LLM doChatWithConfig: ${request.url}")
                val response: okhttp3.Response = httpClient.newCall(request).execute()
                Timber.d("LLM response code: ${response.code}")
                val bodyString = response.body?.string() ?: ""
                Timber.d("LLM response body: $bodyString")
                if (bodyString.isEmpty()) {
                    return@withContext Result.failure(Exception("Empty response body from LLM"))
                }

                val content = parseLlmResponse(bodyString)
                if (content != null) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("Empty response from LLM"))
                }
            } catch (e: Exception) {
                Timber.e(e, "LLM chatWithConfig failed")
                Result.failure(e)
            }
        }
    }

    /**
     * 统一解析 LLM 响应，自动检测格式
     * 支持: OpenAI/Ollama/DeepSeek/MiniMax/Groq 等
     */
    private fun parseLlmResponse(bodyString: String): String? {
        return try {
            val json = JSONObject(bodyString)
            // 优先尝试 OpenAI/Ollama/DeepSeek/MiniMax/Groq 格式: choices[0].message.content
            if (json.has("choices")) {
                val choices = json.getJSONArray("choices")
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    if (choice.has("message")) {
                        return choice.getJSONObject("message").optString("content", null)
                    }
                }
            }
            // 其次尝试 output 格式 (某些 API)
            if (json.has("output")) {
                val output = json.getJSONArray("output")
                if (output.length() > 0) {
                    val outItem = output.getJSONObject(0)
                    if (outItem.has("content")) {
                        val content = outItem.getJSONArray("content")
                        if (content.length() > 0) {
                            return content.getJSONObject(0).optString("text", null)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse LLM response")
            null
        }
    }

    private fun extractJson(content: String): JSONObject {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return JSONObject(trimmed)
        }

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return JSONObject(trimmed.substring(start, end + 1))
        }
        throw IllegalArgumentException("LLM 返回中未找到 JSON 对象")
    }
}
