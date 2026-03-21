package com.voiceassistant.core.dlna

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class DLNAManager(private val context: Context) {

    private val _devices = MutableStateFlow<List<DLNADevice>>(emptyList())
    val devices: StateFlow<List<DLNADevice>> = _devices.asStateFlow()

    fun startDiscovery() {
        Timber.w("DLNA stub - discovery not implemented")
    }

    fun stopDiscovery() {
        Timber.w("DLNA stub - stop not implemented")
    }

    fun getDeviceById(uuid: String): DLNADevice? {
        return _devices.value.find { it.uuid == uuid }
    }

    fun release() {
        _devices.value = emptyList()
    }
}

data class DLNADevice(
    val uuid: String,
    val name: String,
    val manufacturer: String = "",
    val model: String = ""
)
