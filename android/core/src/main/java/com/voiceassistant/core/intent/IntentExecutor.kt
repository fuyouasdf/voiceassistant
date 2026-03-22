package com.voiceassistant.core.intent

import com.voiceassistant.domain.model.Intent
import com.voiceassistant.domain.model.IntentType
import com.voiceassistant.domain.usecase.HandleChatUseCase
import com.voiceassistant.domain.usecase.HandleDeviceUseCase
import com.voiceassistant.domain.usecase.HandleMusicUseCase
import com.voiceassistant.domain.usecase.HandleQueryUseCase
import com.voiceassistant.domain.usecase.HandleVolumeUseCase
import timber.log.Timber

/**
 * Executes parsed intents by delegating to domain use cases.
 *
 * This class is the seam between core (parsing) and domain (business logic).
 * It does NOT contain business logic - only routing.
 *
 * Responsibilities:
 * - Receive parsed Intent
 * - Route to appropriate UseCase
 * - Return response string
 */
class IntentExecutor(
    private val handleMusicUseCase: HandleMusicUseCase,
    private val handleVolumeUseCase: HandleVolumeUseCase,
    private val handleDeviceUseCase: HandleDeviceUseCase,
    private val handleQueryUseCase: HandleQueryUseCase,
    private val handleChatUseCase: HandleChatUseCase
) {

    /**
     * Execute the given intent and return response text.
     */
    suspend fun execute(intent: Intent): String {
        Timber.d("Executing intent: $intent")

        return when (intent.type) {
            IntentType.MUSIC -> handleMusicUseCase.execute(intent)
            IntentType.VOLUME -> handleVolumeUseCase.execute(intent)
            IntentType.DEVICE -> handleDeviceUseCase.execute(intent)
            IntentType.QUERY -> handleQueryUseCase.execute(intent)
            IntentType.CHAT -> handleChatUseCase.execute(intent)
            IntentType.UNKNOWN -> "没听懂，请再说一遍"
        }
    }
}
