package com.hereliesaz.blusnu.ui.bluffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BluffsViewModel(private val deviceRepository: DeviceRepository) : ViewModel() {
    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect {
                _devices.value = it
            }
        }
    }
}
