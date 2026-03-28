package com.voiceassistant.core.dlna

import com.voiceassistant.domain.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DLNA player implementation that implements PlayerRepository.
 * Sends actual SOAP commands to DLNA devices for media playback control.
 */
@Singleton
class DLNAPlayer @Inject constructor(
    private val dlnaManager: DLNAManager
) : PlayerRepository {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentDevice = MutableStateFlow<DLNADevice?>(null)
    val currentDevice: StateFlow<DLNADevice?> = _currentDevice.asStateFlow()

    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var currentArtist: String? = null

    override suspend fun play(url: String, title: String, artist: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Ensure we have a device
            val device = getOrDiscoverDevice()
            if (device == null) {
                Timber.e("No DLNA device available")
                return@withContext Result.failure(Exception("没有可用的DLNA设备"))
            }

            Timber.d("DLNA play: $title by $artist on ${device.name}")

            // Set the transport URI first
            val setUriResult = setTransportURI(device, url, title, artist)
            if (setUriResult.isFailure) {
                return@withContext Result.failure(setUriResult.exceptionOrNull() ?: Exception("Failed to set URI"))
            }

            // Then play
            val playResult = play(device)
            if (playResult.isSuccess) {
                currentUrl = url
                currentTitle = title
                currentArtist = artist
                _isPlaying.value = true
            }

            playResult
        } catch (e: Exception) {
            Timber.e(e, "DLNA play failed")
            Result.failure(e)
        }
    }

    override suspend fun pause(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val device = _currentDevice.value ?: return@withContext Result.failure(Exception("No device connected"))
            pausePlayback(device)
            _isPlaying.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "DLNA pause failed")
            Result.failure(e)
        }
    }

    override suspend fun resume(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val device = _currentDevice.value ?: return@withContext Result.failure(Exception("No device connected"))
            play(device)
            _isPlaying.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "DLNA resume failed")
            Result.failure(e)
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val device = _currentDevice.value ?: return@withContext Result.failure(Exception("No device connected"))
            stopPlayback(device)
            _isPlaying.value = false
            currentUrl = null
            currentTitle = null
            currentArtist = null
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "DLNA stop failed")
            Result.failure(e)
        }
    }

    override suspend fun setVolume(volume: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val device = _currentDevice.value ?: return@withContext Result.failure(Exception("No device connected"))
            setVolume(device, volume.coerceIn(0, 100))
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "DLNA setVolume failed")
            Result.failure(e)
        }
    }

    override fun isPlayerAvailable(): Boolean {
        return _currentDevice.value != null
    }

    /**
     * Restore a previously saved DLNA device from settings.
     * Should be called on app startup to use the last selected device.
     */
    fun restoreDevice(name: String, ipAddress: String, uuid: String) {
        dlnaManager.restoreDevice(name, ipAddress, uuid)
        Timber.d("DLNAPlayer restored device: $name")
    }

    private suspend fun getOrDiscoverDevice(): DLNADevice? {
        // If we already have a device, use it
        _currentDevice.value?.let { return it }

        // Check if there is a restored/preselected device from settings
        val restoredDevice = dlnaManager.getCurrentDevice()
        if (restoredDevice != null) {
            _currentDevice.value = restoredDevice
            Timber.d("Using restored DLNA device: ${restoredDevice.name}")
            return restoredDevice
        }

        // Check if there are any discovered devices
        val devices = dlnaManager.devices.value
        if (devices.isNotEmpty()) {
            val device = devices.first()
            _currentDevice.value = device
            Timber.d("Using DLNA device: ${device.name}")
            return device
        }

        // Start discovery and wait briefly
        dlnaManager.startDiscovery()
        kotlinx.coroutines.delay(3000) // Wait for discovery

        val discoveredDevices = dlnaManager.devices.value
        if (discoveredDevices.isNotEmpty()) {
            val device = discoveredDevices.first()
            _currentDevice.value = device
            Timber.d("Discovered and using DLNA device: ${device.name}")
            return device
        }

        return null
    }

    private fun setTransportURI(device: DLNADevice, url: String, title: String, artist: String): Result<Unit> {
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "SetAVTransportURI",
            DLNASoapClient.buildSetTransportUriBody(url, title, artist)
        )
    }

    private fun play(device: DLNADevice): Result<Unit> {
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "Play",
            DLNASoapClient.buildPlayBody()
        )
    }

    private fun pausePlayback(device: DLNADevice): Result<Unit> {
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "Pause",
            DLNASoapClient.buildPauseBody()
        )
    }

    private fun stopPlayback(device: DLNADevice): Result<Unit> {
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.AV_TRANSPORT_SERVICE,
            "Stop",
            DLNASoapClient.buildStopBody()
        )
    }

    private fun setVolume(device: DLNADevice, volume: Int): Result<Unit> {
        return DLNASoapClient.sendSoapRequest(
            device,
            DLNASoapClient.RENDERING_CONTROL_SERVICE,
            "SetVolume",
            DLNASoapClient.buildSetVolumeBody(volume)
        )
    }
}