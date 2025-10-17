package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DeviceRepository {

    private val _discoveredDevices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TargetDevice>> = _discoveredDevices

    fun addDevice(device: TargetDevice) {
        val currentDevices = _discoveredDevices.value
        if (currentDevices.none { it.macAddress == device.macAddress }) {
            _discoveredDevices.value = currentDevices + device
        }
    }

    fun updateDevice(device: TargetDevice) {
        val currentDevices = _discoveredDevices.value
        val index = currentDevices.indexOfFirst { it.macAddress == device.macAddress }
        if (index != -1) {
            val updatedDevices = currentDevices.toMutableList()
            updatedDevices[index] = device
            _discoveredDevices.value = updatedDevices
        }
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }
}