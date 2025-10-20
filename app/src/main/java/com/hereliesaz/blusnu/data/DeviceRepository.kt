package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class DeviceRepository {

    private val _discoveredDevicesMap = MutableStateFlow<Map<String, TargetDevice>>(emptyMap())
    val discoveredDevices: kotlinx.coroutines.flow.Flow<List<TargetDevice>> = _discoveredDevicesMap.map { it.values.toList() }

    fun addDevice(device: TargetDevice) {
        if (!_discoveredDevicesMap.value.containsKey(device.macAddress)) {
            _discoveredDevicesMap.value = _discoveredDevicesMap.value + (device.macAddress to device)
        }
    }

    fun getDevice(macAddress: String): TargetDevice? {
        return _discoveredDevicesMap.value[macAddress]
    }

    fun clearDevices() {
        _discoveredDevicesMap.value = emptyMap()
    }

    fun updateDeviceServices(macAddress: String, services: List<String>) {
        _discoveredDevicesMap.value[macAddress]?.let {
            _discoveredDevicesMap.value = _discoveredDevicesMap.value + (macAddress to it.copy(services = services))
        }
    }

    fun updateDeviceVulnerabilities(macAddress: String, vulnerabilities: List<Vulnerability>) {
        _discoveredDevicesMap.value[macAddress]?.let {
            _discoveredDevicesMap.value = _discoveredDevicesMap.value + (macAddress to it.copy(vulnerabilities = vulnerabilities))
        }
    }
}
