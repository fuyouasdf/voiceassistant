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

package com.voiceassistant.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Headers

interface LLMApi {
    @POST("v1/responses")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: LLMRequest): Response<LLMResponse>
}

data class LLMRequest(
    val model: String,
    val input: String,
    val previous_response_id: String? = null,
    val reasoning: Reasoning? = null,
    val stream: Boolean = false,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1024
)

data class Reasoning(
    val effort: String = "low"
)

data class LLMResponse(
    val id: String,
    val model: String,
    val status: String,
    val output: List<OutputItem>?,
    val previous_response_id: String?,
    val usage: Usage?
)

data class OutputItem(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<OutputText>?
)

data class OutputText(
    val type: String?,
    val text: String?
)

data class Usage(
    val prompt_tokens: Int?,
    val completion_tokens: Int?,
    val total_tokens: Int?
)

data class ErrorResponse(
    val error: ErrorDetail?
)

data class ErrorDetail(
    val message: String?,
    val type: String?,
    val code: String?
)