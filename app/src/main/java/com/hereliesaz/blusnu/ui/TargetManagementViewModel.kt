package com.hereliesaz.blusnu.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluetoothScanner
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.VulnerabilityCorrelator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class TargetManagementViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TargetManagementScreenState())
    val state: StateFlow<TargetManagementScreenState> = _state

    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val bluetoothScanner =
        bluetoothAdapter?.let {
            BluetoothScanner(application, deviceRepository, it) { macAddress, services ->
                onClassicServicesDiscovered(macAddress, services)
            }
        }

    private val vulnerabilityCorrelator = VulnerabilityCorrelator(application)

    init {
        combine(
            deviceRepository.discoveredDevices,
            _state
        ) { devices, state ->
            val filteredDevices = when (state.selectedFilter) {
                FilterProtocol.ALL -> devices
                FilterProtocol.CLASSIC -> devices.filter { it.protocol == com.hereliesaz.blusnu.data.Protocol.CLASSIC }
                FilterProtocol.BLE -> devices.filter { it.protocol == com.hereliesaz.blusnu.data.Protocol.BLE }
            }
            val sortedDevices = when (state.selectedSort) {
                SortOption.NONE -> filteredDevices
                SortOption.RSSI_ASC -> filteredDevices.sortedBy { it.rssi }
                SortOption.RSSI_DESC -> filteredDevices.sortedByDescending { it.rssi }
            }
            state.copy(devices = sortedDevices)
        }.onEach {
            _state.value = it
        }.launchIn(viewModelScope)

        updatePermissionsState()
        updateBluetoothState()
    }

    fun startScan() {
        if (_state.value.hasPermissions && _state.value.isBluetoothEnabled) {
            _state.update { it.copy(isScanning = true) }
            bluetoothScanner?.startClassicDiscovery()
            bluetoothScanner?.startBleScan()
        }
    }

    fun stopScan() {
        _state.update { it.copy(isScanning = false) }
        bluetoothScanner?.stopClassicDiscovery()
        bluetoothScanner?.stopBleScan()
    }

    fun updatePermissionsState() {
        _state.update { it.copy(hasPermissions = hasBluetoothPermissions()) }
    }

    fun updateBluetoothState() {
        _state.update { it.copy(isBluetoothEnabled = isBluetoothEnabled()) }
    }

    fun onFilterSelected(filter: FilterProtocol) {
        _state.update { it.copy(selectedFilter = filter) }
    }

    fun onSortSelected(sort: SortOption) {
        _state.update { it.copy(selectedSort = sort) }
    }

    fun discoverServices(device: com.hereliesaz.blusnu.data.TargetDevice) {
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.macAddress)
        bluetoothDevice?.let {
            when (device.protocol) {
                com.hereliesaz.blusnu.data.Protocol.CLASSIC -> bluetoothScanner?.fetchUuids(it)
                com.hereliesaz.blusnu.data.Protocol.BLE -> {
                    bluetoothScanner?.discoverGattServices(it) { services ->
                        deviceRepository.updateDeviceServices(device.macAddress, services)
                        checkForVulnerabilities(device.macAddress, services)
                    }
                }
                else -> {}
            }
        }
    }

    private fun checkForVulnerabilities(macAddress: String, services: List<String>) {
        val vulnerabilities = vulnerabilityCorrelator.checkForVulnerabilities(services)
        deviceRepository.updateDeviceVulnerabilities(macAddress, vulnerabilities)
    }

    fun onClassicServicesDiscovered(macAddress: String, services: List<String>) {
        checkForVulnerabilities(macAddress, services)
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getApplication<Application>().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    getApplication<Application>().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            getApplication<Application>().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }
}
