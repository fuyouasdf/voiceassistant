package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import javax.inject.Inject

/**
 * Use case for handling volume control commands.
 */
class HandleVolumeUseCase @Inject constructor() {

    fun execute(intent: Intent): String {
        return when (intent.action) {
            "set" -> "音量调到 ${intent.value}%"
            "up" -> "音量增加 ${intent.value}%"
            "down" -> "音量减少 ${intent.value}%"
            "mute" -> "已静音"
            else -> "音量操作"
        }
    }
}
