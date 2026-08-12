package com.hereliesaz.blusnu.ui.l2capfuzzing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.L2capFuzzMode
import com.hereliesaz.blusnu.data.L2capFuzzingModule
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for L2CAP Stateful Fuzzing.
 *
 * Bridges the UI and the [L2capFuzzingModule]. Manages the fuzzing lifecycle
 * including device filtering, mode selection, cancellation, and resource cleanup.
 */
class L2capFuzzingViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val l2capFuzzingModule = L2capFuzzingModule()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedMode = MutableStateFlow(L2capFuzzMode.CONNECTION_REQUEST_FUZZ)
    val selectedMode: StateFlow<L2capFuzzMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /** Reference to the running fuzzing job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // L2CAP fuzzing targets BR/EDR (Classic). Filter out BLE-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectMode(mode: L2capFuzzMode) {
        _selectedMode.value = mode
    }

    /**
     * Returns a display name for a device, using the MAC address as fallback
     * when the name is null.
     */
    private fun deviceDisplayName(device: TargetDevice): String {
        return device.name ?: "Unknown Device (${device.macAddress})"
    }

    /**
     * Starts the L2CAP fuzzing session.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val taskId = "l2capfuzz_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("L2CAP Fuzzing: Starting ${_selectedMode.value.name} on $displayName")
        ActiveTaskManager.add(taskId, "L2CAP Fuzzing", "${_selectedMode.value.name} on $displayName")

        attackJob = viewModelScope.launch {
            try {
                l2capFuzzingModule.startFuzzing(device, _selectedMode.value).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("L2CAP Fuzzing: Finished on $displayName")
                ActionLogger.log("Result: $resultSummary")
            } finally {
                _isRunning.value = false
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
                attackJob = null
            }
        }
    }

    /**
     * Stops the running fuzzing session.
     */
    fun stopAttack() {
        l2capFuzzingModule.stopFuzzing()
        attackJob?.cancel()
        attackJob = null
    }

    /**
     * Cancels any running fuzzing session and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        l2capFuzzingModule.stopFuzzing()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
