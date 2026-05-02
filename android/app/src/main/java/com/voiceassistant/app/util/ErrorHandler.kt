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

package com.voiceassistant.app.util

import android.content.Context
import com.voiceassistant.app.R
import timber.log.Timber

object ErrorHandler {
    
    fun handle(context: Context, error: Throwable, silent: Boolean = false): String {
        Timber.e(error)
        
        val message = when (error) {
            is NetworkException -> "网络连接失败，请检查网络"
            is PermissionException -> "缺少必要权限"
            is ModelException -> "模型加载失败，请重新下载"
            is DLNAException -> "音箱连接失败"
            else -> "操作失败: ${error.localizedMessage ?: "未知错误"}"
        }
        
        if (!silent) {
            // Show error in UI or TTS
        }
        
        return message
    }
    
    fun getFallbackResponse(errorType: ErrorType): String {
        return when (errorType) {
            ErrorType.NETWORK -> listOf(
                "网络有点慢，稍后再试吧",
                "连不上服务器，先离线用着",
                "网络不好，我只会放歌了"
            ).random()
            
            ErrorType.RECOGNITION -> listOf(
                "没听清楚，请再说一遍",
                "声音有点小，靠近点说",
                "没听懂，换个说法试试"
            ).random()
            
            ErrorType.DEVICE -> listOf(
                "音箱没连上，检查一下",
                "找不到音箱，先配置一下"
            ).random()
            
            ErrorType.MODEL -> listOf(
                "模型还没准备好，稍等",
                "语音功能初始化中"
            ).random()
        }
    }
}

class NetworkException(message: String) : Exception(message)
class PermissionException(message: String) : Exception(message)
class ModelException(message: String) : Exception(message)
class DLNAException(message: String) : Exception(message)

enum class ErrorType {
    NETWORK, RECOGNITION, DEVICE, MODEL
}
