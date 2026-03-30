package com.voiceassistant.data.repository

import com.google.gson.Gson
import com.voiceassistant.data.remote.ChatMessage
import com.voiceassistant.data.remote.ChatRequest
import com.voiceassistant.data.remote.ErrorResponse
import com.voiceassistant.data.remote.LLMApi
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.ModelNotFoundException
import com.voiceassistant.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest

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
        try {
            // Read config from settings at runtime
            val model = settingsRepository.getLLMModel()
            val systemPrompt = settingsRepository.getLLMSystemPrompt()

            val request = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = message)
                ),
                temperature = 0.7,
                max_tokens = 1024
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
}