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

    fun updateDeviceServices(macAddress: String, services: List<String>) {
        val currentList = _discoveredDevices.value
        val deviceIndex = currentList.indexOfFirst { it.macAddress == macAddress }
        if (deviceIndex != -1) {
            val updatedDevice = currentList[deviceIndex].copy(services = services)
            _discoveredDevices.value = currentList.toMutableList().apply {
                this[deviceIndex] = updatedDevice
            }
        }
    }

    fun updateDeviceVulnerabilities(macAddress: String, vulnerabilities: List<Vulnerability>) {
        val currentList = _discoveredDevices.value
        val deviceIndex = currentList.indexOfFirst { it.macAddress == macAddress }
        if (deviceIndex != -1) {
            val updatedDevice = currentList[deviceIndex].copy(vulnerabilities = vulnerabilities)
            _discoveredDevices.value = currentList.toMutableList().apply {
                this[deviceIndex] = updatedDevice
            }
        }
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }
}