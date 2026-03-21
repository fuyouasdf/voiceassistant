package com.voiceassistant.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NavidromeApi {
    
    @GET("rest/ping")
    suspend fun ping(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("c") client: String = "voice-assistant",
        @Query("v") version: String = "1.16.1",
        @Query("f") format: String = "json"
    ): Response<SubsonicResponse>
    
    @GET("rest/search3")
    suspend fun search(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("query") query: String,
        @Query("songCount") songCount: Int = 20,
        @Query("c") client: String = "voice-assistant",
        @Query("v") version: String = "1.16.1",
        @Query("f") format: String = "json"
    ): Response<SubsonicResponse>
}

data class SubsonicResponse(
    @SerializedName("subsonic-response")
    val response: SubsonicInnerResponse
)

data class SubsonicInnerResponse(
    val status: String,
    val version: String,
    val error: SubsonicError?,
    val searchResult3: SearchResult?
)

data class SubsonicError(
    val code: Int,
    val message: String
)

data class SearchResult(
    val song: List<Song>?
)

data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int
)
