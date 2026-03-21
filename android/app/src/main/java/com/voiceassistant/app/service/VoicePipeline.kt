package com.voiceassistant.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder for VoicePipeline
 * Will be implemented in core module
 */
class VoicePipeline {
    
    private val _state = MutableStateFlow(PipelineState.IDLE)
    val state: StateFlow<PipelineState> = _state.asStateFlow()
    
    fun start() {
        _state.value = PipelineState.IDLE
    }
    
    fun stop() {
        _state.value = PipelineState.IDLE
    }
}

enum class PipelineState {
    IDLE,
    LISTENING,
    RECORDING,
    RECOGNIZING,
    THINKING,
    SPEAKING
}
