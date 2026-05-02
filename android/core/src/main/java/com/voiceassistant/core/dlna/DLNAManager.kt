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

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

class DLNAManager(private val context: Context) {

    private val _devices = MutableStateFlow<List<DLNADevice>>(emptyList())
    val devices: StateFlow<List<DLNADevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var discoveryJob: Job? = null
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val foundDevices = CopyOnWriteArrayList<DLNADevice>()

    // Preselected device (e.g., restored from saved settings on startup)
    private val _preselectedDevice = MutableStateFlow<DLNADevice?>(null)
    val preselectedDevice: StateFlow<DLNADevice?> = _preselectedDevice.asStateFlow()

    // Callback for device discovery
    var onDeviceFound: ((DLNADevice) -> Unit)? = null

    // Callback for device unavailability (when restored device becomes unreachable)
    var onDeviceUnavailable: ((DLNADevice, String) -> Unit)? = null

    // Current/restored device (used as the active DLNA device)
    private var _currentDevice: DLNADevice? = null

    // Device types to search for
    private val searchTargets = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:device:MediaRenderer:2",
        "urn:schemas-upnp-org:service:AVTransport:1",
        "urn:schemas-upnp-org:service:RenderingControl:1",
        "ssdp:all"
    )

    fun startDiscovery() {
        if (_isScanning.value) {
            Timber.w("DLNA discovery already in progress")
            return
        }

        _isScanning.value = true
        foundDevices.clear()
        _devices.value = emptyList()

        discoveryJob = discoveryScope.launch {
            try {
                // Send multiple M-SEARCH requests with different targets and delays
                sendMultipleSSDPDiscoveries()

                // Wait for responses for 3 seconds (reduced from 8)
                delay(3000)

                // Update devices list
                _devices.value = foundDevices.toList()
                Timber.d("DLNA discovery complete. Found ${foundDevices.size} devices")
            } catch (e: Exception) {
                Timber.e(e, "DLNA discovery failed")
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun sendMultipleSSDPDiscoveries() {
        // Send M-SEARCH for each target type with shorter delays
        for (target in searchTargets) {
            sendSSDPDiscover(target)
            delay(300) // Wait 300ms between requests
        }

        // Send additional ssdp:all requests at intervals to catch slow devices
        repeat(2) { index ->
            delay(800)
            if (_isScanning.value) {
                sendSSDPDiscover("ssdp:all")
                Timber.d("Sent additional M-SEARCH #${index + 1}")
            }
            // Early exit if we already found at least one device
            if (foundDevices.isNotEmpty()) {
                Timber.d("Early exit from discovery - found ${foundDevices.size} device(s)")
                return
            }
        }
    }

    private fun sendSSDPDiscover(searchTarget: String = "ssdp:all") {
        var socket: DatagramSocket? = null
        var multicastSocket: java.net.MulticastSocket? = null
        try {
            // Enable multicast for receiving broadcast advertisements
            multicastSocket = java.net.MulticastSocket(1900)
            multicastSocket?.joinGroup(InetAddress.getByName("239.255.255.250"))
            multicastSocket?.soTimeout = 100

            // SSDP M-SEARCH message
            val searchMessage = """M-SEARCH * HTTP/1.1
HOST: 239.255.255.250:1900
MAN: "ssdp:discover"
MX: 5
ST: $searchTarget
USER-AGENT: Android/1.0 UPnP/1.1 VoiceAssistant/1.0

"""

            val buffer = searchMessage.toByteArray()
            val address = InetAddress.getByName("239.255.255.250")
            val packet = DatagramPacket(buffer, buffer.size, address, 1900)

            // Send from multiple ports/broadcasts to ensure reachability
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 8000

            // Send multiple times to different addresses
            socket.send(packet)

            // Also send to the broadcast address
            try {
                val broadcastPacket = DatagramPacket(buffer, buffer.size, InetAddress.getByName("255.255.255.255"), 1900)
                socket.send(broadcastPacket)
            } catch (e: Exception) {
                Timber.w("Failed to send broadcast: ${e.message}")
            }

            Timber.d("SSDP M-SEARCH sent for target: $searchTarget")

            // Listen for responses
            val responseBuffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 8000 && _isScanning.value) {
                try {
                    val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                    socket.receive(responsePacket)

                    val sourceIp = responsePacket.address.hostAddress?.let { formatHostAddress(it) } ?: continue
                    val response = String(responsePacket.data, 0, responsePacket.length)
                    parseSSDPResponse(response, sourceIp)
                } catch (e: Exception) {
                    // Timeout or error, continue listening
                }

                // Also check multicast socket for advertisements
                try {
                    val mcPacket = DatagramPacket(responseBuffer, responseBuffer.size)
                    multicastSocket?.receive(mcPacket)
                    val mcSourceIp = mcPacket.address.hostAddress?.let { formatHostAddress(it) } ?: continue
                    val mcResponse = String(mcPacket.data, 0, mcPacket.length)
                    parseSSDPResponse(mcResponse, mcSourceIp)
                } catch (e: Exception) {
                    // No multicast data available
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during SSDP discovery for target: $searchTarget")
        } finally {
            try {
                multicastSocket?.leaveGroup(InetAddress.getByName("239.255.255.250"))
            } catch (e: Exception) { }
            multicastSocket?.close()
            socket?.close()
        }
    }

    private fun parseSSDPResponse(response: String, sourceIp: String) {
        val lines = response.lines()
        var usn: String? = null
        var location: String? = null
        var server: String? = null
        var st: String? = null
        var nt: String? = null

        for (line in lines) {
            when {
                line.startsWith("USN:", ignoreCase = true) -> usn = line.substringAfter("USN:").trim()
                line.startsWith("LOCATION:", ignoreCase = true) -> location = line.substringAfter("LOCATION:").trim()
                line.startsWith("SERVER:", ignoreCase = true) -> server = line.substringAfter("SERVER:").trim()
                line.startsWith("ST:", ignoreCase = true) -> st = line.substringAfter("ST:").trim()
                line.startsWith("NT:", ignoreCase = true) -> nt = line.substringAfter("NT:").trim()
            }
        }

        // Check if this is a valid UPnP device with location
        if (location == null) return

        // Use ST or NT as the service type
        val serviceType = st ?: nt ?: ""

        // More permissive filter - accept MediaRenderer, MediaServer, and other common UPnP devices
        val isMediaDevice = serviceType.contains("MediaRenderer", ignoreCase = true) ||
                           serviceType.contains("MediaServer", ignoreCase = true) ||
                           serviceType.contains("AVTransport", ignoreCase = true) ||
                           serviceType.contains("RenderingControl", ignoreCase = true) ||
                           serviceType.contains("ConnectionManager", ignoreCase = true) ||
                           serviceType.contains("urn:schemas-upnp-org:device:", ignoreCase = true) ||
                           serviceType.contains("device:Media", ignoreCase = true)

        // Also accept ssdp:all responses that have location
        if (!isMediaDevice && !serviceType.contains("ssdp:all", ignoreCase = true)) {
            return
        }

        val uuid = usn?.substringAfter("uuid:")?.substringBefore(":") ?: usn?.substringAfter("uuid:") ?: "unknown-${sourceIp}"

        // Skip if already found
        if (foundDevices.any { it.uuid == uuid }) return

        // Launch coroutine to fetch device description
        discoveryScope.launch {
            try {
                val deviceInfo = fetchDeviceDescription(location)
                val device = DLNADevice(
                    uuid = uuid,
                    name = deviceInfo.friendlyName ?: extractDeviceNameFromServer(server) ?: "DLNA Device ($sourceIp)",
                    manufacturer = deviceInfo.manufacturer ?: extractManufacturer(server) ?: "",
                    model = deviceInfo.modelName ?: "",
                    location = location,
                    ipAddress = sourceIp
                )

                foundDevices.add(device)
                _devices.value = foundDevices.toList()
                onDeviceFound?.invoke(device)
                Timber.d("Found DLNA device: ${device.name} (${device.uuid})")
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch device description from: $location")
                // Add device with basic info even if description fetch fails
                val device = DLNADevice(
                    uuid = uuid,
                    name = extractDeviceNameFromServer(server) ?: "DLNA Device ($sourceIp)",
                    manufacturer = extractManufacturer(server) ?: "",
                    model = "",
                    location = location,
                    ipAddress = sourceIp
                )
                foundDevices.add(device)
                _devices.value = foundDevices.toList()
                onDeviceFound?.invoke(device)
            }
        }
    }

    private fun extractDeviceNameFromServer(server: String?): String? {
        if (server == null) return null
        // Try to extract device name from SERVER header like "UPnP/1.0 DLNADOC/1.50"
        val parts = server.split("/", " ")
        return parts.firstOrNull { it.isNotBlank() && it.length > 2 }
    }

    private fun extractManufacturer(server: String?): String? {
        if (server == null) return null
        // Common manufacturers
        return when {
            server.contains("Windows", ignoreCase = true) -> "Microsoft"
            server.contains("Linux", ignoreCase = true) -> "Linux"
            server.contains("Samsung", ignoreCase = true) -> "Samsung"
            server.contains("Sony", ignoreCase = true) -> "Sony"
            server.contains("LG", ignoreCase = true) -> "LG"
            server.contains("Panasonic", ignoreCase = true) -> "Panasonic"
            else -> server.substringBefore("/").takeIf { it.isNotBlank() }
        }
    }

    private data class DeviceDescription(
        val friendlyName: String?,
        val manufacturer: String?,
        val modelName: String?
    )

    private fun fetchDeviceDescription(locationUrl: String): DeviceDescription {
        val url = java.net.URL(locationUrl)
        val connection = url.openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val xml = connection.inputStream.use { stream ->
            stream.bufferedReader().use { it.readText() }
        }

        return parseDeviceDescription(xml)
    }

    private fun parseDeviceDescription(xml: String): DeviceDescription {
        // Parse XML for device information
        val friendlyName = extractXmlValue(xml, "friendlyName")
        val manufacturer = extractXmlValue(xml, "manufacturer")
        val modelName = extractXmlValue(xml, "modelName")

        return DeviceDescription(friendlyName, manufacturer, modelName)
    }

    private fun extractXmlValue(xml: String, tagName: String): String? {
        val regex = Regex("<$tagName>([^<]+)</$tagName>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groups?.get(1)?.value?.trim()
    }

    /**
     * Format host address for display. IPv6 addresses need brackets.
     */
    private fun formatHostAddress(address: String): String {
        return if (address.contains(":")) {
            "[$address]"
        } else {
            address
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _isScanning.value = false
        _devices.value = emptyList()
        Timber.d("DLNA discovery stopped")
    }

    /**
     * Restore a previously saved device (e.g., from settings).
     * This device will be used first before starting discovery.
     * Validates device reachability before use.
     */
    fun restoreDevice(name: String, ipAddress: String, uuid: String) {
        val device = DLNADevice(
            uuid = uuid,
            name = name,
            ipAddress = ipAddress
        )
        _preselectedDevice.value = device
        _currentDevice = device
        Timber.d("DLNA device restored: $name ($uuid) at $ipAddress")

        // Validate device is still reachable in background
        validateDeviceReachability(device)
    }

    /**
     * Validate if a device is still reachable on the network.
     * @return true if device responds to ping or TCP connection attempt
     */
    private fun validateDeviceReachability(device: DLNADevice) {
        discoveryScope.launch {
            val isReachable = checkDeviceTcpConnection(device.ipAddress)
            if (!isReachable) {
                Timber.w("DLNA device ${device.name} is not reachable at ${device.ipAddress}")
                onDeviceUnavailable?.invoke(device, "设备${device.name}已离线，正在重新搜索...")
                // Trigger re-discovery since saved device is unavailable
                startDiscovery()
            } else {
                Timber.d("DLNA device ${device.name} is still reachable at ${device.ipAddress}")
            }
        }
    }

    /**
     * Check if device is reachable via TCP connection to port 1900 (UPnP) or port 80/443 (HTTP)
     */
    private suspend fun checkDeviceTcpConnection(ipAddress: String): Boolean {
        return withContext(Dispatchers.IO) {
            val portsToCheck = listOf(80, 443, 1900)
            for (port in portsToCheck) {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ipAddress, port), 2000)
                    socket.close()
                    Timber.d("Device $ipAddress reachable on port $port")
                    return@withContext true
                } catch (e: Exception) {
                    // Try next port
                }
            }
            // If all ports fail, try ping as last resort
            return@withContext checkPing(ipAddress)
        }
    }

    /**
     * Check if device responds to ICMP ping
     */
    private fun checkPing(ipAddress: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 $ipAddress")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Timber.w(e, "Ping check failed for $ipAddress")
            false
        }
    }

    fun getCurrentDevice(): DLNADevice? = _currentDevice

    fun getDeviceById(uuid: String): DLNADevice? {
        return _devices.value.find { it.uuid == uuid }
    }

    fun release() {
        stopDiscovery()
        discoveryScope.cancel()
        _devices.value = emptyList()
    }
}

data class DLNADevice(
    val uuid: String,
    val name: String,
    val manufacturer: String = "",
    val model: String = "",
    val location: String = "",
    val ipAddress: String = ""
)
