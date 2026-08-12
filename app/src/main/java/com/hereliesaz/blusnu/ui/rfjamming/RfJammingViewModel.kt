package com.hereliesaz.blusnu.ui.rfjamming

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.JammingMode
import com.hereliesaz.blusnu.data.RfJammingModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for RF Jamming / Selective Denial.
 *
 * Bridges the UI and the [RfJammingModule]. Manages hardware connection state,
 * jamming mode selection, target device selection, and the jamming lifecycle
 * including cancellation and resource cleanup.
 */
class RfJammingViewModel(
    application: Application,
    private val hardwareManager: HardwareManager,
    private val rfJammingModule: RfJammingModule,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedMode = MutableStateFlow(JammingMode.BROADBAND_24GHZ)
    val selectedMode: StateFlow<JammingMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _hardwareConnected = MutableStateFlow(false)
    val hardwareConnected: StateFlow<Boolean> = _hardwareConnected

    /** Reference to the running jamming job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        // Observe all devices -- RF jamming can target any protocol (BR/EDR + BLE).
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _devices.value = allDevices
            }
        }

        // Observe hardware connection state.
        hardwareManager.hardwareState
            .map { state ->
                state == HardwareState.CONNECTED_BTLEJACK ||
                        state == HardwareState.CONNECTED_DUAL
            }
            .onEach { connected ->
                _hardwareConnected.value = connected
            }
            .launchIn(viewModelScope)
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectMode(mode: JammingMode) {
        _selectedMode.value = mode
    }

    /**
     * Returns a display name for a device, using the MAC address as fallback
     * when the name is null.
     */
    private fun deviceDisplayName(device: TargetDevice): String {
        return device.name ?: "Unknown Device (${device.macAddress})"
    }

    fun connectHardware() {
        viewModelScope.launch {
            hardwareManager.connect()
        }
    }

    fun disconnectHardware() {
        viewModelScope.launch {
            hardwareManager.disconnect()
        }
    }

    /**
     * Starts the RF jamming operation with the selected mode and optional target.
     */
    fun startAttack() {
        val mode = _selectedMode.value
        val device = _selectedDevice.value
        if (_isRunning.value) return

        // Targeted modes require a selected device
        val requiresTarget = mode == JammingMode.SELECTIVE_CHANNEL ||
                mode == JammingMode.HOPPING_SYNCHRONIZED
        if (requiresTarget && device == null) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = device?.let { deviceDisplayName(it) } ?: "Broadband"
        val taskId = "rfjamming_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("RF Jamming: Starting ${mode.name} on $displayName")
        ActiveTaskManager.add(taskId, "RF Jamming", "${mode.name} targeting $displayName")

        attackJob = viewModelScope.launch {
            try {
                rfJammingModule.startJamming(mode, device).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("RF Jamming: Finished on $displayName")
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
     * Stops the running jamming operation.
     */
    fun stopAttack() {
        rfJammingModule.stopJamming()
        attackJob?.cancel()
        attackJob = null
    }

    /**
     * Cancels any running operation and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        rfJammingModule.close()
    }
}
