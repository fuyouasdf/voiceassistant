package com.voiceassistant.core.dlna

import timber.log.Timber

/**
 * DLNA controller for a specific DLNA device.
 * Provides play, pause, stop, and volume control via UPnP SOAP commands.
 *
 * @param device The DLNA device to control
 */
class DLNAController(private val device: DLNADevice) {

    /**
     * Play media at the given URL.
     * @param url The media stream URL
     * @param title Media title for display
     * @param artist Artist name
     */
    fun play(url: String, title: String, artist: String): Result<Unit> {
        Timber.d("DLNA play: $title by $artist on ${device.name}")

        // Set the transport URI first
        val setUriResult = DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "SetAVTransportURI",
            DLNASoapClient.buildSetTransportUriBody(url, title, artist)
        )
        if (setUriResult.isFailure) {
            return setUriResult
        }

        // Then play
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "Play",
            DLNASoapClient.buildPlayBody()
        )
    }

    /**
     * Pause current playback.
     */
    fun pause(): Result<Unit> {
        Timber.d("DLNA pause on ${device.name}")
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "Pause",
            DLNASoapClient.buildPauseBody()
        )
    }

    /**
     * Stop playback.
     */
    fun stop(): Result<Unit> {
        Timber.d("DLNA stop on ${device.name}")
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "Stop",
            DLNASoapClient.buildStopBody()
        )
    }

    /**
     * Set volume.
     * @param volume Volume level 0-100
     */
    fun setVolume(volume: Int): Result<Unit> {
        Timber.d("DLNA setVolume $volume on ${device.name}")
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.RENDERING_CONTROL_SERVICE,
            "SetVolume",
            DLNASoapClient.buildSetVolumeBody(volume.coerceIn(0, 100))
        )
    }
}