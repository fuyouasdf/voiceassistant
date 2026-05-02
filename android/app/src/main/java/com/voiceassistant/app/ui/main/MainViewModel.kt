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
        viewModelScope.launch {
            try {
                voicePipeline.interrupt()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    stateText = "启动失败"
                )
            }
        }
    }

    private fun getStateText(state: PipelineState): String {
        return when (state) {
            PipelineState.INITIALIZING -> "准备中"
            PipelineState.IDLE -> "待机中"
            PipelineState.WAKEWORD_DETECTED -> "唤醒成功"
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