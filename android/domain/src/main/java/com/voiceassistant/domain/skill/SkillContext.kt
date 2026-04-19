package com.voiceassistant.domain.skill

import com.voiceassistant.domain.repository.ChatContextProvider
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.PlaylistRepository
import com.voiceassistant.domain.usecase.HandleChatUseCase
import com.voiceassistant.domain.usecase.HandleDeviceUseCase
import com.voiceassistant.domain.usecase.HandleMusicUseCase
import com.voiceassistant.domain.usecase.HandleQueryUseCase
import com.voiceassistant.domain.usecase.HandleVolumeUseCase

/**
 * Context passed to skills when handling input.
 * Provides access to domain repositories and use cases.
 */
data class SkillContext(
    val musicRepository: MusicRepository?,
    val playlistRepository: PlaylistRepository?,
    val llmRepository: LLMRepository?,
    val chatContextProvider: ChatContextProvider?,
    val handleMusicUseCase: HandleMusicUseCase?,
    val handleVolumeUseCase: HandleVolumeUseCase?,
    val handleDeviceUseCase: HandleDeviceUseCase?,
    val handleQueryUseCase: HandleQueryUseCase?,
    val handleChatUseCase: HandleChatUseCase?,
    val savedSessionId: String?
) {
    companion object {
        const val LOCAL_DEVICE_SESSION_ID = "__local_device_session__"
    }

    /**
     * Check if the session ID is the local device session.
     */
    fun isLocalSession(sessionId: String?): Boolean {
        return sessionId == LOCAL_DEVICE_SESSION_ID
    }
}