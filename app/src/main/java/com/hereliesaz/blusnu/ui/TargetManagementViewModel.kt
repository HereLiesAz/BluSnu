package com.hereliesaz.blusnu.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.BluetoothScanner
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.data.VulnerabilityCorrelator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TargetManagementScreenState(
    val isScanning: Boolean = false,
    val hasPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val discoveredDevices: List<TargetDevice> = emptyList(),
    val activeFilters: Map<FilterType, Any> = emptyMap(),
    val showServiceDialog: Boolean = false,
    val showVulnerabilityDialog: Boolean = false,
    val logMessages: List<String> = emptyList()
)

sealed class FilterType {
    object Protocol : FilterType()
    object SignalStrength : FilterType()
    object Text : FilterType()
}

class TargetManagementViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val vulnerabilityCorrelator: VulnerabilityCorrelator
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TargetManagementScreenState())
    val state: StateFlow<TargetManagementScreenState>

    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val bluetoothScanner =
        bluetoothAdapter?.let {
            BluetoothScanner(application, deviceRepository, it) { macAddress, _ ->
                deviceRepository.getDevice(macAddress)?.let(::checkForVulnerabilities)
            }
        }

    private val _activeFilters = MutableStateFlow<Map<FilterType, Any>>(emptyMap())

    init {
        val filteredDevices: StateFlow<List<TargetDevice>> = combine(
            deviceRepository.discoveredDevices,
            _activeFilters
        ) { devices, filters ->
            devices.filter { device ->
                filters.all { (filterType, value) ->
                    when (filterType) {
                        is FilterType.Protocol -> device.protocol == value
                        is FilterType.SignalStrength -> device.rssi >= value as Int
                        is FilterType.Text -> device.name?.contains(value as String, ignoreCase = true) == true ||
                                device.macAddress.contains(value as String, ignoreCase = true)
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        state = combine(
            _state,
            filteredDevices,
            _activeFilters
        ) { state, devices, filters ->
            state.copy(
                discoveredDevices = devices,
                activeFilters = filters
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TargetManagementScreenState())

        updatePermissionsState()
        updateBluetoothState()
    }

    private fun updatePermissionsState() {
        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        _state.update { it.copy(hasPermissions = hasPermissions) }
    }

    private fun updateBluetoothState() {
        _state.update { it.copy(isBluetoothEnabled = bluetoothAdapter?.isEnabled == true) }
    }

    fun addFilter(filterType: FilterType, value: Any) {
        _activeFilters.update { it + (filterType to value) }
    }

    fun removeFilter(filterType: FilterType) {
        _activeFilters.update { it - filterType }
    }

    fun startScan() {
        if (_state.value.hasPermissions && _state.value.isBluetoothEnabled) {
            ActionLogger.log("Bluetooth scan started.")
            _state.update { it.copy(isScanning = true) }
            deviceRepository.clearDevices()
            bluetoothScanner?.startClassicDiscovery()
            bluetoothScanner?.startBleScan()
        }
    }

    fun stopScan() {
        _state.update { it.copy(isScanning = false) }
        bluetoothScanner?.stopClassicDiscovery()
        bluetoothScanner?.stopBleScan()
    }

    fun discoverServices(device: TargetDevice) {
        viewModelScope.launch {
            _state.update { it.copy(showServiceDialog = true, logMessages = emptyList()) }
            addLogMessage("Starting service discovery for ${device.macAddress}")
            bluetoothAdapter?.let { adapter ->
                val bluetoothDevice = adapter.getRemoteDevice(device.macAddress)
                bluetoothScanner?.discoverServices(bluetoothDevice)
            }
            delay(2000) // Simulate network delay
            _state.update { it.copy(showServiceDialog = false) }
        }
    }

    fun checkForVulnerabilities(device: TargetDevice) {
        viewModelScope.launch {
            _state.update { it.copy(showVulnerabilityDialog = true, logMessages = emptyList()) }
            addLogMessage("Checking vulnerabilities for ${device.macAddress}")
            ActionLogger.log("Checking vulnerabilities for ${device.macAddress}.")
            val vulnerabilities = vulnerabilityCorrelator.findVulnerabilities(device.services)
            deviceRepository.updateDeviceVulnerabilities(device.macAddress, vulnerabilities)
            addLogMessage("Found ${vulnerabilities.size} vulnerabilities.")
            delay(2000) // Simulate network delay
            _state.update { it.copy(showVulnerabilityDialog = false) }
        }
    }

    private fun addLogMessage(message: String) {
        _state.update {
            val newLogs = it.logMessages + message
            it.copy(logMessages = newLogs.takeLast(10))
        }
    }
}
