package com.voiceassistant.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Jellyfin 客户端 - 使用 Retrofit 直接调用 REST API
 */
class JellyfinClient(private val baseUrl: String, private val apiKey: String) {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-MediaBrowser-Token", apiKey)
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(normalizeUrl(baseUrl))
        .client(okHttpClient)
        .build()

    private val api = retrofit.create(JellyfinApi::class.java)

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.endsWith("/")) {
            normalized += "/"
        }
        if (!normalized.startsWith("http")) {
            normalized = "http://$normalized"
        }
        return normalized
    }

    /**
     * 搜索歌曲
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<JellyfinSong> = withContext(Dispatchers.IO) {
        try {
            val response = api.getItems(
                includeMediaTypes = "Audio",
                searchTerm = query,
                limit = limit
            )

            if (response.isSuccessful) {
                response.body()?.items?.mapNotNull { item ->
                    if (item.type == "Audio") {
                        JellyfinSong(
                            id = item.id ?: return@mapNotNull null,
                            title = item.name ?: return@mapNotNull null,
                            artist = item.artists?.firstOrNull(),
                            album = item.albumName,
                            duration = ((item.runTimeTicks ?: 0) / 10000000).toInt(),
                            coverUrl = getCoverUrl(item.id ?: return@mapNotNull null)
                        )
                    } else null
                } ?: emptyList()
            } else {
                Timber.e("Search failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Search failed")
            emptyList()
        }
    }

    /**
     * 获取专辑列表
     */
    suspend fun getAlbums(parentId: String? = null, startIndex: Int = 0, limit: Int = 50): List<JellyfinAlbum> = withContext(Dispatchers.IO) {
        try {
            val response = api.getItems(
                parentId = parentId,
                includeMediaTypes = "Audio",
                startIndex = startIndex,
                limit = limit
            )

            if (response.isSuccessful) {
                response.body()?.items?.mapNotNull { item ->
                    if (item.type == "MusicAlbum" || item.type == "Folder") {
                        JellyfinAlbum(
                            id = item.id ?: return@mapNotNull null,
                            name = item.name ?: return@mapNotNull null,
                            artist = item.albumArtist,
                            imageTag = item.imageTags?.primary
                        )
                    } else null
                } ?: emptyList()
            } else {
                Timber.e("Get albums failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Get albums failed")
            emptyList()
        }
    }

    /**
     * 获取艺术家列表
     */
    suspend fun getArtists(startIndex: Int = 0, limit: Int = 50): List<JellyfinArtist> = withContext(Dispatchers.IO) {
        try {
            val response = api.getItems(
                includeMediaTypes = "Audio",
                startIndex = startIndex,
                limit = limit
            )

            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                val artistMap = mutableMapOf<String, JellyfinArtist>()
                items.forEach { item ->
                    item.artists?.forEach { artistName ->
                        if (!artistMap.containsKey(artistName)) {
                            artistMap[artistName] = JellyfinArtist(
                                id = artistName,
                                name = artistName,
                                imageTag = null
                            )
                        }
                    }
                }
                artistMap.values.toList().take(limit)
            } else {
                Timber.e("Get artists failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Get artists failed")
            emptyList()
        }
    }

    /**
     * 获取专辑/艺术家下的歌曲
     */
    suspend fun getItems(parentId: String, startIndex: Int = 0, limit: Int = 50): List<JellyfinSong> = withContext(Dispatchers.IO) {
        try {
            val response = api.getItems(
                parentId = parentId,
                includeMediaTypes = "Audio",
                startIndex = startIndex,
                limit = limit
            )

            if (response.isSuccessful) {
                response.body()?.items?.mapNotNull { item ->
                    if (item.type == "Audio") {
                        JellyfinSong(
                            id = item.id ?: return@mapNotNull null,
                            title = item.name ?: return@mapNotNull null,
                            artist = item.artists?.firstOrNull(),
                            album = item.albumName,
                            duration = ((item.runTimeTicks ?: 0) / 10000000).toInt(),
                            coverUrl = getCoverUrl(item.id ?: return@mapNotNull null)
                        )
                    } else null
                } ?: emptyList()
            } else {
                Timber.e("Get items failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Get items failed")
            emptyList()
        }
    }

    /**
     * 获取播放信息
     */
    suspend fun getPlaybackInfo(itemId: String): PlaybackResult? = withContext(Dispatchers.IO) {
        try {
            val response = api.getPlaybackInfo(itemId)
            if (response.isSuccessful) {
                val mediaSources = response.body()?.mediaSources
                val sourceInfo = mediaSources?.firstOrNull() ?: return@withContext null

                val playMethod = when {
                    sourceInfo.supportsDirectPlay == true -> PlayMethodType.DIRECT_PLAY
                    sourceInfo.supportsDirectStream == true -> PlayMethodType.DIRECT_STREAM
                    sourceInfo.supportsTranscoding == true -> PlayMethodType.TRANSCODE
                    else -> PlayMethodType.DIRECT_PLAY
                }

                PlaybackResult(
                    itemId = itemId,
                    path = sourceInfo.path,
                    container = sourceInfo.container,
                    supportsDirectPlay = sourceInfo.supportsDirectPlay ?: false,
                    supportsDirectStream = sourceInfo.supportsDirectStream ?: false,
                    supportsTranscoding = sourceInfo.supportsTranscoding ?: false,
                    transcodingUrl = sourceInfo.transcodingUrl,
                    playMethod = playMethod
                )
            } else {
                Timber.e("Get playback info failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Get playback info failed")
            null
        }
    }

    /**
     * 获取流媒体 URL
     */
    suspend fun getStreamUrl(songId: String): String = withContext(Dispatchers.IO) {
        val result = getPlaybackInfo(songId) ?: return@withContext ""

        return@withContext when (result.playMethod) {
            PlayMethodType.DIRECT_PLAY -> {
                val path = result.path
                if (!path.isNullOrEmpty()) {
                    path
                } else {
                    val container = result.container
                    if (!container.isNullOrEmpty()) {
                        "$baseUrl/Audio/$songId/stream.$container?api_key=$apiKey"
                    } else {
                        "$baseUrl/Audio/$songId/stream?api_key=$apiKey"
                    }
                }
            }
            PlayMethodType.DIRECT_STREAM -> {
                val container = result.container
                if (!container.isNullOrEmpty()) {
                    "$baseUrl/Audio/$songId/stream.$container?api_key=$apiKey"
                } else {
                    "$baseUrl/Audio/$songId/stream?api_key=$apiKey"
                }
            }
            PlayMethodType.TRANSCODE -> {
                val transcodingUrl = result.transcodingUrl
                if (!transcodingUrl.isNullOrEmpty()) {
                    if (transcodingUrl.startsWith("http")) {
                        "$transcodingUrl&api_key=$apiKey"
                    } else {
                        "$baseUrl$transcodingUrl&api_key=$apiKey"
                    }
                } else {
                    "$baseUrl/Audio/$songId/stream?api_key=$apiKey"
                }
            }
        }
    }

    /**
     * 测试连接
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSystemInfo()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Connection failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Connection test failed")
            Result.failure(e)
        }
    }

    /**
     * 获取封面图 URL
     */
    fun getCoverUrl(itemId: String, width: Int = 300, height: Int = 300): String {
        return "$baseUrl/Items/$itemId/Images/Primary?maxWidth=$width&maxHeight=$height&api_key=$apiKey"
    }
}

/**
 * 数据模型
 */
data class JellyfinSong(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int, // 秒
    val coverUrl: String? = null
)

data class JellyfinAlbum(
    val id: String,
    val name: String,
    val artist: String?,
    val imageTag: String?
)

data class JellyfinArtist(
    val id: String,
    val name: String,
    val imageTag: String?
)

data class PlaybackResult(
    val itemId: String,
    val path: String?,
    val container: String?,
    val supportsDirectPlay: Boolean,
    val supportsDirectStream: Boolean,
    val supportsTranscoding: Boolean,
    val transcodingUrl: String?,
    val playMethod: PlayMethodType
)

enum class PlayMethodType {
    DIRECT_PLAY, DIRECT_STREAM, TRANSCODE
}

/**
 * Jellyfin API 接口
 */
interface JellyfinApi {
    @GET("System/Info")
    suspend fun getSystemInfo(): Response<SystemInfoResponse>

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeMediaTypes") includeMediaTypes: String? = null,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 50
    ): Response<ItemsResponse>

    @GET("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String
    ): Response<PlaybackInfoResponse>
}

/**
 * API 响应模型
 */
data class SystemInfoResponse(
    val id: String? = null,
    val version: String? = null
)

data class ItemsResponse(
    val items: List<ItemDto>? = null
)

data class ItemDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val artists: List<String>? = null,
    val albumName: String? = null,
    val runTimeTicks: Long? = null,
    val albumArtist: String? = null,
    val imageTags: ImageTagsDto? = null
)

data class ImageTagsDto(
    val primary: String? = null
)

data class PlaybackInfoResponse(
    val mediaSources: List<MediaSourceDto>? = null
)

data class MediaSourceDto(
    val path: String? = null,
    val container: String? = null,
    val supportsDirectPlay: Boolean? = null,
    val supportsDirectStream: Boolean? = null,
    val supportsTranscoding: Boolean? = null,
    val transcodingUrl: String? = null
)
