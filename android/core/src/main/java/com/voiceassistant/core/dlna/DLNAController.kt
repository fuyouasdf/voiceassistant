package com.voiceassistant.core.dlna

import timber.log.Timber

class DLNAController(private val device: DLNADevice) {

    fun play(url: String, title: String, artist: String) {
        Timber.w("DLNA stub - play not implemented: $title by $artist")
    }

    fun pause() {
        Timber.w("DLNA stub - pause not implemented")
    }

    fun stop() {
        Timber.w("DLNA stub - stop not implemented")
    }

    fun setVolume(volume: Int) {
        Timber.w("DLNA stub - setVolume not implemented: $volume")
    }
}
