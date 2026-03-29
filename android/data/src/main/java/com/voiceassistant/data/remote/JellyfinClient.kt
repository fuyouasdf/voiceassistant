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
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Jellyfin 客户端 - 使用 Retrofit 直接调用 REST API
 */
class JellyfinClient(
    private val baseUrl: String,
    private val apiKey: String = "",
    private val deviceId: String = "voice-assistant-android"
) {

    private val gson = Gson()
    private var cachedUserId: String? = null
    private var cachedAccessToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
            // Use access token if available, otherwise use API key
            if (!cachedAccessToken.isNullOrEmpty()) {
                requestBuilder.addHeader("X-MediaBrowser-Token", cachedAccessToken!!)
            } else if (apiKey.isNotEmpty()) {
                requestBuilder.addHeader("X-MediaBrowser-Token", apiKey)
            }
            val request = requestBuilder.build()
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
    private val sessionApi = retrofit.create(JellyfinSessionApi::class.java)

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
     * 使用 /Items 端点（与 Jellyfin Web 相同），需要 userId 才能返回结果
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<JellyfinSong> = withContext(Dispatchers.IO) {
        Timber.d("searchSongs: query=$query, limit=$limit")

        val userId = getDefaultUserId()
        if (userId == null) {
            Timber.e("searchSongs失败: 无法获取userId")
            return@withContext emptyList()
        }

        try {
            // 与 Jellyfin Web 相同的搜索方式：使用 /Items 端点 + searchTerm
            val response = api.getItems(
                userId = userId,
                parentId = null,  // 不限制文件夹，全局搜索
                includeMediaTypes = "Audio",
                searchTerm = query,
                startIndex = 0,
                limit = limit,
                recursive = true  // 递归搜索子文件夹
            )

            Timber.d("searchSongs响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                Timber.d("搜索结果数量: ${items.size}")

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
                Timber.e("搜索失败: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "搜索异常")
            emptyList()
        }
    }

    /**
     * 获取专辑列表，或搜索专辑（当 searchTerm 不为空时）
     */
    suspend fun getAlbums(parentId: String? = null, startIndex: Int = 0, limit: Int = 50, searchTerm: String? = null): List<JellyfinAlbum> = withContext(Dispatchers.IO) {
        Timber.d("getAlbums: parentId=$parentId, startIndex=$startIndex, limit=$limit, searchTerm=$searchTerm")

        val userId = getDefaultUserId()
        if (userId == null) {
            Timber.e("getAlbums失败: 无法获取userId")
            return@withContext emptyList()
        }

        try {
            Timber.d("正在获取专辑列表, userId=$userId...")
            // 浏览时：使用 IncludeMediaTypes=Audio 获取所有音频相关项目（包括 CollectionFolder）
            // 搜索时：使用 IncludeItemTypes=MusicAlbum 精确获取专辑
            val response = if (searchTerm != null) {
                api.getItems(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = "MusicAlbum",
                    startIndex = startIndex,
                    limit = limit,
                    searchTerm = searchTerm,
                    recursive = true
                )
            } else {
                api.getItems(
                    userId = userId,
                    parentId = parentId,
                    includeMediaTypes = "Audio",
                    startIndex = startIndex,
                    limit = limit
                )
            }

            Timber.d("getItems响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val items = response.body()?.items ?: emptyList()
                Timber.d("获取到${items.size}个items")

                val albums = items.mapNotNull { item ->
                    Timber.d("处理item: type=${item.type}, name=${item.name}")
                    // 匹配音乐相关的文件夹类型（用于浏览）
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
     * 如果直接获取不到歌曲，会递归搜索子文件夹
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

                // 首先收集直接包含的Audio项
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
                }.toMutableList()

                // 如果没有找到直接歌曲，递归搜索子文件夹
                if (songs.isEmpty()) {
                    Timber.d("未找到直接歌曲，递归搜索子文件夹...")
                    val folders = items.filter { item ->
                        item.type == "Folder" || item.type == "MusicAlbum" ||
                        item.type == "MusicArtist" || item.type == "CollectionFolder"
                    }
                    for (folder in folders) {
                        Timber.d("搜索文件夹: ${folder.name}, id=${folder.id}")
                        folder.id?.let { folderId ->
                            val subSongs = getItems(folderId, 0, 50)
                            if (subSongs.isNotEmpty()) {
                                songs.addAll(subSongs)
                                Timber.d("从文件夹 ${folder.name} 获取到 ${subSongs.size} 首歌曲")
                            }
                        }
                        // 限制总数，避免过多递归
                        if (songs.size >= 50) break
                    }
                }

                Timber.d("返回歌曲列表: ${songs.size} 首")
                songs.take(limit)
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
        val playSessionParam = if (playSessionId.isNotEmpty()) "&PlaySessionId=$playSessionId" else ""
        val mediaSourceIdParam = "&MediaSourceId=$mediaSourceId"
        val deviceIdParam = "&DeviceId=$deviceId"
        val apiKeyParam = "api_key=$apiKey"

        Timber.d("getStreamUrl: playMethod=${result.playMethod}, mediaSourceId=$mediaSourceId (original=${result.mediaSourceId}), path=${result.path}, container=${result.container}, transcodingUrl=${result.transcodingUrl}")

        // ExoPlayer 不支持 asf/wma 容器，需要转码
        // ExoPlayer 支持: mp3, aac, flac, ogg, wav, opus, mp4, mkv, webm, ts 等
        // 不支持: asf (wma), wmv, avi, divx
        val unsupportedContainers = listOf("asf", "wmv", "avi", "divx")
        val needsTranscode = result.container?.lowercase() in unsupportedContainers

        val streamUrl = when {
            // 如果容器不支持，使用 /Audio 端点强制转码为 AAC/MP4
            // 注意：转码流不支持 HTTP Range seek，Jellyfin 会忽略 Range 头从0开始
            needsTranscode -> {
                Timber.d("容器 ${result.container} 不被 ExoPlayer 支持，使用转码")
                // 使用 /Audio/{id}/stream 并强制转码参数
                val url = "$baseUrl/Audio/$songId/stream?$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam&Container=mp4&AudioCodec=aac"
                Timber.d("使用转码URL (Audio stream): $url")
                url
            }
            result.playMethod == PlayMethodType.DIRECT_PLAY -> {
                // 使用 /Videos/{id}/stream 但不带 static=true
                // 这样 Jellyfin 返回 206 Partial Content，支持 HTTP Range seek
                val url = "$baseUrl/Videos/$songId/stream?$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam"
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
     * 获取流媒体信息（包含 URL 和会话信息）
     */
    suspend fun getStreamInfo(songId: String): StreamInfo = withContext(Dispatchers.IO) {
        val url = getStreamUrl(songId)
        val result = getPlaybackInfo(songId)
        StreamInfo(
            url = url,
            playSessionId = result?.playSessionId,
            mediaSourceId = result?.mediaSourceId?.replace("-", "")
        )
    }

    /**
     * 获取带起始位置的流媒体 URL（用于 seek 到指定位置）
     * 使用 /Audio/{id}/stream 端点 + StartTimeTicks 参数，让 Jellyfin 从指定位置开始转码
     * @param songId 歌曲 ID
     * @param startTimeMs 起始位置（毫秒）
     * @param playSessionId 可选：已有的播放会话 ID（避免重新调用 getPlaybackInfo）
     * @param mediaSourceId 可选：已有的媒体源 ID
     */
    suspend fun getStreamUrlWithStartTime(
        songId: String,
        startTimeMs: Long,
        playSessionId: String? = null,
        mediaSourceId: String? = null
    ): String = withContext(Dispatchers.IO) {
        // 如果没有提供 session 信息，才需要调用 getPlaybackInfo
        val effectivePlaySessionId: String
        val effectiveMediaSourceId: String
        if (playSessionId.isNullOrEmpty() || mediaSourceId.isNullOrEmpty()) {
            val result = getPlaybackInfo(songId)
            if (result == null) {
                Timber.e("无法获取播放信息，返回空URL")
                return@withContext ""
            }
            effectivePlaySessionId = result.playSessionId ?: ""
            effectiveMediaSourceId = (result.mediaSourceId ?: songId).replace("-", "")
        } else {
            effectivePlaySessionId = playSessionId
            effectiveMediaSourceId = mediaSourceId.replace("-", "")
        }

        val startTimeTicks = startTimeMs * 10000 // 毫秒转 ticks
        val apiKeyParam = "api_key=$apiKey"
        val playSessionParam = if (effectivePlaySessionId.isNotEmpty()) "&PlaySessionId=$effectivePlaySessionId" else ""
        val mediaSourceIdParam = "&MediaSourceId=$effectiveMediaSourceId"
        val deviceIdParam = "&DeviceId=$deviceId"
        val startTimeParam = "&StartTimeTicks=$startTimeTicks"

        // 始终使用 /Audio/{id}/stream 端点进行 seek
        // 这样 Jellyfin 从 StartTimeTicks 指定的位置开始转码，支持任意 seek 位置
        val url = "$baseUrl/Audio/$songId/stream?$apiKeyParam$playSessionParam$mediaSourceIdParam$deviceIdParam$startTimeParam&Container=mp4&AudioCodec=aac"
        Timber.d("getStreamUrlWithStartTime: songId=$songId, startTimeMs=$startTimeMs, sessionId=$effectivePlaySessionId, url=$url")
        url
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
     * 使用用户名密码登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果，包含用户ID和访问令牌
     */
    suspend fun login(username: String, password: String): Result<LoginResult> = withContext(Dispatchers.IO) {
        Timber.d("login: username=$username")
        try {
            // Jellyfin 密码使用 MD5 hash
            val md5Hash = md5(password)
            val request = AuthenticateRequest(
                username = username,
                password = md5Hash,
                passwordMd5 = md5Hash
            )
            val response = api.authenticateByName(request)
            Timber.d("login响应: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val authResponse = response.body()
                val user = authResponse?.user
                val session = authResponse?.session
                if (user != null && session != null) {
                    cachedUserId = user.id
                    cachedAccessToken = session.accessToken
                    Timber.d("登录成功: userId=${user.id}")
                    Result.success(LoginResult(
                        userId = user.id ?: "",
                        userName = user.name ?: username,
                        accessToken = session.accessToken ?: ""
                    ))
                } else {
                    Timber.e("登录响应数据不完整")
                    Result.failure(Exception("Invalid login response"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("登录失败: ${response.code()}, errorBody=$errorBody")
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "登录异常")
            Result.failure(e)
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 获取封面图 URL
     */
    fun getCoverUrl(itemId: String, width: Int = 300, height: Int = 300): String {
        val url = "$baseUrl/Items/$itemId/Images/Primary?maxWidth=$width&maxHeight=$height&api_key=$apiKey"
        Timber.d("封面图URL: $url")
        return url
    }

    /**
     * 获取单个项目详情
     */
    suspend fun getItem(itemId: String): JellyfinSong? = withContext(Dispatchers.IO) {
        Timber.d("getItem: itemId=$itemId")
        try {
            val response = api.getItem(itemId)
            if (response.isSuccessful) {
                val item = response.body()
                if (item != null && item.type == "Audio") {
                    JellyfinSong(
                        id = item.id ?: "",
                        title = item.name ?: "未知歌曲",
                        artist = item.artists?.firstOrNull(),
                        album = item.albumName,
                        duration = ((item.runTimeTicks ?: 0) / 10000000).toInt(),
                        coverUrl = getCoverUrl(item.id ?: "")
                    )
                } else {
                    null
                }
            } else {
                Timber.e("getItem失败: ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "getItem异常")
            null
        }
    }

    /**
     * 上报播放开始
     */
    suspend fun reportPlaybackStart(
        itemId: String,
        positionTicks: Long = 0,
        isPaused: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("reportPlaybackStart: itemId=$itemId, positionTicks=$positionTicks, isPaused=$isPaused")
        try {
            val request = PlaybackStartRequest(
                itemId = itemId,
                playMethod = "DirectPlay",
                positionTicks = positionTicks,
                isPaused = isPaused
            )
            val response = api.reportPlaybackStart(request)
            if (response.isSuccessful) {
                Timber.d("reportPlaybackStart成功")
                Result.success(true)
            } else {
                Timber.e("reportPlaybackStart失败: ${response.code()}")
                Result.failure(Exception("Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "reportPlaybackStart异常")
            Result.failure(e)
        }
    }

    /**
     * 上报播放进度
     */
    suspend fun reportPlaybackProgress(
        itemId: String,
        positionTicks: Long,
        isPaused: Boolean = false,
        playSessionId: String? = null,
        mediaSourceId: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        // 降低日志频率，只在关键时候打印
        Timber.d("reportPlaybackProgress: itemId=$itemId, positionTicks=$positionTicks, playSessionId=$playSessionId")
        try {
            val request = PlaybackProgressRequest(
                itemId = itemId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                playSessionId = playSessionId,
                mediaSourceId = mediaSourceId
            )
            val response = api.reportPlaybackProgress(request)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 上报播放停止
     */
    suspend fun reportPlaybackStopped(
        itemId: String,
        positionTicks: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("reportPlaybackStopped: itemId=$itemId, positionTicks=$positionTicks")
        try {
            val request = PlaybackStopRequest(
                itemId = itemId,
                positionTicks = positionTicks
            )
            val response = api.reportPlaybackStopped(request)
            if (response.isSuccessful) {
                Timber.d("reportPlaybackStopped成功")
                Result.success(true)
            } else {
                Timber.e("reportPlaybackStopped失败: ${response.code()}")
                Result.failure(Exception("Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "reportPlaybackStopped异常")
            Result.failure(e)
        }
    }

    /**
     * 标记项目为收藏
     */
    suspend fun markAsFavorite(itemId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("markAsFavorite: itemId=$itemId")
        val userId = getDefaultUserId() ?: return@withContext Result.failure(Exception("No user ID"))
        try {
            val response = api.markAsFavorite(userId, itemId)
            if (response.isSuccessful) {
                Timber.d("markAsFavorite成功")
                Result.success(true)
            } else {
                Timber.e("markAsFavorite失败: ${response.code()}")
                Result.failure(Exception("Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "markAsFavorite异常")
            Result.failure(e)
        }
    }

    /**
     * 取消收藏
     */
    suspend fun removeFromFavorites(itemId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("removeFromFavorites: itemId=$itemId")
        val userId = getDefaultUserId() ?: return@withContext Result.failure(Exception("No user ID"))
        try {
            val response = api.removeFromFavorites(userId, itemId)
            if (response.isSuccessful) {
                Timber.d("removeFromFavorites成功")
                Result.success(true)
            } else {
                Timber.e("removeFromFavorites失败: ${response.code()}")
                Result.failure(Exception("Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "removeFromFavorites异常")
            Result.failure(e)
        }
    }

    /**
     * 检查项目是否为收藏状态
     */
    suspend fun isFavorite(itemId: String): Boolean = withContext(Dispatchers.IO) {
        Timber.d("isFavorite: itemId=$itemId")
        val userId = getDefaultUserId() ?: return@withContext false
        try {
            val response = api.getUserItem(userId, itemId)
            if (response.isSuccessful) {
                val data = response.body()
                Timber.d("isFavorite: ${data?.favorite ?: false}")
                data?.favorite ?: false
            } else {
                Timber.e("isFavorite检查失败: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "isFavorite异常")
            false
        }
    }

    // ==================== Session 远程控制相关方法 ====================

    /**
     * 获取所有活跃会话
     */
    suspend fun getSessions(): List<SessionInfo> = withContext(Dispatchers.IO) {
        Timber.d("getSessions")
        try {
            val response = sessionApi.getSessions()
            if (response.isSuccessful) {
                response.body()?.mapNotNull { dto -> dto.toSessionInfo() } ?: emptyList()
            } else {
                Timber.e("获取会话失败: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "获取会话异常")
            emptyList()
        }
    }

    /**
     * 发送播放命令（通过 Playing 端点）
     * 使用 /Sessions/{sessionId}/Playing?ItemIds=xxx&PlayCommand=PlayNow
     */
    suspend fun playToSession(sessionId: String, itemId: String, playCommand: String = "PlayNow", startPositionTicks: Long = 0): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("playToSession: sessionId=$sessionId, itemId=$itemId, playCommand=$playCommand, startPositionTicks=$startPositionTicks")
        try {
            val response = sessionApi.playToSession(sessionId, itemId, playCommand, startPositionTicks)
            if (response.isSuccessful || response.code() == 204) {
                Timber.d("playToSession成功")
                Result.success(true)
            } else {
                Timber.e("playToSession失败: ${response.code()}")
                Result.failure(Exception("Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "playToSession异常")
            Result.failure(e)
        }
    }

    /**
     * 发送播放命令（通过 Command 端点）
     * 支持: Play, Pause, Unpause, Stop, Seek
     */
    suspend fun sendSessionCommand(sessionId: String, command: String, arguments: Map<String, Any>? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("sendSessionCommand: sessionId=$sessionId, command=$command, arguments=$arguments")
        try {
            val request = SessionCommandRequest(cmd = command, arguments = arguments)
            val response = sessionApi.sendCommand(sessionId, request)
            if (response.isSuccessful || response.code() == 204) {
                Timber.d("sendSessionCommand成功: $command")
                Result.success(true)
            } else {
                Timber.e("sendSessionCommand失败: ${response.code()}")
                Result.failure(Exception("Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "sendSessionCommand异常")
            Result.failure(e)
        }
    }

    /**
     * 播放（恢复播放）
     */
    suspend fun play(sessionId: String, itemId: String? = null, startPositionTicks: Long = 0): Result<Boolean> {
        val args = mutableMapOf<String, Any>()
        itemId?.let { args["ItemIds"] = it }
        if (startPositionTicks > 0) args["StartPositionTicks"] = startPositionTicks
        return sendSessionCommand(sessionId, "Play", args.ifEmpty { null })
    }

    /**
     * 暂停播放
     */
    suspend fun pause(sessionId: String): Result<Boolean> = sendSessionCommand(sessionId, "Pause")

    /**
     * 恢复播放
     */
    suspend fun unpause(sessionId: String): Result<Boolean> = sendSessionCommand(sessionId, "Unpause")

    /**
     * 停止播放
     */
    suspend fun stop(sessionId: String): Result<Boolean> = sendSessionCommand(sessionId, "Stop")

    /**
     * 跳转播放位置
     * @param positionMs 位置（毫秒）
     */
    suspend fun seek(sessionId: String, positionMs: Long): Result<Boolean> {
        val positionTicks = positionMs * 10000 // 毫秒转 ticks
        return sendSessionCommand(sessionId, "Seek", mapOf("PositionTicks" to positionTicks))
    }

    /**
     * 播放指定项目到目标会话
     * @param sessionId 会话ID
     * @param itemId 项目ID
     * @param startPositionMs 起始位置（毫秒）
     */
    suspend fun playItem(sessionId: String, itemId: String, startPositionMs: Long = 0): Result<Boolean> {
        val startTicks = startPositionMs * 10000
        return playToSession(sessionId, itemId, "PlayNow", startTicks)
    }

    /**
     * 下一曲
     */
    suspend fun nextTrack(sessionId: String): Result<Boolean> = sendSessionCommand(sessionId, "NextTrack")

    /**
     * 上一曲
     */
    suspend fun previousTrack(sessionId: String): Result<Boolean> = sendSessionCommand(sessionId, "PreviousTrack")
}

// SessionDto 扩展函数
private fun SessionDto.toSessionInfo(): SessionInfo? {
    if (id == null) return null
    return SessionInfo(
        id = id,
        deviceName = deviceName ?: "Unknown",
        deviceId = deviceId ?: "",
        client = client ?: "",
        userName = userName,
        userId = userId,
        isActive = isActive ?: false,
        supportsMediaControl = supportsMediaControl ?: false,
        playbackState = playState?.let {
            SessionPlaybackState(
                positionTicks = it.positionTicks ?: 0,
                isPaused = it.isPaused ?: false,
                isMuted = it.isMuted ?: false,
                volumeLevel = it.volumeLevel ?: 100,
                canSeek = it.canSeek ?: false,
                playMethod = it.playMethod,
                repeatMode = it.repeatMode ?: "RepeatNone"
            )
        },
        nowPlayingItem = nowPlayingItem?.let {
            SessionNowPlayingItem(
                id = it.id ?: "",
                name = it.name ?: "Unknown",
                album = it.album,
                artists = it.artists ?: emptyList(),
                durationTicks = it.runTimeTicks ?: 0,
                mediaType = it.mediaType ?: "Audio"
            )
        }
    )
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
 * 流媒体信息（包含 URL 和会话信息）
 */
data class StreamInfo(
    val url: String,
    val playSessionId: String?,
    val mediaSourceId: String?
)

/**
 * Jellyfin Session 远程控制 API
 */
interface JellyfinSessionApi {
    // 获取所有会话
    @GET("Sessions")
    suspend fun getSessions(): Response<List<SessionDto>>

    // 发送播放命令（通过 Command 端点）
    @POST("Sessions/{sessionId}/Command")
    suspend fun sendCommand(
        @Path("sessionId") sessionId: String,
        @Body request: SessionCommandRequest
    ): Response<Unit>

    // 播放到指定会话（通过 Playing 端点）
    @POST("Sessions/{sessionId}/Playing")
    suspend fun playToSession(
        @Path("sessionId") sessionId: String,
        @Query("ItemIds") itemIds: String,
        @Query("PlayCommand") playCommand: String = "PlayNow",
        @Query("StartPositionTicks") startPositionTicks: Long = 0
    ): Response<Unit>

    // 上报播放开始
    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Body request: PlaybackStartRequest
    ): Response<Unit>

    // 上报播放进度
    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Body request: PlaybackProgressRequest
    ): Response<Unit>

    // 上报播放停止
    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Body request: PlaybackStopRequest
    ): Response<Unit>
}

/**
 * Jellyfin API 接口
 */
interface JellyfinApi {
    @GET("System/Info")
    suspend fun getSystemInfo(): Response<SystemInfoResponse>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body request: AuthenticateRequest
    ): Response<AuthenticateResponse>

    @GET("Users")
    suspend fun getUsers(): Response<List<UserDto>>

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeMediaTypes") includeMediaTypes: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 50,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("Recursive") recursive: Boolean = false
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

    @GET("Items/{itemId}")
    suspend fun getItem(
        @Path("itemId") itemId: String
    ): Response<ItemDto>

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Body request: PlaybackStartRequest
    ): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Body request: PlaybackProgressRequest
    ): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Body request: PlaybackStopRequest
    ): Response<Unit>

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun markAsFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    ): Response<UserItemDataDto>

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFromFavorites(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    ): Response<UserItemDataDto>

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getUserItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    ): Response<UserItemDataDto>
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

/**
 * 播放进度上报请求
 */
data class PlaybackStartRequest(
    @SerializedName("ItemId") val itemId: String,
    @SerializedName("PlayMethod") val playMethod: String = "DirectPlay",
    @SerializedName("PlaySessionId") val playSessionId: String? = null,
    @SerializedName("MediaSourceId") val mediaSourceId: String? = null,
    @SerializedName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerializedName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerializedName("IsPaused") val isPaused: Boolean = false,
    @SerializedName("IsMuted") val isMuted: Boolean = false,
    @SerializedName("CanSeek") val canSeek: Boolean = true,
    @SerializedName("PositionTicks") val positionTicks: Long? = null,
    @SerializedName("VolumeLevel") val volumeLevel: Int? = null,
    @SerializedName("RepeatMode") val repeatMode: String = "RepeatNone",
    @SerializedName("PlaybackOrder") val playbackOrder: String = "Default"
)

data class PlaybackProgressRequest(
    @SerializedName("ItemId") val itemId: String,
    @SerializedName("PlayMethod") val playMethod: String = "DirectPlay",
    @SerializedName("PlaySessionId") val playSessionId: String? = null,
    @SerializedName("MediaSourceId") val mediaSourceId: String? = null,
    @SerializedName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerializedName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerializedName("IsPaused") val isPaused: Boolean = false,
    @SerializedName("IsMuted") val isMuted: Boolean = false,
    @SerializedName("CanSeek") val canSeek: Boolean = true,
    @SerializedName("PositionTicks") val positionTicks: Long,
    @SerializedName("VolumeLevel") val volumeLevel: Int? = null,
    @SerializedName("RepeatMode") val repeatMode: String = "RepeatNone",
    @SerializedName("PlaybackOrder") val playbackOrder: String = "Default"
)

data class PlaybackStopRequest(
    @SerializedName("ItemId") val itemId: String,
    @SerializedName("PlaySessionId") val playSessionId: String? = null,
    @SerializedName("MediaSourceId") val mediaSourceId: String? = null,
    @SerializedName("PositionTicks") val positionTicks: Long,
    @SerializedName("Failed") val failed: Boolean = false
)

data class UserItemDataDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Played") val played: Boolean? = null,
    @SerializedName("Favorite") val favorite: Boolean? = null,
    @SerializedName("ItemId") val itemId: String? = null
)

// ==================== Session 远程控制相关数据模型 ====================

/**
 * Session 信息
 */
data class SessionInfo(
    val id: String,
    val deviceName: String,
    val deviceId: String,
    val client: String,
    val userName: String?,
    val userId: String?,
    val isActive: Boolean,
    val supportsMediaControl: Boolean,
    val playbackState: SessionPlaybackState?,
    val nowPlayingItem: SessionNowPlayingItem?
)

/**
 * Session 播放状态
 */
data class SessionPlaybackState(
    val positionTicks: Long,
    val isPaused: Boolean,
    val isMuted: Boolean,
    val volumeLevel: Int,
    val canSeek: Boolean,
    val playMethod: String?,
    val repeatMode: String
)

/**
 * Session 当前播放项目
 */
data class SessionNowPlayingItem(
    val id: String,
    val name: String,
    val album: String?,
    val artists: List<String>,
    val durationTicks: Long,
    val mediaType: String
)

/**
 * Session 命令请求
 */
data class SessionCommandRequest(
    @SerializedName("Cmd") val cmd: String,
    @SerializedName("Arguments") val arguments: Map<String, @JvmSuppressWildcards Any>? = null
)

/**
 * Session DTO
 */
data class SessionDto(
    @SerializedName("Id") val id: String?,
    @SerializedName("DeviceName") val deviceName: String?,
    @SerializedName("DeviceId") val deviceId: String?,
    @SerializedName("Client") val client: String?,
    @SerializedName("UserId") val userId: String?,
    @SerializedName("UserName") val userName: String?,
    @SerializedName("IsActive") val isActive: Boolean?,
    @SerializedName("SupportsMediaControl") val supportsMediaControl: Boolean?,
    @SerializedName("PlayState") val playState: PlayStateDto?,
    @SerializedName("NowPlayingItem") val nowPlayingItem: NowPlayingItemDto?
)

/**
 * PlayState DTO
 */
data class PlayStateDto(
    @SerializedName("PositionTicks") val positionTicks: Long?,
    @SerializedName("IsPaused") val isPaused: Boolean?,
    @SerializedName("IsMuted") val isMuted: Boolean?,
    @SerializedName("VolumeLevel") val volumeLevel: Int?,
    @SerializedName("CanSeek") val canSeek: Boolean?,
    @SerializedName("PlayMethod") val playMethod: String?,
    @SerializedName("RepeatMode") val repeatMode: String?
)

/**
 * NowPlayingItem DTO
 */
data class NowPlayingItemDto(
    @SerializedName("Id") val id: String?,
    @SerializedName("Name") val name: String?,
    @SerializedName("Album") val album: String?,
    @SerializedName("Artists") val artists: List<String>?,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long?,
    @SerializedName("MediaType") val mediaType: String?
)

/**
 * 登录请求
 */
data class AuthenticateRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Password") val password: String,
    @SerializedName("PasswordMd5") val passwordMd5: String
)

/**
 * 登录响应
 */
data class AuthenticateResponse(
    @SerializedName("User") val user: UserDto?,
    @SerializedName("SessionInfo") val session: SessionInfoDto?
)

/**
 * Session信息
 */
data class SessionInfoDto(
    @SerializedName("Id") val id: String?,
    @SerializedName("UserId") val userId: String?,
    @SerializedName("AccessToken") val accessToken: String?,
    @SerializedName("DeviceId") val deviceId: String?
)

/**
 * 登录结果
 */
data class LoginResult(
    val userId: String,
    val userName: String,
    val accessToken: String
)