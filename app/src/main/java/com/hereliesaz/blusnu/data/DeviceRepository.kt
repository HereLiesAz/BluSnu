package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DeviceRepository {

    private val _discoveredDevices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TargetDevice>> = _discoveredDevices

    fun addDevice(device: TargetDevice) {
        if (_discoveredDevices.value.none { it.macAddress == device.macAddress }) {
            _discoveredDevices.value = _discoveredDevices.value + device
        }
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }
}