package com.voiceassistant.core.sherpa

import android.content.Context
import timber.log.Timber

class SherpaVADImpl : SherpaVAD {

    override fun initialize(modelPath: String): Boolean {
        Timber.w("VAD stub - not implemented")
        return true
    }

    override fun process(audio: FloatArray): Boolean {
        return audio.any { it > 0.01f }
    }

    override fun reset() {
    }

    override fun release() {
    }
}
