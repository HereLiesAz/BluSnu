package com.hereliesaz.blusnu.ui.perfektblue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.PerfektBlueModule
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for PerfektBlue.
 *
 * Interfaces with [PerfektBlueModule] to run auditing logic.
 */
class PerfektBlueViewModel(
    private val deviceRepository: DeviceRepository,
    private val perfektBlueModule: PerfektBlueModule
) : ViewModel() {

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
                // PerfektBlue targets Classic stack (IVI systems usually use Classic profiles).
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    /**
     * Starts the audit sequence.
     */
    fun startAudit() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val taskId = "perfektblue_${System.currentTimeMillis()}"
        ActionLogger.log("PerfektBlue: Starting audit on ${device.name ?: device.macAddress}")
        ActiveTaskManager.add(taskId, "PerfektBlue Audit", "Targeting ${device.name ?: device.macAddress}")

        viewModelScope.launch {
            try {
                perfektBlueModule.startAudit(device).collect { log ->
                    _logs.value = _logs.value + log
                }
                // Log the final result summary (5F).
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("PerfektBlue: Finished audit on ${device.name ?: device.macAddress}")
                ActionLogger.log("Result: $resultSummary")
            } finally {
                _isRunning.value = false
                ActiveTaskManager.remove(taskId)
            }
        }
    }
}
