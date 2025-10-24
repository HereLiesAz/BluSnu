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

class DeviceManagementViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val vulnerabilityCorrelator: com.hereliesaz.blusnu.data.VulnerabilityCorrelator
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DeviceManagementScreenState())
    val state: StateFlow<DeviceManagementScreenState> = _state

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(application, BluetoothManager::class.java)
        bluetoothManager?.adapter
    }

    private val bluetoothScanner: BluetoothScanner? by lazy {
        bluetoothAdapter?.let {
            BluetoothScanner(
                context = application,
                deviceRepository = deviceRepository,
                bluetoothAdapter = it,
                coroutineScope = viewModelScope
            )
        }
    }

    init {
        deviceRepository.allDevices
            .onEach { devices ->
                _state.value = _state.value.copy(devices = devices)
            }
            .launchIn(viewModelScope)
    }

    fun startScan() {
        bluetoothScanner?.startBleScan()
        bluetoothScanner?.startClassicDiscovery()
        _state.value = _state.value.copy(isScanning = true, scanStartTime = System.currentTimeMillis())
    }

    fun stopScan() {
        bluetoothScanner?.stopBleScan()
        bluetoothScanner?.stopClassicDiscovery()
        _state.value = _state.value.copy(isScanning = false)
    }

    fun updateDeviceNotes(device: TargetDevice, notes: String) {
        viewModelScope.launch {
            deviceRepository.updateNotes(device.macAddress, notes)
        }
    }
}

data class DeviceManagementScreenState(
    val devices: List<TargetDevice> = emptyList(),
    val scanStartTime: Long = 0L,
    val isScanning: Boolean = false
)
