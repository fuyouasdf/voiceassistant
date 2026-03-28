package com.voiceassistant.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.remote.SessionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Jellyfin 远程控制页面 UI 状态
 */
data class JellyfinControlUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionInfo> = emptyList(),
    val selectedSession: SessionInfo? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Jellyfin 远程控制 ViewModel
 */
@HiltViewModel
class JellyfinControlViewModel @Inject constructor(
    private val jellyfinClient: JellyfinClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinControlUiState())
    val uiState: StateFlow<JellyfinControlUiState> = _uiState.asStateFlow()

    init {
        refreshSessions()
        // 定时刷新会话状态
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(3000)
                refreshSessions()
            }
        }
    }

    /**
     * 刷新会话列表
     */
    fun refreshSessions() {
        viewModelScope.launch {
            try {
                val sessions = jellyfinClient.getSessions()
                _uiState.update { state ->
                    // 保持当前选中的会话
                    val selectedSession = state.selectedSession?.let { selected ->
                        sessions.find { it.id == selected.id }
                    }
                    state.copy(
                        sessions = sessions,
                        selectedSession = selectedSession,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "刷新会话失败")
                _uiState.update { it.copy(error = "刷新失败: ${e.message}", isLoading = false) }
            }
        }
    }

    /**
     * 选择会话
     */
    fun selectSession(session: SessionInfo) {
        _uiState.update { it.copy(selectedSession = session, message = "已选择: ${session.deviceName}") }
    }

    /**
     * 播放（恢复播放）
     */
    fun play() {
        val sessionId = _uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = jellyfinClient.play(sessionId)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, message = "播放") }
                refreshSessions()
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "播放失败: ${it.message}") }
            }
        }
    }

    /**
     * 暂停
     */
    fun pause() {
        val sessionId = _uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = jellyfinClient.pause(sessionId)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, message = "已暂停") }
                refreshSessions()
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "暂停失败: ${it.message}") }
            }
        }
    }

    /**
     * 停止
     */
    fun stop() {
        val sessionId = _uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = jellyfinClient.stop(sessionId)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, message = "已停止") }
                refreshSessions()
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "停止失败: ${it.message}") }
            }
        }
    }

    /**
     * 上一曲
     */
    fun previousTrack() {
        val sessionId = _uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = jellyfinClient.previousTrack(sessionId)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, message = "上一曲") }
                refreshSessions()
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "上一曲失败: ${it.message}") }
            }
        }
    }

    /**
     * 下一曲
     */
    fun nextTrack() {
        val sessionId = _uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = jellyfinClient.nextTrack(sessionId)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, message = "下一曲") }
                refreshSessions()
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "下一曲失败: ${it.message}") }
            }
        }
    }

    /**
     * 跳转位置
     */
    fun seek(positionMs: Long) {
        val sessionId = _uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            val result = jellyfinClient.seek(sessionId, positionMs)
            result.onSuccess {
                _uiState.update { it.copy(message = "已跳转") }
                refreshSessions()
            }.onFailure {
                _uiState.update { it.copy(error = "跳转失败: ${it.message}") }
            }
        }
    }

    /**
     * 播放指定项目
     */
    fun playItem(itemId: String, startPositionMs: Long = 0) {
        val sessionId = _uiState.value.selectedSession?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = jellyfinClient.playItem(sessionId, itemId, startPositionMs)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, message = "开始播放") }
                refreshSessions()
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "播放失败: ${it.message}") }
            }
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}