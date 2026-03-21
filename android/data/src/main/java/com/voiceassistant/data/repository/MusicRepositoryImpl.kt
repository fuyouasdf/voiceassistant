package com.voiceassistant.data.repository

import com.voiceassistant.data.remote.NavidromeApi
import com.voiceassistant.data.remote.Song
import com.voiceassistant.data.remote.SubsonicAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest

class MusicRepositoryImpl(
    private val api: NavidromeApi,
    private val settingsRepository: SettingsRepository
) : MusicRepository {
    
    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = settingsRepository.getNavidromeUrl()
            val username = settingsRepository.getNavidromeUsername()
            val password = settingsRepository.getNavidromePassword()
            
            if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("配置不完整"))
            }
            
            val (token, salt) = generateToken(password)
            val response = api.ping(username, token, salt)
            
            if (response.isSuccessful && response.body()?.response?.status == "ok") {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.response?.error?.message ?: "连接失败"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Test connection failed")
            Result.failure(e)
        }
    }
    
    override suspend fun searchSongs(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val username = settingsRepository.getNavidromeUsername()
            val password = settingsRepository.getNavidromePassword()
            val (token, salt) = generateToken(password)
            
            val response = api.search(username, token, salt, query)
            
            if (response.isSuccessful) {
                val songs = response.body()?.response?.searchResult3?.song ?: emptyList()
                Result.success(songs)
            } else {
                Result.failure(Exception("搜索失败"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Search failed")
            Result.failure(e)
        }
    }
    
    override suspend fun getStreamUrl(songId: String): String {
        val url = settingsRepository.getNavidromeUrl()
        val username = settingsRepository.getNavidromeUsername()
        val password = settingsRepository.getNavidromePassword()
        val (token, salt) = generateToken(password)
        
        return "$url/rest/stream?u=$username&t=$token&s=$salt&id=$songId&c=voice-assistant"
    }
    
    private fun generateToken(password: String): Pair<String, String> {
        val salt = generateSalt()
        val token = md5(password + salt)
        return Pair(token, salt)
    }
    
    private fun generateSalt(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
    
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

interface MusicRepository {
    suspend fun testConnection(): Result<Boolean>
    suspend fun searchSongs(query: String): Result<List<Song>>
    suspend fun getStreamUrl(songId: String): String
}
