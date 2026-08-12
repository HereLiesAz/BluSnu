package com.hereliesaz.blusnu.ui.smpbypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
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
                // SMP Bypass targets BLE pairing. Filter out Classic-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
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

        val taskId = "smpbypass_${System.currentTimeMillis()}"
        ActionLogger.log("SMP Bypass: Starting attack on ${device.name ?: device.macAddress}")
        ActiveTaskManager.add(taskId, "SMP Bypass", "Targeting ${device.name ?: device.macAddress}")

        viewModelScope.launch {
            try {
                smpBypassModule.startAttack(device).collect { log ->
                    _logs.value = _logs.value + log
                }
                // Log the final result summary (5F).
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("SMP Bypass: Finished attack on ${device.name ?: device.macAddress}")
                ActionLogger.log("Result: $resultSummary")
            } finally {
                _isRunning.value = false
                ActiveTaskManager.remove(taskId)
            }
        }
    }
}
