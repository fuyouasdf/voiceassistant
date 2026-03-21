package com.voiceassistant.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.core.pipeline.PipelineState
import com.voiceassistant.core.pipeline.VoicePipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val voicePipeline: VoicePipeline
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            voicePipeline.state.collect { stateInfo ->
                _uiState.value = _uiState.value.copy(
                    pipelineState = stateInfo.state,
                    stateText = getStateText(stateInfo.state)
                )
            }
        }
    }
    
    fun onManualTrigger() {
        // Simulate wake word detection
        viewModelScope.launch {
            // Trigger listening state
        }
    }
    
    private fun getStateText(state: PipelineState): String {
        return when (state) {
            PipelineState.IDLE -> "待机中"
            PipelineState.LISTENING -> "请说话"
            PipelineState.RECORDING -> "正在录音"
            PipelineState.RECOGNIZING -> "正在识别"
            PipelineState.THINKING -> "正在思考"
            PipelineState.SPEAKING -> "正在播报"
        }
    }
}

data class MainUiState(
    val pipelineState: PipelineState = PipelineState.IDLE,
    val stateText: String = "待机中",
    val memoryWarning: Boolean = false
)
