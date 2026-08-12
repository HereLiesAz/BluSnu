package com.hereliesaz.blusnu.ui.devicemanagement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluetoothScanner
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.FilterProtocol
import com.hereliesaz.blusnu.ui.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the scanning process and device list.
 */
class DeviceManagementViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val vulnerabilityCorrelator: com.hereliesaz.blusnu.data.VulnerabilityCorrelator,
    private val macLookupClient: com.hereliesaz.blusnu.data.MacLookupClient,
    private val bluetoothScanner: BluetoothScanner
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DeviceManagementScreenState())
    val state: StateFlow<DeviceManagementScreenState> = _state

    // --- Sort & Filter (5B) ---
    private val _sortOption = MutableStateFlow(SortOption.NONE)
    val sortOption: StateFlow<SortOption> = _sortOption

    private val _filterProtocol = MutableStateFlow(FilterProtocol.ALL)
    val filterProtocol: StateFlow<FilterProtocol> = _filterProtocol

    /** Changes the sort order for the device list. */
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        recomputeVisibleDevices()
    }

    /** Changes the protocol filter for the device list. */
    fun setFilterProtocol(filter: FilterProtocol) {
        _filterProtocol.value = filter
        recomputeVisibleDevices()
    }

    /**
     * Re-derives [DeviceManagementScreenState.devices] from the latest raw list
     * by applying the current filter and sort.
     */
    private fun recomputeVisibleDevices() {
        val filtered = when (_filterProtocol.value) {
            FilterProtocol.ALL -> latestDevices
            FilterProtocol.CLASSIC -> latestDevices.filter { it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL }
            FilterProtocol.BLE -> latestDevices.filter { it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL }
        }
        val sorted = when (_sortOption.value) {
            SortOption.NONE -> filtered
            SortOption.RSSI_ASC -> filtered.sortedBy { it.rssi }
            SortOption.RSSI_DESC -> filtered.sortedByDescending { it.rssi }
        }
        _state.value = _state.value.copy(devices = sorted)
    }

    // Snapshot of the MAC addresses that were already in the DB at the moment the current
    // scan started. A device is "new in this scan" only if its MAC is NOT in this snapshot.
    // Because the DB stores exactly one row per MAC (upsert), we cannot detect "new" by
    // querying lastSeen on the single row, so we compare against this scan-start snapshot.
    private var macsSeenBeforeScan: Set<String> = emptySet()

    // Latest devices list observed from the repository, used to take the snapshot on scan start.
    private var latestDevices: List<TargetDevice> = emptyList()

    init {
        // Collect all devices and compute statistics for the UI.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { devices ->
                latestDevices = devices

                // Correlate each device's advertised services against the vulnerability DB
                // and populate the runtime-only vulnerabilities field.
                devices.forEach { device ->
                    device.vulnerabilities = vulnerabilityCorrelator.findVulnerabilities(device.services)
                }

                val scanStartTime = _state.value.scanStartTime
                val devicesInCurrentScan = devices.filter { it.lastSeen >= scanStartTime }
                // "New" means the MAC was not present in the DB when this scan started.
                val newDevicesInCurrentScan = devicesInCurrentScan.filter { device ->
                    device.macAddress !in macsSeenBeforeScan
                }
                _state.value = _state.value.copy(
                    devicesInCurrentScan = devicesInCurrentScan.size,
                    newDevicesInCurrentScan = newDevicesInCurrentScan.size,
                    totalDevicesInDb = devices.size
                )
                // Apply current sort/filter to update the visible device list.
                recomputeVisibleDevices()
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
        // Snapshot the MACs already known before this scan so newly discovered devices
        // can be counted correctly.
        macsSeenBeforeScan = latestDevices.map { it.macAddress }.toSet()
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
