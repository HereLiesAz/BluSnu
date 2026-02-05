package com.hereliesaz.blusnu.ui.devicemanagement

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluetoothScanner
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the scanning process and device list.
 */
class DeviceManagementViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val vulnerabilityCorrelator: com.hereliesaz.blusnu.data.VulnerabilityCorrelator,
    private val macLookupClient: com.hereliesaz.blusnu.data.MacLookupClient,
    private val bluetoothLog: com.hereliesaz.blusnu.data.BluetoothLog
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DeviceManagementScreenState())
    val state: StateFlow<DeviceManagementScreenState> = _state

    // System adapter.
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(application, BluetoothManager::class.java)
        bluetoothManager?.adapter
    }

    // Instance of the data layer scanner.
    private val bluetoothScanner: BluetoothScanner? by lazy {
        bluetoothAdapter?.let {
            BluetoothScanner(
                context = application,
                deviceRepository = deviceRepository,
                bluetoothAdapter = it,
                bluetoothLog = bluetoothLog
            )
        }
    }

    init {
        // Collect all devices and compute statistics for the UI.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { devices ->
                val devicesInCurrentScan = devices.filter { it.lastSeen >= _state.value.scanStartTime }
                val newDevicesInCurrentScan = devicesInCurrentScan.filter { device ->
                    // "New" means we haven't seen it before this scan session.
                    // This logic is slightly simplistic (relying on DB state), but sufficient.
                    devices.none { it.macAddress == device.macAddress && it.lastSeen < _state.value.scanStartTime }
                }
                _state.value = _state.value.copy(
                    devices = devices,
                    devicesInCurrentScan = devicesInCurrentScan.size,
                    newDevicesInCurrentScan = newDevicesInCurrentScan.size,
                    totalDevicesInDb = devices.size
                )
            }
        }
    }

    /**
     * Starts dual-mode scanning (Classic + BLE).
     */
    fun startScan() {
        viewModelScope.launch {
            bluetoothScanner?.startBleScan()
            bluetoothScanner?.startClassicDiscovery()
        }
        _state.value = _state.value.copy(isScanning = true, scanStartTime = System.currentTimeMillis())
    }

    /**
     * Stops all scanning.
     */
    fun stopScan() {
        bluetoothScanner?.stopBleScan()
        bluetoothScanner?.stopClassicDiscovery()
        _state.value = _state.value.copy(isScanning = false)
    }

    /**
     * Saves user notes to the DB.
     */
    fun updateDeviceNotes(device: TargetDevice, notes: String) {
        viewModelScope.launch {
            deviceRepository.updateNotes(device.macAddress, notes)
        }
    }

    /**
     * Toggles favorite status in DB.
     */
    fun toggleFavorite(device: TargetDevice) {
        viewModelScope.launch {
            deviceRepository.updateIsFavorite(device.macAddress, !device.isFavorite)
        }
    }

    /**
     * Selects a device for detail view and triggers OUI lookup.
     */
    fun onDeviceSelected(device: TargetDevice) {
        _state.value = _state.value.copy(selectedDevice = device)
        guessVendor(device)
    }

    /**
     * Performs network lookup for vendor name.
     */
    private fun guessVendor(device: TargetDevice) {
        viewModelScope.launch {
            val vendor = macLookupClient.getVendor(device.macAddress)
            _state.value = _state.value.copy(vendor = vendor)
        }
    }
}

data class DeviceManagementScreenState(
    val devices: List<TargetDevice> = emptyList(),
    val scanStartTime: Long = 0L,
    val isScanning: Boolean = false,
    val selectedDevice: TargetDevice? = null,
    val vendor: String? = null,
    val devicesInCurrentScan: Int = 0,
    val newDevicesInCurrentScan: Int = 0,
    val totalDevicesInDb: Int = 0
)
