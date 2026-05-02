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

package com.voiceassistant.core.skill

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.skill.Skill
import com.voiceassistant.domain.skill.SkillContext
import com.voiceassistant.domain.skill.SkillResult
import com.voiceassistant.domain.usecase.HandleChatUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for handling general chat commands.
 *
 * This is the fallback skill when no other skill handles the input.
 * It delegates to the LLM for natural language processing.
 */
@Singleton
class ChatSkill @Inject constructor() : Skill {

    override val name: String = "chat"

    override val keywords: List<String> = emptyList() // No keywords - fallback only

    override val priority: Int = -10 // Lowest priority - fallback

    override fun canHandle(input: String): Boolean {
        // Chat skill doesn't match on keywords - it's the fallback
        return false
    }

    override suspend fun handle(input: String, rawInput: String, context: SkillContext): SkillResult {
        val chatUseCase = context.handleChatUseCase
        if (chatUseCase == null) {
            val llmRepo = context.llmRepository
            if (llmRepo == null) {
                return SkillResult.Success("需要联网才能聊天，请配置 LLM API")
            }
            // Direct LLM chat if HandleChatUseCase is not available
            return directChat(rawInput, context)
        }

        val intent = Intent(IntentType.CHAT, query = rawInput)
        val result = chatUseCase.execute(intent)
        return SkillResult.Success(result)
    }

    private suspend fun directChat(rawInput: String, context: SkillContext): SkillResult {
        val llmRepo = context.llmRepository ?: return SkillResult.Success("LLM 服务未配置")

        val query = rawInput.trim()
        if (query.isEmpty()) {
            return SkillResult.Success("没听懂，请再说一遍")
        }

        return try {
            // Build context if available
            val queryWithContext = context.chatContextProvider?.let { provider ->
                val contextString = provider.buildContextString()
                val contextCount = provider.getContextCount()
                if (contextCount > 0) {
                    Timber.d("ChatSkill: context available ($contextCount items), prepending context")
                }
                if (contextString.isNotEmpty()) {
                    "$contextString\n\n当前消息: $query"
                } else {
                    query
                }
            } ?: query

            llmRepo.chat(queryWithContext).fold(
                onSuccess = { SkillResult.Success(it) },
                onFailure = { e ->
                    Timber.e(e, "ChatSkill: LLM chat failed")
                    SkillResult.Success(getFriendlyErrorMessage(e as? Exception ?: Exception(e.toString())))
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "ChatSkill: exception during chat")
            SkillResult.Success(getFriendlyErrorMessage(e))
        }
    }

    private fun getFriendlyErrorMessage(e: Exception): String {
        return when (e) {
            is java.net.SocketTimeoutException -> "连接超时，请检查网络或 LLM 服务是否可用"
            is java.net.UnknownHostException -> "无法连接到 LLM 服务，请检查服务地址是否正确"
            is java.net.ConnectException -> "无法连接 LLM 服务，请检查服务是否启动"
            else -> "聊天服务暂时不可用，请稍后重试"
        }
    }
}