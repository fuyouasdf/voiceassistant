package com.voiceassistant.data.repository

import com.google.gson.Gson
import com.voiceassistant.data.remote.ErrorResponse
import com.voiceassistant.data.remote.LLMApi
import com.voiceassistant.data.remote.LLMRequest
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

        val contextHistory = buildContextString()
        val requestBody = Gson().toJson(
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

        val request = Request.Builder()
            .url("$baseUrl/v1/responses")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .apply {
                if (apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }
            .build()

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
            val request = LLMRequest(
                model = model,
                input = "ok",
                temperature = 0.0,
                max_tokens = 1
            )
            val response = api.chat(request)
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
            val contextHistory = buildContextString()

            val request = LLMRequest(
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

            val response = api.chat(request)

            if (response.isSuccessful) {
                val body = response.body()
                // Parse: output[0].content[0].text
                val content = body?.output
                    ?.firstOrNull()
                    ?.content
                    ?.firstOrNull()
                    ?.text
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
