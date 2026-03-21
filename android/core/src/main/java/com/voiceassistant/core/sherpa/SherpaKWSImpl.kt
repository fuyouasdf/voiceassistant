package com.voiceassistant.core.sherpa

import android.content.Context
import timber.log.Timber

class SherpaKWSImpl : SherpaKWS {

    override fun initialize(modelPath: String): Boolean {
        Timber.w("KWS stub - not implemented")
        return true
    }

    override fun process(audio: FloatArray): Boolean {
        return false
    }

    override fun setSensitivity(sensitivity: Float) {
    }

    override fun release() {
    }
}
