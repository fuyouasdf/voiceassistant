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

package com.voiceassistant.core.skill

import android.content.SharedPreferences
import com.voiceassistant.domain.repository.ChatContextProvider
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.domain.repository.MusicRepository
import com.voiceassistant.domain.repository.PlaylistRepository
import com.voiceassistant.domain.skill.Skill
import com.voiceassistant.domain.skill.SkillContext
import com.voiceassistant.domain.skill.SkillRegistry
import com.voiceassistant.domain.usecase.HandleChatUseCase
import com.voiceassistant.domain.usecase.HandleDeviceUseCase
import com.voiceassistant.domain.usecase.HandleMusicUseCase
import com.voiceassistant.domain.usecase.HandleQueryUseCase
import com.voiceassistant.domain.usecase.HandleVolumeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing skill-related dependencies.
 *
 * This module:
 * - Provides SkillContext with all necessary dependencies
 * - Registers built-in skills with SkillRegistry
 */
@Module
@InstallIn(SingletonComponent::class)
object SkillModule {

    private const val PREF_LAST_SESSION_ID = "jellyfin_selected_device_id"

    @Provides
    @Singleton
    fun provideSkillContext(
        musicRepository: MusicRepository?,
        playlistRepository: PlaylistRepository?,
        llmRepository: LLMRepository?,
        chatContextProvider: ChatContextProvider?,
        handleMusicUseCase: HandleMusicUseCase?,
        handleVolumeUseCase: HandleVolumeUseCase?,
        handleDeviceUseCase: HandleDeviceUseCase?,
        handleQueryUseCase: HandleQueryUseCase?,
        handleChatUseCase: HandleChatUseCase?,
        sharedPreferences: SharedPreferences?
    ): SkillContext {
        val savedSessionIdProvider = {
            sharedPreferences?.getString(PREF_LAST_SESSION_ID, null)?.takeIf { it.isNotBlank() }
        }
        return SkillContext(
            musicRepository = musicRepository,
            playlistRepository = playlistRepository,
            llmRepository = llmRepository,
            chatContextProvider = chatContextProvider,
            handleMusicUseCase = handleMusicUseCase,
            handleVolumeUseCase = handleVolumeUseCase,
            handleDeviceUseCase = handleDeviceUseCase,
            handleQueryUseCase = handleQueryUseCase,
            handleChatUseCase = handleChatUseCase,
            savedSessionIdProvider = savedSessionIdProvider
        )
    }

    @Provides
    @Singleton
    fun provideSkillRegistry(
        musicSkill: MusicSkill,
        volumeSkill: VolumeSkill,
        deviceSkill: DeviceSkill,
        querySkill: QuerySkill,
        chatSkill: ChatSkill
    ): SkillRegistry {
        val registry = SkillRegistry()
        // Register built-in skills (they will be sorted by priority)
        registry.register(musicSkill)
        registry.register(volumeSkill)
        registry.register(deviceSkill)
        registry.register(querySkill)
        registry.register(chatSkill)
        return registry
    }
}