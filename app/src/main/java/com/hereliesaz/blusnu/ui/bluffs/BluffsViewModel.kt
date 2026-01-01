package com.hereliesaz.blusnu.ui.bluffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluffsMode
import com.hereliesaz.blusnu.data.BluffsModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BluffsViewModel(private val deviceRepository: DeviceRepository) : ViewModel() {
    private val bluffsModule = BluffsModule()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedMode = MutableStateFlow(BluffsMode.A1)
    val selectedMode: StateFlow<BluffsMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // BLUFFS targets BR/EDR (Classic)
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectMode(mode: BluffsMode) {
        _selectedMode.value = mode
    }

    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        viewModelScope.launch {
            bluffsModule.startAttack(device, _selectedMode.value).collect { log ->
                _logs.value = _logs.value + log
            }
            _isRunning.value = false
        }
    }
}
