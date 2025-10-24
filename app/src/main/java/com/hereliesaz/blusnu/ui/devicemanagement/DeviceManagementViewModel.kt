package com.hereliesaz.blusnu.ui.devicemanagement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        deviceRepository.allDevices
            .onEach { devices ->
                _state.value = _state.value.copy(devices = devices)
            }
            .launchIn(viewModelScope)
    }

    fun updateDeviceNotes(device: TargetDevice, notes: String) {
        viewModelScope.launch {
            deviceRepository.updateNotes(device.macAddress, notes)
        }
    }
}

data class DeviceManagementScreenState(
    val devices: List<TargetDevice> = emptyList()
)
