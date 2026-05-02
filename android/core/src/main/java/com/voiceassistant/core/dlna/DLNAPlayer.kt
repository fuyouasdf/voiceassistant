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

package com.voiceassistant.core.dlna

import com.voiceassistant.domain.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

            // Query current state first - some devices (like JBL Bar 800) may not respond correctly
            // to pause if already paused, or to play if already playing
            val controller = DLNAController(device)
            val stateResult = controller.getTransportState()
            val currentState = stateResult.getOrNull()
            Timber.d("DLNA pause: current state is $currentState")

            if (currentState == "PLAYING") {
                val pauseResult = pausePlayback(device)
                if (pauseResult.isFailure) {
                    Timber.e("DLNA pause failed: ${pauseResult.exceptionOrNull()?.message}")
                    _isPlaying.value = false
                    return@withContext pauseResult
                }
                _isPlaying.value = false
                Result.success(Unit)
            } else {
                // Already paused or stopped, just update local state
                _isPlaying.value = false
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "DLNA pause failed")
            Result.failure(e)
        }
    }

    override suspend fun resume(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val device = _currentDevice.value ?: return@withContext Result.failure(Exception("No device connected"))

            // Query current state first
            val controller = DLNAController(device)
            val stateResult = controller.getTransportState()
            val currentState = stateResult.getOrNull()
            Timber.d("DLNA resume: current state is $currentState")

            if (currentState != "PLAYING") {
                val playResult = play(device)
                if (playResult.isFailure) {
                    Timber.e("DLNA resume failed: ${playResult.exceptionOrNull()?.message}")
                    _isPlaying.value = false
                    return@withContext playResult
                }
                _isPlaying.value = true
                Result.success(Unit)
            } else {
                // Already playing, just update local state
                _isPlaying.value = true
                Result.success(Unit)
            }
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
        // If we already have a device, verify it's still usable
        val currentDeviceValue = _currentDevice.value
        if (currentDeviceValue != null) {
            // Verify device is still reachable before using
            if (isDeviceReachable(currentDeviceValue)) {
                return currentDeviceValue
            } else {
                // Device no longer reachable, clear it and discover a new one
                Timber.w("Current DLNA device ${currentDeviceValue.name} is no longer reachable")
                _currentDevice.value = null
                _isPlaying.value = false
            }
        }

        // Check if there is a restored/preselected device from settings
        val restoredDevice = dlnaManager.getCurrentDevice()
        if (restoredDevice != null) {
            // Verify restored device is still reachable
            if (isDeviceReachable(restoredDevice)) {
                _currentDevice.value = restoredDevice
                Timber.d("Using restored DLNA device: ${restoredDevice.name}")
                return restoredDevice
            } else {
                // Restored device is offline, will trigger re-discovery via DLNAManager callback
                Timber.w("Restored DLNA device ${restoredDevice.name} is not reachable")
                _currentDevice.value = null
            }
        }

        // Check if there are any discovered devices
        val devices = dlnaManager.devices.value
        if (devices.isNotEmpty()) {
            val device = devices.first()
            _currentDevice.value = device
            Timber.d("Using DLNA device: ${device.name}")
            return device
        }

        // Start discovery and wait for devices using flow-based approach
        // DLNAManager.startDiscovery() takes ~8 seconds, so we need to wait that long
        dlnaManager.startDiscovery()

        // Wait for first device from discovery, with timeout
        val discoveredDevice = withTimeoutOrNull(10000) {
            dlnaManager.devices.first { it.isNotEmpty() }.firstOrNull()
        }

        if (discoveredDevice != null) {
            _currentDevice.value = discoveredDevice
            Timber.d("Discovered and using DLNA device: ${discoveredDevice.name}")
            return discoveredDevice
        }

        return null
    }

    /**
     * Check if a device is still reachable on the network
     */
    private fun isDeviceReachable(device: DLNADevice): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(device.ipAddress, 1900), 1500)
            socket.close()
            true
        } catch (e: Exception) {
            // Try HTTP port as fallback
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(device.ipAddress, 80), 1500)
                socket.close()
                true
            } catch (e2: Exception) {
                false
            }
        }
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

    /**
     * Release all resources held by the DLNA player.
     * Call this when DLNA is disabled or app terminates.
     */
    fun release() {
        try {
            // Stop any current playback
            _currentDevice.value?.let { device ->
                try {
                    stopPlayback(device)
                } catch (e: Exception) {
                    Timber.w(e, "Error stopping playback during release")
                }
            }

            // Clear state
            _isPlaying.value = false
            _currentDevice.value = null
            currentUrl = null
            currentTitle = null
            currentArtist = null

            // Release the DLNA manager resources
            dlnaManager.release()

            Timber.d("DLNAPlayer released successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing DLNAPlayer")
        }
    }
}