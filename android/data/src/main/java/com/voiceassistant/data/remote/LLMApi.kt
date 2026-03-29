package com.voiceassistant.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Headers

interface LLMApi {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>

    @GET("v1/models")
    suspend fun getModels(): Response<ModelsResponse>
}

data class ModelsResponse(
    val data: List<ModelInfo>
)

data class ModelInfo(
    val id: String,
    @SerializedName("object")
    val objectType: String = "model",
    val created: Long = 0,
    val owned_by: String = ""
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1024
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: Usage?
)

data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String?
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)