package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceRepository {
    private val _discoveredDevices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    fun addDevice(device: TargetDevice) {
        if (_discoveredDevices.value.any { it.macAddress == device.macAddress }) {
            updateDevice(device)
        } else {
            _discoveredDevices.value = _discoveredDevices.value + device
        }
    }

    fun updateDevice(device: TargetDevice) {
        _discoveredDevices.value = _discoveredDevices.value.map {
            if (it.macAddress == device.macAddress) device else it
        }
    }
}