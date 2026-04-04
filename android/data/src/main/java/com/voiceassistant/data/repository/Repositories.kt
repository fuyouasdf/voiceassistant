package com.voiceassistant.data.repository

import com.google.gson.Gson
import com.voiceassistant.data.remote.ChatMessage
import com.voiceassistant.data.remote.ChatRequest
import com.voiceassistant.data.remote.ErrorResponse
import com.voiceassistant.data.remote.LLMApi
import com.voiceassistant.domain.repository.LLMParsedIntent
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.LLMRouteDecision
import com.voiceassistant.domain.repository.LLMRouteMode
import com.voiceassistant.domain.repository.ModelNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/**
 * Implementation of LLMRepository
 * Connects to DeepSeek or similar LLM API
 * Reads config from SettingsRepository at runtime
 */
class LLMRepositoryImpl(
    private val api: LLMApi,
    private val settingsRepository: SettingsRepository
) : LLMRepository {

    override suspend fun chat(message: String): Result<String> = withContext(Dispatchers.IO) {
        val systemPrompt = settingsRepository.getLLMSystemPrompt()
        requestChat(message = message, systemPrompt = systemPrompt, temperature = 0.7, maxTokens = 1024)
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

            val request = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = message)
                ),
                temperature = temperature,
                max_tokens = maxTokens
            )

            val response = api.chat(request)

            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
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
