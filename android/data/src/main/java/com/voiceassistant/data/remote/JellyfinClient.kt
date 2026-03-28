package com.voiceassistant.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Jellyfin 客户端 - 使用 Retrofit 直接调用 REST API
 */
class JellyfinClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val deviceId: String = "voice-assistant-android"
) {

    private val gson = Gson()
    private var cachedUserId: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-MediaBrowser-Token", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            Timber.d("Jellyfin请求: ${request.method} ${request.url}")
            val response = chain.proceed(request)
            Timber.d("Jellyfin响应: ${response.code} for ${request.url}")
            response
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(normalizeUrl(baseUrl))
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val api = retrofit.create(JellyfinApi::class.java)

    init {
        Timber.d("JellyfinClient初始化: baseUrl=$baseUrl, apiKey=${if (apiKey.isNotEmpty()) "已设置" else "未设置"}")
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.endsWith("/")) {
            normalized += "/"
        }
        if (!normalized.startsWith("http")) {
            normalized = "http://$normalized"
        }
        Timber.d("normalizeUrl: $url -> $normalized")
        return normalized
    }

    /**
     * 获取默认用户 ID
     */
    private suspend fun getDefaultUserId(): String? {
        if (cachedUserId != null) {
            Timber.d("使用缓存的userId: $cachedUserId")
            return cachedUserId
        }

        return withContext(Dispatchers.IO) {
            try {
                Timber.d("正在获取用户列表...")
                val response = api.getUsers()
                Timber.d("getUsers响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                if (response.isSuccessful) {
                    val users = response.body()
                    Timber.d("用户数量: ${users?.size ?: 0}")
                    cachedUserId = users?.firstOrNull()?.id
                    Timber.d("获取到userId: $cachedUserId")
                    cachedUserId
                } else {
                    Timber.e("获取用户失败: ${response.code()} - ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "获取用户异常")
                null
            }
        }
    }

    /**
     * 搜索歌曲
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<JellyfinSong> = withContext(Dispatchers.IO) {
        Timber.d("searchSongs: query=$query, limit=$limit")
        try {
            val response = api.searchHints(
                searchTerm = query,
                mediaTypes = "Audio",
                limit = limit
            )

            Timber.d("searchHints响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val results = response.body()?.results ?: emptyList()
                Timber.d("搜索结果数量: ${results.size}")

                val songs = results.mapNotNull { hint ->
                    if (hint.mediaType == "Audio") {
                        val itemId = hint.itemId ?: hint.id ?: return@mapNotNull null
                        val song = JellyfinSong(
                            id = itemId,
                            title = hint.name ?: return@mapNotNull null,
                            artist = hint.artists?.firstOrNull(),
                            album = hint.album,
                            duration = ((hint.runTimeTicks ?: 0) / 10000000).toInt(),
                            coverUrl = getCoverUrl(itemId)
                        )
                        Timber.d("解析歌曲: ${song.title} - ${song.artist}")
                        song
                    } else {
                        Timber.d("跳过非音频项: ${hint.name}, mediaType=${hint.mediaType}")
                        null
                    }
                }
                Timber.d("返回歌曲列表: ${songs.size} 首")
                songs
            } else {
                Timber.e("搜索失败: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "搜索异常")
            emptyList()
        }
    }

    /**
     * 获取专辑列表
     */
    suspend fun getAlbums(parentId: String? = null, startIndex: Int = 0, limit: Int = 50): List<JellyfinAlbum> = withContext(Dispatchers.IO) {
        Timber.d("getAlbums: parentId=$parentId, startIndex=$startIndex, limit=$limit")

        val userId = getDefaultUserId()
        if (userId == null) {
            Timber.e("getAlbums失败: 无法获取userId")
            return@withContext emptyList()
        }

        try {
            Timber.d("正在获取专辑列表, userId=$userId...")
            val response = api.getItems(
                userId = userId,
                parentId = parentId,
                includeMediaTypes = "Audio",
                startIndex = startIndex,
                limit = limit
            )

            Timber.d("getItems响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                Timber.d("获取到${items.size}个items")

                val albums = items.mapNotNull { item ->
                    Timber.d("处理item: type=${item.type}, name=${item.name}")
                    // 匹配音乐相关的文件夹类型
                    if (item.type == "MusicAlbum" || item.type == "Folder" ||
                        item.type == "CollectionFolder" || item.type == "ManualPlaylistsFolder") {
                        val album = JellyfinAlbum(
                            id = item.id ?: return@mapNotNull null,
                            name = item.name ?: return@mapNotNull null,
                            artist = item.albumArtist,
                            imageTag = item.imageTags?.primary
                        )
                        Timber.d("解析专辑: ${album.name} - ${album.artist}")
                        album
                    } else {
                        null
                    }
                }
                Timber.d("返回专辑列表: ${albums.size} 张")
                albums
            } else {
                Timber.e("获取专辑失败: ${response.code()} - ${response.message()}")
                // 打印响应体帮助调试
                try {
                    Timber.e("响应体: ${response.errorBody()?.string()}")
                } catch (e: Exception) {
                    Timber.e(e, "读取响应体失败")
                }
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "获取专辑异常")
            emptyList()
        }
    }

    /**
     * 获取艺术家列表
     */
    suspend fun getArtists(startIndex: Int = 0, limit: Int = 50): List<JellyfinArtist> = withContext(Dispatchers.IO) {
        Timber.d("getArtists: startIndex=$startIndex, limit=$limit")

        val userId = getDefaultUserId()
        if (userId == null) {
            Timber.e("getArtists失败: 无法获取userId")
            return@withContext emptyList()
        }

        try {
            val response = api.getItems(
                userId = userId,
                includeMediaTypes = "Audio",
                startIndex = startIndex,
                limit = limit
            )

            Timber.d("getArtists响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

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
                val artists = artistMap.values.toList().take(limit)
                Timber.d("返回艺术家列表: ${artists.size} 位")
                artists
            } else {
                Timber.e("获取艺术家失败: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "获取艺术家异常")
            emptyList()
        }
    }

    /**
     * 获取所有歌曲（不带 parentId，获取用户的音乐库）
     */
    suspend fun getAllSongs(startIndex: Int = 0, limit: Int = 100): List<JellyfinSong> = withContext(Dispatchers.IO) {
        Timber.d("getAllSongs: startIndex=$startIndex, limit=$limit")

        val userId = getDefaultUserId()
        if (userId == null) {
            Timber.e("getAllSongs失败: 无法获取userId")
            return@withContext emptyList()
        }

        try {
            // 不设置 parentId，获取用户音乐库中的所有音频
            val response = api.getItems(
                userId = userId,
                parentId = null,
                includeMediaTypes = "Audio",
                startIndex = startIndex,
                limit = limit
            )

            Timber.d("getAllSongs响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                Timber.d("获取到${items.size}个items")

                val songs = items.mapNotNull { item ->
                    if (item.type == "Audio") {
                        val song = JellyfinSong(
                            id = item.id ?: return@mapNotNull null,
                            title = item.name ?: return@mapNotNull null,
                            artist = item.artists?.firstOrNull(),
                            album = item.albumName,
                            duration = ((item.runTimeTicks ?: 0) / 10000000).toInt(),
                            coverUrl = getCoverUrl(item.id ?: return@mapNotNull null)
                        )
                        Timber.d("解析歌曲: ${song.title} - ${song.artist}")
                        song
                    } else {
                        Timber.d("跳过非音频项: ${item.name}, type=${item.type}")
                        null
                    }
                }
                Timber.d("返回歌曲列表: ${songs.size} 首")
                songs
            } else {
                Timber.e("获取歌曲失败: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "获取歌曲异常")
            emptyList()
        }
    }

    /**
     * 获取文件夹下的所有项目（不限制类型）
     * 用于浏览文件夹/艺术家/专辑内容
     */
    suspend fun getFolderItems(parentId: String, startIndex: Int = 0, limit: Int = 50): List<JellyfinItem> = withContext(Dispatchers.IO) {
        Timber.d("getFolderItems: parentId=$parentId, startIndex=$startIndex, limit=$limit")

        val userId = getDefaultUserId()
        if (userId == null) {
            Timber.e("getFolderItems失败: 无法获取userId")
            return@withContext emptyList()
        }

        try {
            // 不设置 includeMediaTypes，获取所有类型
            val response = api.getItems(
                userId = userId,
                parentId = parentId,
                includeMediaTypes = null,
                startIndex = startIndex,
                limit = limit
            )

            Timber.d("getFolderItems响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                Timber.d("获取到${items.size}个items")

                val result = items.mapNotNull { item ->
                    item.id?.let { id ->
                        JellyfinItem(
                            id = id,
                            name = item.name ?: return@mapNotNull null,
                            type = item.type ?: return@mapNotNull null,
                            artist = item.artists?.firstOrNull(),
                            albumName = item.albumName,
                            runTimeTicks = item.runTimeTicks
                        )
                    }
                }
                Timber.d("返回项目列表: ${result.size} 项")
                result
            } else {
                Timber.e("获取文件夹项目失败: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "获取文件夹项目异常")
            emptyList()
        }
    }

    /**
     * 获取专辑/艺术家下的歌曲
     */
    suspend fun getItems(parentId: String, startIndex: Int = 0, limit: Int = 50): List<JellyfinSong> = withContext(Dispatchers.IO) {
        Timber.d("getItems: parentId=$parentId, startIndex=$startIndex, limit=$limit")

        val userId = getDefaultUserId()
        if (userId == null) {
            Timber.e("getItems失败: 无法获取userId")
            return@withContext emptyList()
        }

        try {
            val response = api.getItems(
                userId = userId,
                parentId = parentId,
                includeMediaTypes = "Audio",
                startIndex = startIndex,
                limit = limit
            )

            Timber.d("getItems响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                Timber.d("获取到${items.size}个items")

                val songs = items.mapNotNull { item ->
                    if (item.type == "Audio") {
                        val song = JellyfinSong(
                            id = item.id ?: return@mapNotNull null,
                            title = item.name ?: return@mapNotNull null,
                            artist = item.artists?.firstOrNull(),
                            album = item.albumName,
                            duration = ((item.runTimeTicks ?: 0) / 10000000).toInt(),
                            coverUrl = getCoverUrl(item.id ?: return@mapNotNull null)
                        )
                        Timber.d("解析歌曲: ${song.title} - ${song.artist}")
                        song
                    } else {
                        Timber.d("跳过非音频项: ${item.name}, type=${item.type}")
                        null
                    }
                }
                Timber.d("返回歌曲列表: ${songs.size} 首")
                songs
            } else {
                Timber.e("获取歌曲失败: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "获取歌曲异常")
            emptyList()
        }
    }

    /**
     * 获取播放信息
     */
    suspend fun getPlaybackInfo(itemId: String): PlaybackResult? = withContext(Dispatchers.IO) {
        Timber.d("getPlaybackInfo: itemId=$itemId")
        try {
            // 必须移除 dashes 以便服务器能找到正确的媒体源
            // 参考: https://github.com/jellyfin/jellyfin/blob/9a35fd673203cfaf0098138b2768750f4818b3ab/Jellyfin.Api/Helpers/MediaInfoHelper.cs#L196-L201
            val mediaSourceId = itemId.replace("-", "")
            // 不发送 DeviceProfile，让 Jellyfin 使用默认配置
            // 自定义 DeviceProfile 容易因字段不匹配导致 400 错误
            val playbackInfoDto = PlaybackInfoDto(
                mediaSourceId = mediaSourceId,
                maxStreamingBitrate = 100000000 // 100 Mbps
            )
            val response = api.getPlaybackInfo(itemId, playbackInfoDto)
            Timber.d("getPlaybackInfo响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")
            // 打印完整响应体以便调试
            if (!response.isSuccessful) {
                Timber.e("getPlaybackInfo失败: ${response.code()}, errorBody=${response.errorBody()?.string()}")
            } else {
                val bodyStr = gson.toJson(response.body())
                Timber.d("getPlaybackInfo响应体: $bodyStr")
            }

            if (response.isSuccessful) {
                val body = response.body()
                val playSessionId = body?.playSessionId
                Timber.d("playSessionId: $playSessionId")

                val mediaSources = body?.mediaSources
                val sourceInfo = mediaSources?.firstOrNull() ?: run {
                    Timber.e("没有媒体源信息")
                    return@withContext null
                }

                val playMethod = when {
                    sourceInfo.supportsDirectPlay == true -> PlayMethodType.DIRECT_PLAY
                    sourceInfo.supportsDirectStream == true -> PlayMethodType.DIRECT_STREAM
                    sourceInfo.supportsTranscoding == true -> PlayMethodType.TRANSCODE
                    else -> PlayMethodType.DIRECT_PLAY
                }

                Timber.d("播放方式: $playMethod, container=${sourceInfo.container}")

                PlaybackResult(
                    itemId = itemId,
                    mediaSourceId = sourceInfo.id,
                    playSessionId = playSessionId,
                    path = sourceInfo.path,
                    container = sourceInfo.container,
                    supportsDirectPlay = sourceInfo.supportsDirectPlay ?: false,
                    supportsDirectStream = sourceInfo.supportsDirectStream ?: false,
                    supportsTranscoding = sourceInfo.supportsTranscoding ?: false,
                    transcodingUrl = sourceInfo.transcodingUrl,
                    playMethod = playMethod
                )
            } else {
                Timber.e("获取播放信息失败: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "获取播放信息异常")
            null
        }
    }

    /**
     * 获取流媒体 URL
     * 参考 jellyfin-android: VideosApi.getVideoStreamUrl() 和 getVideoStreamByContainerUrl()
     */
    suspend fun getStreamUrl(songId: String): String = withContext(Dispatchers.IO) {
        Timber.d("getStreamUrl: songId=$songId")
        val result = getPlaybackInfo(songId) ?: run {
            Timber.e("无法获取播放信息，返回空URL")
            return@withContext ""
        }

        // 重要：mediaSourceId 必须移除 dashes 才能让服务器找到正确的媒体源
        // 参考: https://github.com/jellyfin/jellyfin/blob/9a35fd673203cfaf0098138b2768750f4818b3ab/Jellyfin.Api/Helpers/MediaInfoHelper.cs#L196-L201
        val mediaSourceId = (result.mediaSourceId ?: songId).replace("-", "")
        val playSessionId = result.playSessionId ?: ""
        val apiKeyParam = "api_key=$apiKey"
        val playSessionParam = if (playSessionId.isNotEmpty()) "&PlaySessionId=$playSessionId" else ""
        val mediaSourceIdParam = "&MediaSourceId=$mediaSourceId"
        val deviceIdParam = "&DeviceId=$deviceId"

        Timber.d("getStreamUrl: playMethod=${result.playMethod}, mediaSourceId=$mediaSourceId (original=${result.mediaSourceId}), path=${result.path}, container=${result.container}, transcodingUrl=${result.transcodingUrl}")

        // ExoPlayer 不支持 asf/wma 容器，需要转码
        // ExoPlayer 支持: mp3, aac, flac, ogg, wav, opus, mp4, mkv, webm, ts 等
        // 不支持: asf (wma), wmv, avi, divx
        val unsupportedContainers = listOf("asf", "wmv", "avi", "divx")
        val needsTranscode = result.container?.lowercase() in unsupportedContainers

        val streamUrl = when {
            // 如果容器不支持，直接使用转码
            needsTranscode -> {
                Timber.d("容器 ${result.container} 不被 ExoPlayer 支持，使用转码")
                // transcodingUrl 为 null 说明服务器不支持此格式转码
                if (result.transcodingUrl != null) {
                    val fullUrl = if (result.transcodingUrl.startsWith("http")) {
                        result.transcodingUrl
                    } else {
                        "$baseUrl${result.transcodingUrl}"
                    }
                    val separator = if (fullUrl.contains("?")) "&" else "?"
                    val url = "$fullUrl$separator$apiKeyParam$deviceIdParam"
                    Timber.d("使用转码URL (from transcodingUrl): $url")
                    url
                } else {
                    // transcodingUrl 为 null，尝试使用 /Videos/{id}/stream 不带 static，让服务器决定是否转码
                    val url = "$baseUrl/Videos/$songId/stream?$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam"
                    Timber.d("使用转码URL (stream): $url")
                    url
                }
            }
            result.playMethod == PlayMethodType.DIRECT_PLAY -> {
                // jellyfin-android 使用: /Videos/{itemId}/stream?static=true&playSessionId=...&mediaSourceId=...&deviceId=...
                // 即使 path 是 HTTP URL，也通过 /Videos/{id}/stream 获取以获取正确的流
                val url = "$baseUrl/Videos/$songId/stream?static=true&$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam"
                Timber.d("使用DIRECT_PLAY生成URL: $url")
                url
            }
            result.playMethod == PlayMethodType.DIRECT_STREAM -> {
                // 使用 /Videos/{itemId}/stream.{container} 格式
                val container = result.container
                val url = if (!container.isNullOrEmpty()) {
                    "$baseUrl/Videos/$songId/stream.$container?$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam"
                } else {
                    "$baseUrl/Videos/$songId/stream?$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam"
                }
                Timber.d("使用DIRECT_STREAM URL: $url")
                url
            }
            else -> {
                // TRANSCODE - 使用服务器返回的 transcodingUrl
                val transcodingUrl = result.transcodingUrl
                val url = if (!transcodingUrl.isNullOrEmpty()) {
                    val fullUrl = if (transcodingUrl.startsWith("http")) {
                        transcodingUrl
                    } else {
                        "$baseUrl$transcodingUrl"
                    }
                    // transcodingUrl 通常已包含必要的参数
                    if (fullUrl.contains("?")) {
                        "$fullUrl&$apiKeyParam$deviceIdParam"
                    } else {
                        "$fullUrl?$apiKeyParam$deviceIdParam"
                    }
                } else {
                    "$baseUrl/Videos/$songId/stream?$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam"
                }
                Timber.d("使用TRANSCODE URL: $url")
                url
            }
        }
        Timber.d("getStreamUrl 最终streamUrl: $streamUrl")
        streamUrl
    }

    /**
     * 测试连接
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("testConnection: baseUrl=$baseUrl")
        try {
            val response = api.getSystemInfo()
            Timber.d("testConnection响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val info = response.body()
                Timber.d("Jellyfin服务器版本: ${info?.version ?: "unknown"}")
                Result.success(true)
            } else {
                Timber.e("连接测试失败: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Connection failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "连接测试异常")
            Result.failure(e)
        }
    }

    /**
     * 获取封面图 URL
     */
    fun getCoverUrl(itemId: String, width: Int = 300, height: Int = 300): String {
        val url = "$baseUrl/Items/$itemId/Images/Primary?maxWidth=$width&maxHeight=$height&api_key=$apiKey"
        Timber.d("封面图URL: $url")
        return url
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

/**
 * 通用文件夹项目（用于浏览文件夹内容）
 */
data class JellyfinItem(
    val id: String,
    val name: String,
    val type: String, // Audio, MusicAlbum, MusicArtist, Folder, CollectionFolder 等
    val artist: String? = null,
    val albumName: String? = null,
    val runTimeTicks: Long? = null
) {
    val duration: Int
        get() = ((runTimeTicks ?: 0) / 10000000).toInt()

    val isAudio: Boolean get() = type == "Audio"
    val isAlbum: Boolean get() = type == "MusicAlbum"
    val isArtist: Boolean get() = type == "MusicArtist"
    val isFolder: Boolean get() = type == "Folder" || type == "CollectionFolder" || type == "ManualPlaylistsFolder"
}

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
    val mediaSourceId: String?,
    val playSessionId: String?,
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

    @GET("Users")
    suspend fun getUsers(): Response<List<UserDto>>

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeMediaTypes") includeMediaTypes: String? = null,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 50
    ): Response<ItemsResponse>

    @GET("Search/Hints")
    suspend fun searchHints(
        @Query("searchTerm") searchTerm: String,
        @Query("mediaTypes") mediaTypes: String = "Audio",
        @Query("limit") limit: Int = 20
    ): Response<SearchHintsResponse>

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Body playbackInfoDto: PlaybackInfoDto
    ): Response<PlaybackInfoResponse>
}

/**
 * API 响应模型
 */
data class SystemInfoResponse(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Version") val version: String? = null
)

data class UserDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null
)

data class ItemsResponse(
    @SerializedName("Items") val items: List<ItemDto>? = null
)

data class ItemDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Artists") val artists: List<String>? = null,
    @SerializedName("AlbumName") val albumName: String? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("AlbumArtist") val albumArtist: String? = null,
    @SerializedName("ImageTags") val imageTags: ImageTagsDto? = null
)

data class ImageTagsDto(
    @SerializedName("Primary") val primary: String? = null
)

data class SearchHintsResponse(
    @SerializedName("TotalRecordCount") val totalRecordCount: Int? = null,
    @SerializedName("Results") val results: List<SearchHintDto>? = null
)

data class SearchHintDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("MediaType") val mediaType: String? = null,
    @SerializedName("Album") val album: String? = null,
    @SerializedName("Artists") val artists: List<String>? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("ItemId") val itemId: String? = null
)

data class PlaybackInfoResponse(
    @SerializedName("MediaSources") val mediaSources: List<MediaSourceDto>? = null,
    @SerializedName("PlaySessionId") val playSessionId: String? = null
)

data class PlaybackInfoDto(
    @SerializedName("MediaSourceId") val mediaSourceId: String? = null,
    @SerializedName("DeviceProfile") val deviceProfile: DeviceProfileDto? = null,
    @SerializedName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @SerializedName("StartTimeTicks") val startTimeTicks: Long? = null,
    @SerializedName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerializedName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerializedName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean = true
)

data class DeviceProfileDto(
    @SerializedName("Name") val name: String = "ExoPlayer",
    @SerializedName("MaxStreamingBitrate") val maxStreamingBitrate: Int = 100000000,
    @SerializedName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfileDto>? = null,
    @SerializedName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfileDto>? = null,
    @SerializedName("MusicStreamingTranscodingBitrate") val musicStreamingTranscodingBitrate: Int? = null
)

data class DirectPlayProfileDto(
    @SerializedName("Container") val container: String? = null,
    @SerializedName("AudioCodec") val audioCodec: String? = null,
    @SerializedName("Type") val type: String = "Video"
)

data class TranscodingProfileDto(
    @SerializedName("Container") val container: String = "mp4",
    @SerializedName("AudioCodec") val audioCodec: String = "aac",
    @SerializedName("Type") val type: String = "Audio"
)

data class MediaSourceDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("Container") val container: String? = null,
    @SerializedName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerializedName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerializedName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerializedName("TranscodingUrl") val transcodingUrl: String? = null
)