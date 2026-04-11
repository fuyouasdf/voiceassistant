package com.voiceassistant.core.dlna

import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared DLNA SOAP client for sending UPnP SOAP commands to DLNA devices.
 * Both DLNAPlayer and DLNAController use this utility.
 */
object DLNASoapClient {

    // Cache for control URLs to avoid repeated device description XML fetches
    // Key: "$deviceLocation|$serviceType", Value: control URL
    private val controlUrlCache = ConcurrentHashMap<String, String>()

    /**
     * Clear the control URL cache (call when device list changes)
     */
    fun clearCache() {
        controlUrlCache.clear()
    }

    /**
     * Send a SOAP request to a DLNA device.
     */
    fun sendSoapRequest(
        device: DLNADevice,
        serviceType: String,
        action: String,
        soapBody: String
    ): Result<Unit> {
        return sendSoapRequestWithResponse(device, serviceType, action, soapBody).map { }
    }

    /**
     * Send a SOAP request and return the response body.
     */
    fun sendSoapRequestWithResponse(
        device: DLNADevice,
        serviceType: String,
        action: String,
        soapBody: String
    ): Result<String> {
        try {
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
                return Result.success(response.toString())
            } else {
                Timber.e("DLNA $action failed with code $responseCode: $response")
                return Result.failure(Exception("DLNA $action failed: $responseCode"))
            }
        } catch (e: Exception) {
            Timber.e(e, "DLNA $action failed")
            return Result.failure(e)
        }
    }

    /**
     * Get the control URL for a service type from the device description XML.
     * Results are cached to avoid repeated XML fetches.
     */
    fun getControlUrl(deviceLocation: String, serviceType: String): String? {
        val cacheKey = "$deviceLocation|$serviceType"

        // Check cache first
        controlUrlCache[cacheKey]?.let { return it }

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
            val serviceTypeShort = serviceType.substringAfterLast(":") // e.g., "AVTransport:1" -> "AVTransport:1"

            // Simple regex to find control URL
            val controlUrlRegex = Regex(
                "<service>.*?<serviceType>$serviceType</serviceType>.*?<controlURL>([^<]+)</controlURL>.*?</service>",
                RegexOption.DOT_MATCHES_ALL
            )

            val result = controlUrlRegex.find(xml)?.groupValues?.get(1)?.let { controlUrl ->
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

            // Cache the result if found
            result?.let { controlUrlCache[cacheKey] = it }

            return result
        } catch (e: Exception) {
            Timber.e(e, "Failed to get control URL from $deviceLocation")
            return null
        }
    }

    /**
     * Escape special XML characters in a string.
     */
    fun String.escapeXml(): String {
        return this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // SOAP action constants
    const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"

    /**
     * Build SetAVTransportURI SOAP body.
     */
    fun buildSetTransportUriBody(url: String, title: String, artist: String): String {
        return """
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
    }

    /**
     * Build Play SOAP body.
     */
    fun buildPlayBody(): String {
        return """
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
    }

    /**
     * Build Pause SOAP body.
     */
    fun buildPauseBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Pause>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    /**
     * Build Stop SOAP body.
     */
    fun buildStopBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    /**
     * Build SetVolume SOAP body.
     */
    fun buildSetVolumeBody(volume: Int): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                        <Volume>$volume</Volume>
                    </u:SetVolume>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    /**
     * Build GetTransportInfo SOAP body.
     */
    fun buildGetTransportInfoBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:GetTransportInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:GetTransportInfo>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    /**
     * Parse GetTransportInfo response to extract current playback state.
     * @return "PLAYING", "PAUSED_PLAYBACK", "STOPPED", or null if parsing fails
     */
    fun parseTransportInfoResponse(response: String): String? {
        // Look for TransportState element
        val regex = Regex("<TransportState>([^<]+)</TransportState>", RegexOption.IGNORE_CASE)
        return regex.find(response)?.groupValues?.get(1)?.trim()
    }

    /**
     * Build PlayPause SOAP body (for devices that support it).
     */
    fun buildPlayPauseBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:PlayPause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:PlayPause>
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }
}