package com.voiceassistant.domain.usecase

import com.voiceassistant.core.pipeline.VoicePipeline
import javax.inject.Inject

class StartVoicePipelineUseCase @Inject constructor(
    private val voicePipeline: VoicePipeline
) {
    operator fun invoke() {
        voicePipeline.start()
    }
}
