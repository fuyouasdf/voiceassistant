package com.voiceassistant.core.dlna

import com.voiceassistant.domain.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>${url.escapeXml()}</CurrentURI>
                        <CurrentURIMetaData><DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item><dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">${title.escapeXml()}</dc:title><dc:creator xmlns:dc="http://purl.org/dc/elements/1.1/">${artist.escapeXml()}</dc:creator><upnp:artist xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">${artist.escapeXml()}</upnp:artist><res>${url.escapeXml()}</res></item></DIDL-Lite></CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(device, "urn:schemas-upnp-org:service:AVTransport:1", "SetAVTransportURI", soapBody)
    }

    private fun play(device: DLNADevice): Result<Unit> {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(device, "urn:schemas-upnp-org:service:AVTransport:1", "Play", soapBody)
    }

    private fun pausePlayback(device: DLNADevice): Result<Unit> {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Pause>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(device, "urn:schemas-upnp-org:service:AVTransport:1", "Pause", soapBody)
    }

    private fun stopPlayback(device: DLNADevice): Result<Unit> {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(device, "urn:schemas-upnp-org:service:AVTransport:1", "Stop", soapBody)
    }

    private fun setVolume(device: DLNADevice, volume: Int): Result<Unit> {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                        <Volume>${volume}</Volume>
                    </u:SetVolume>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(device, "urn:schemas-upnp-org:service:RenderingControl:1", "SetVolume", soapBody)
    }

    private fun sendSoapRequest(
        device: DLNADevice,
        serviceType: String,
        action: String,
        soapBody: String
    ): Result<Unit> {
        try {
            // Get the control URL from device location
            val controlUrl = getControlUrl(device.location, serviceType)
            if (controlUrl == null) {
                Timber.e("Failed to get control URL for $serviceType")
                return Result.failure(Exception("Failed to get control URL"))
            }

            val url = URL(controlUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            connection.setRequestProperty("SOAPACTION", "\"$serviceType#$action\"")
            connection.setRequestProperty("User-Agent", "Android/1.0 UPnP/1.1 VoiceAssistant/1.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(soapBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val response = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
            }

            connection.disconnect()

            if (responseCode == 200) {
                Timber.d("DLNA $action succeeded")
                return Result.success(Unit)
            } else {
                Timber.e("DLNA $action failed with code $responseCode: $response")
                return Result.failure(Exception("DLNA $action failed: $responseCode"))
            }
        } catch (e: Exception) {
            Timber.e(e, "DLNA $action failed")
            return Result.failure(e)
        }
    }

    private fun getControlUrl(deviceLocation: String, serviceType: String): String? {
        try {
            val url = URL(deviceLocation)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val xml = connection.inputStream.use { stream ->
                stream.bufferedReader().use { it.readText() }
            }
            connection.disconnect()

            // Parse the XML to find the control URL for the service
            // Look for service type in the XML and extract its control URL
            val serviceTypeShort = serviceType.substringAfterLast(":") // e.g., "AVTransport:1" -> "AVTransport:1"

            // Simple regex to find control URL
            val controlUrlRegex = Regex(
                "<service>.*?<serviceType>$serviceType</serviceType>.*?<controlURL>([^<]+)</controlURL>.*?</service>",
                RegexOption.DOT_MATCHES_ALL
            )

            return controlUrlRegex.find(xml)?.groupValues?.get(1)?.let { controlUrl ->
                // Handle relative URLs
                if (controlUrl.startsWith("http")) {
                    controlUrl
                } else {
                    val baseUrl = "${url.protocol}://${url.host}:${url.port}"
                    if (controlUrl.startsWith("/")) {
                        "$baseUrl$controlUrl"
                    } else {
                        "$baseUrl/${controlUrl}"
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get control URL from $deviceLocation")
            return null
        }
    }

    private fun String.escapeXml(): String {
        return this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}