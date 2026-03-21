package com.voiceassistant.data.repository

import com.voiceassistant.data.local.ConfigDao
import com.voiceassistant.data.local.ConfigEntity
import com.voiceassistant.data.local.ConfigKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val configDao: ConfigDao
) : SettingsRepository {
    
    override suspend fun getString(key: String, default: String): String {
        return configDao.get(key)?.value ?: default
    }
    
    override suspend fun setString(key: String, value: String) {
        configDao.set(ConfigEntity(key = key, value = value))
    }
    
    override fun getStringFlow(key: String, default: String): Flow<String> {
        return configDao.getFlow(key).map { it?.value ?: default }
    }
    
    // Navidrome
    override suspend fun getNavidromeUrl(): String = getString(ConfigKeys.NAVIDROME_URL, "")
    override suspend fun setNavidromeUrl(url: String) = setString(ConfigKeys.NAVIDROME_URL, url)
    
    override suspend fun getNavidromeUsername(): String = getString(ConfigKeys.NAVIDROME_USERNAME, "")
    override suspend fun setNavidromeUsername(username: String) = setString(ConfigKeys.NAVIDROME_USERNAME, username)
    
    override suspend fun getNavidromePassword(): String = getString(ConfigKeys.NAVIDROME_PASSWORD, "")
    override suspend fun setNavidromePassword(password: String) = setString(ConfigKeys.NAVIDROME_PASSWORD, password)
    
    // DLNA
    override suspend fun getDLNADeviceIp(): String = getString(ConfigKeys.DLNA_DEVICE_IP, "")
    override suspend fun setDLNADeviceIp(ip: String) = setString(ConfigKeys.DLNA_DEVICE_IP, ip)
    
    // LLM
    override suspend fun getLLMBaseUrl(): String = getString(ConfigKeys.LLM_BASE_URL, "https://api.deepseek.com")
    override suspend fun setLLMBaseUrl(url: String) = setString(ConfigKeys.LLM_BASE_URL, url)
    override suspend fun getLLMApiKey(): String = getString(ConfigKeys.LLM_API_KEY, "")
    override suspend fun setLLMApiKey(key: String) = setString(ConfigKeys.LLM_API_KEY, key)
    override suspend fun getLLMModel(): String = getString(ConfigKeys.LLM_MODEL, "deepseek-chat")
    override suspend fun setLLMModel(model: String) = setString(ConfigKeys.LLM_MODEL, model)

    // Voice Settings
    override suspend fun getWakeSensitivity(): Float = getString(ConfigKeys.WAKE_SENSITIVITY, "0.5").toFloatOrNull() ?: 0.5f
    override suspend fun setWakeSensitivity(sensitivity: Float) = setString(ConfigKeys.WAKE_SENSITIVITY, sensitivity.toString())

    override suspend fun getTtsSpeed(): Float = getString(ConfigKeys.TTS_SPEED, "1.0").toFloatOrNull() ?: 1.0f
    override suspend fun setTtsSpeed(speed: Float) = setString(ConfigKeys.TTS_SPEED, speed.toString())
}

interface SettingsRepository {
    suspend fun getString(key: String, default: String = ""): String
    suspend fun setString(key: String, value: String)
    fun getStringFlow(key: String, default: String = ""): Flow<String>
    
    // Navidrome
    suspend fun getNavidromeUrl(): String
    suspend fun setNavidromeUrl(url: String)
    suspend fun getNavidromeUsername(): String
    suspend fun setNavidromeUsername(username: String)
    suspend fun getNavidromePassword(): String
    suspend fun setNavidromePassword(password: String)
    
    // DLNA
    suspend fun getDLNADeviceIp(): String
    suspend fun setDLNADeviceIp(ip: String)
    
    // LLM
    suspend fun getLLMBaseUrl(): String
    suspend fun setLLMBaseUrl(url: String)
    suspend fun getLLMApiKey(): String
    suspend fun setLLMApiKey(key: String)
    suspend fun getLLMModel(): String
    suspend fun setLLMModel(model: String)

    // Voice Settings
    suspend fun getWakeSensitivity(): Float
    suspend fun setWakeSensitivity(sensitivity: Float)
    suspend fun getTtsSpeed(): Float
    suspend fun setTtsSpeed(speed: Float)
}
