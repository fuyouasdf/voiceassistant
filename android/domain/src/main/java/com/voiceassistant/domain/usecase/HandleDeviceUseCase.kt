package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.Intent
import javax.inject.Inject

/**
 * Use case for handling device control commands (lights, AC, etc).
 */
class HandleDeviceUseCase @Inject constructor() {

    fun execute(intent: Intent): String {
        return when (intent.action) {
            "on" -> "已打开设备"
            "off" -> "已关闭设备"
            "toggle" -> "已切换设备状态"
            else -> "设备操作"
        }
    }
}
