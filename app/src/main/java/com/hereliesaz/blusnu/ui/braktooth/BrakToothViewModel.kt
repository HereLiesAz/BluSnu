package com.hereliesaz.blusnu.ui.braktooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BrakToothModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrakToothViewModel(
    private val deviceRepository: DeviceRepository,
    private val brakToothModule: BrakToothModule
) : ViewModel() {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    val devices: StateFlow<List<TargetDevice>> = deviceRepository.allDevices
        .combine(_selectedDevice) { devices, _ ->
            devices.filter { it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStatus: StateFlow<String> = brakToothModule.connectionStatus
    val logs: StateFlow<List<String>> = brakToothModule.logs

    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun connectHardware() {
        viewModelScope.launch {
            brakToothModule.connectToEsp32()
        }
    }

    fun startAttack() {
        val device = selectedDevice.value ?: return
        viewModelScope.launch {
            brakToothModule.runCrashTest(device.macAddress)
        }
    }
}
