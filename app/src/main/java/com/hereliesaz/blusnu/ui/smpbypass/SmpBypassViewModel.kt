package com.hereliesaz.blusnu.ui.smpbypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.SmpBypassModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for SMP Bypass functionality.
 *
 * Bridges the UI and the [SmpBypassModule].
 */
class SmpBypassViewModel(private val deviceRepository: DeviceRepository) : ViewModel() {
    private val smpBypassModule = SmpBypassModule()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // SMP Bypass targets Android devices (likely Classic/Dual or BLE).
                // Assuming BLE SMP for now, but CVE context might imply Classic.
                _devices.value = allDevices
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    /**
     * Executes the bypass attack.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        viewModelScope.launch {
            smpBypassModule.startAttack(device).collect { log ->
                _logs.value = _logs.value + log
            }
            _isRunning.value = false
        }
    }
}
