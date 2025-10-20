package com.hereliesaz.blusnu.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluetoothScanner
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.data.VulnerabilityCorrelator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TargetManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val deviceRepository = DeviceRepository()
    private val bluetoothScanner =
        bluetoothAdapter?.let { BluetoothScanner(application, deviceRepository, it) }
    private val vulnerabilityCorrelator = VulnerabilityCorrelator(application)

    private val _filter = MutableStateFlow<Protocol?>(null)
    val filter: StateFlow<Protocol?> = _filter

    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText

    val filteredDevices: StateFlow<List<TargetDevice>> =
        combine(
            deviceRepository.discoveredDevices,
            _filter,
            _filterText
        ) { devices, filter, filterText ->
            devices.filter { device ->
                (filter == null || device.protocol == filter) &&
                        (filterText.isBlank() ||
                                device.name?.contains(filterText, ignoreCase = true) == true ||
                                device.macAddress.contains(filterText, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var hasPermissions = false
    var isBluetoothEnabled = false

    fun startScan() {
        if (hasPermissions && isBluetoothEnabled) {
            deviceRepository.clearDevices()
            bluetoothScanner?.startClassicDiscovery()
            bluetoothScanner?.startBleScan()
        }
    }

    fun stopScan() {
        bluetoothScanner?.stopClassicDiscovery()
        bluetoothScanner?.stopBleScan()
    }

    fun setFilter(protocol: Protocol?) {
        _filter.value = protocol
    }

    fun setFilterText(text: String) {
        _filterText.value = text
    }

    fun discoverServices(device: TargetDevice) {
        bluetoothAdapter?.let { adapter ->
            val bluetoothDevice = adapter.getRemoteDevice(device.macAddress)
            bluetoothScanner?.discoverServices(bluetoothDevice)
        }
    }

    fun checkForVulnerabilities(device: TargetDevice) {
        val vulnerabilities = vulnerabilityCorrelator.findVulnerabilities(device.services)
        deviceRepository.updateDeviceVulnerabilities(device.macAddress, vulnerabilities)
    }
}
