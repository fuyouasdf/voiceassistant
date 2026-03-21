package com.voiceassistant.core.sherpa

import android.content.Context
import timber.log.Timber

class SherpaASRImpl : SherpaASR {

    override fun initialize(modelPath: String): Boolean {
        Timber.w("ASR stub - not implemented")
        return true
    }

    override suspend fun recognize(audio: FloatArray): String {
        return ""
    }

    override fun reset() {
    }

    override fun release() {
    }
}
