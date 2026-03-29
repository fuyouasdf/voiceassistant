package com.voiceassistant.data.repository

import com.voiceassistant.data.remote.ChatMessage
import com.voiceassistant.data.remote.ChatRequest
import com.voiceassistant.data.remote.LLMApi
import com.voiceassistant.domain.model.Song
import com.voiceassistant.domain.repository.LLMRepository
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
                Timber.e("LLM API error: ${response.code()} ${response.message()}")
                Result.failure(Exception("LLM API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "LLM chat failed")
            Result.failure(e)
        }
    }
}