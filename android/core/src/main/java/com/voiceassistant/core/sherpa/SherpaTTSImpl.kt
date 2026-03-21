package com.voiceassistant.core.sherpa

import android.content.Context
import timber.log.Timber

class SherpaTTSImpl : SherpaTTS {

    override fun initialize(modelPath: String): Boolean {
        Timber.w("TTS stub - not implemented")
        return true
    }

    override fun synthesize(text: String): FloatArray {
        return FloatArray(0)
    }

    override fun stop() {
    }

    override fun release() {
    }
}
