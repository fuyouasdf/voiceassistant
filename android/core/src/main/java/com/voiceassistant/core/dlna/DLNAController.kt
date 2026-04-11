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
     * Get current transport/playback state.
     * @return "PLAYING", "PAUSED_PLAYBACK", "STOPPED", or null on error
     */
    fun getTransportState(): Result<String> {
        Timber.d("DLNA getTransportState on ${device.name}")
        val result = DLNASoapClient.sendSoapRequestWithResponse(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "GetTransportInfo",
            DLNASoapClient.buildGetTransportInfoBody()
        )
        return result.mapCatching { response ->
            DLNASoapClient.parseTransportInfoResponse(response)
                ?: throw Exception("Failed to parse transport state")
        }
    }

    /**
     * Toggle play/pause based on current state.
     * Uses PlayPause action if available, otherwise falls back to Pause/Play.
     */
    fun playPause(): Result<Unit> {
        Timber.d("DLNA playPause on ${device.name}")

        // First get current state
        val stateResult = getTransportState()
        val currentState = stateResult.getOrNull()
        Timber.d("Current DLNA transport state: $currentState")

        return if (currentState == "PLAYING") {
            // Currently playing, send Pause
            pause()
        } else if (currentState == "PAUSED_PLAYBACK") {
            // Currently paused, send Play to resume
            DLNASoapClient.sendSoapRequest(
                device,
                DLNASoapClient.AV_TRANSPORT_SERVICE,
                "Play",
                DLNASoapClient.buildPlayBody()
            )
        } else {
            // State is unknown (null) or "STOPPED" - try Pause first (safer).
            // If device is already paused or stopped, Pause is typically a no-op.
            // Only send Play if Pause fails and state was not STOPPED.
            pause().recoverCatching {
                if (currentState == "STOPPED") {
                    // Device was stopped, need to send Play to start
                    DLNASoapClient.sendSoapRequest(
                        device,
                        DLNASoapClient.AV_TRANSPORT_SERVICE,
                        "Play",
                        DLNASoapClient.buildPlayBody()
                    )
                } else {
                    // State unknown and Pause failed, rethrow original error
                    throw it
                }
            }
        }
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