package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.repository.ChatContextProvider
import com.voiceassistant.domain.repository.ModelNotFoundException
import com.voiceassistant.domain.repository.LLMRepository
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Use case for handling general chat commands.
 */
class HandleChatUseCase @Inject constructor(
    private val llmRepository: LLMRepository?,
    private val chatContextProvider: ChatContextProvider?
) {

    suspend fun execute(intent: Intent): String {
        if (llmRepository == null) {
            return "需要联网才能聊天，请配置 LLM API"
        }

        // Build query with conversation context
        val query = intent.query ?: ""
        val queryWithContext = chatContextProvider?.let { provider ->
            val contextString = provider.buildContextString()
            val contextCount = provider.getContextCount()
            if (contextCount > 0) {
                Timber.d("HandleChatUseCase: context available ($contextCount items), prepending context")
            }
            if (contextString.isNotEmpty()) {
                "$contextString\n\n当前消息: $query"
            } else {
                query
            }
        } ?: query

        return try {
            llmRepository.chat(queryWithContext).fold(
                onSuccess = { it },
                onFailure = { e -> getFriendlyErrorMessage(e as? Exception ?: Exception(e.toString())) }
            )
        } catch (e: Exception) {
            Timber.e(e, "Chat failed")
            getFriendlyErrorMessage(e)
        }
    }

    private fun getFriendlyErrorMessage(e: Exception): String {
        return when (e) {
            is ModelNotFoundException -> "模型 ${e.modelName} 不存在，请到设置中更换模型"
            is SocketTimeoutException -> "连接超时，请检查网络或 LLM 服务是否可用"
            is UnknownHostException -> "无法连接到 LLM 服务，请检查服务地址是否正确"
            is java.net.ConnectException -> "无法连接 LLM 服务，请检查服务是否启动"
            else -> "聊天服务暂时不可用，请稍后重试"
        }
    }
}
