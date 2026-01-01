package com.hereliesaz.blusnu.ui.bluffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluffsMode
import com.hereliesaz.blusnu.data.BluffsModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BluffsViewModel(
    private val deviceRepository: DeviceRepository,
    private val bluffsModule: BluffsModule
) : ViewModel() {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedMode = MutableStateFlow(BluffsMode.A1_SPOOF_LSC_CENTRAL)
    val selectedMode: StateFlow<BluffsMode> = _selectedMode

    val devices: StateFlow<List<TargetDevice>> = deviceRepository.allDevices
        .combine(_selectedDevice) { devices, _ ->
            devices.filter { it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<String>> = bluffsModule.logs

    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun onModeSelected(mode: BluffsMode) {
        _selectedMode.value = mode
    }

    fun startAttack() {
        val device = selectedDevice.value ?: return
        viewModelScope.launch {
            bluffsModule.runAttack(device.macAddress, selectedMode.value)
        }
    }
}
