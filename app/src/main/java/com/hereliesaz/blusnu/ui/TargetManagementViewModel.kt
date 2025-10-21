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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TargetManagementScreenState(
    val isScanning: Boolean = false,
    val hasPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val discoveredDevices: List<TargetDevice> = emptyList(),
    val activeFilters: Map<FilterType, Any> = emptyMap(),
    val isDiscoveringServices: Set<String> = emptySet(),
    val isCheckingVulnerabilities: Set<String> = emptySet()
)

sealed class FilterType {
    object Protocol : FilterType()
    object SignalStrength : FilterType()
    object Text : FilterType()
    object Favorites : FilterType()
}

class TargetManagementViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
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
    private val vulnerabilityCorrelator = VulnerabilityCorrelator(application)

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
                        is FilterType.Favorites -> device.isFavorite
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
            _state.update { it.copy(isDiscoveringServices = it.isDiscoveringServices + device.macAddress) }
            bluetoothAdapter?.let { adapter ->
                val bluetoothDevice = adapter.getRemoteDevice(device.macAddress)
                bluetoothScanner?.discoverServices(bluetoothDevice)
            }
            _state.update { it.copy(isDiscoveringServices = it.isDiscoveringServices - device.macAddress) }
        }
    }

    fun checkForVulnerabilities(device: TargetDevice) {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingVulnerabilities = it.isCheckingVulnerabilities + device.macAddress) }
            val vulnerabilities = vulnerabilityCorrelator.findVulnerabilities(device.services)
            deviceRepository.updateDeviceVulnerabilities(device.macAddress, vulnerabilities)
            _state.update { it.copy(isCheckingVulnerabilities = it.isCheckingVulnerabilities - device.macAddress) }
        }
    }

    fun toggleFavorite(macAddress: String) {
        deviceRepository.toggleFavorite(macAddress)
    }

    fun saveNotes(macAddress: String, notes: String) {
        deviceRepository.updateDeviceNotes(macAddress, notes)
    }
}
