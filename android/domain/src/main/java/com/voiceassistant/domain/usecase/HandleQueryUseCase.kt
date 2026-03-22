package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.repository.LLMRepository
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Use case for handling information query commands (weather, time, etc).
 * Uses LLM when available, falls back to simple responses.
 */
class HandleQueryUseCase @Inject constructor(
    private val llmRepository: LLMRepository?
) {

    suspend fun execute(intent: Intent): String {
        val query = intent.query ?: ""

        if (llmRepository == null) {
            return getFallbackResponse(query)
        }

        return try {
            llmRepository.chat(query).fold(
                onSuccess = { it },
                onFailure = { e -> getFriendlyErrorMessage(e as? Exception ?: Exception(e.toString()), query) }
            )
        } catch (e: Exception) {
            Timber.e(e, "Query handling failed")
            getFriendlyErrorMessage(e, query)
        }
    }

    private fun getFriendlyErrorMessage(e: Exception, query: String): String {
        val baseMessage = when (e) {
            is SocketTimeoutException -> "连接超时，请检查网络或 LLM 服务是否可用"
            is UnknownHostException -> "无法连接到 LLM 服务，请检查服务地址是否正确"
            is java.net.ConnectException -> "无法连接 LLM 服务，请检查服务是否启动"
            else -> null
        }
        return baseMessage ?: getFallbackResponse(query)
    }

    private fun getFallbackResponse(query: String): String {
        return when {
            query.contains("天气") -> "抱歉，我需要联网才能查询天气"
            query.contains("时间") || query.contains("日期") -> "抱歉，我需要联网才能查询时间"
            else -> "需要联网才能回答这个问题"
        }
    }
}
