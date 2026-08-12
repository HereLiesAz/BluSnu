package com.hereliesaz.blusnu.ui.sweyntooth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.SweynToothModule
import com.hereliesaz.blusnu.data.SweynToothVector
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for SweynTooth BLE Link Layer attack screen.
 *
 * Bridges the UI and the [SweynToothModule]. Manages the attack lifecycle
 * including hardware connection awareness, vector selection, cancellation,
 * and resource cleanup.
 */
class SweynToothViewModel(
    application: Application,
    private val hardwareManager: HardwareManager,
    private val sweynToothModule: SweynToothModule,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    companion object {
        /** Maximum number of log entries kept in memory. */
        private const val MAX_LOG_ENTRIES = 1000
    }

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedVector = MutableStateFlow<SweynToothVector?>(null)
    val selectedVector: StateFlow<SweynToothVector?> = _selectedVector

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _hardwareConnected = MutableStateFlow(false)
    val hardwareConnected: StateFlow<Boolean> = _hardwareConnected

    /** Reference to the running attack job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        // Observe available BLE devices.
        deviceRepository.allDevices
            .onEach { allDevices ->
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
            .launchIn(viewModelScope)

        // Observe hardware connection state.
        hardwareManager.hardwareState
            .onEach { state ->
                _hardwareConnected.value =
                    state == HardwareState.CONNECTED_BTLEJACK || state == HardwareState.CONNECTED_DUAL
            }
            .launchIn(viewModelScope)

        // Observe hardware logs and append to local log buffer.
        hardwareManager.deviceLogs
            .onEach { log ->
                val currentLogs = _logs.value
                val updatedLogs = if (currentLogs.size >= MAX_LOG_ENTRIES) {
                    currentLogs.drop(1) + log
                } else {
                    currentLogs + log
                }
                _logs.value = updatedLogs
            }
            .launchIn(viewModelScope)
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectVector(vector: SweynToothVector) {
        _selectedVector.value = vector
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
     * Executes a single SweynTooth vector attack against the selected device.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        val vector = _selectedVector.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val taskId = "sweyntooth_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("SweynTooth: Starting ${vector.description} on $displayName")
        ActiveTaskManager.add(taskId, "SweynTooth Attack", "Targeting $displayName with ${vector.vectorId}")

        attackJob = viewModelScope.launch {
            try {
                sweynToothModule.startAttack(device, vector).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("SweynTooth: Finished ${vector.vectorId} on $displayName")
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
     * Executes all SweynTooth vectors sequentially against the selected device.
     */
    fun startFullSuite() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val taskId = "sweyntooth_suite_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("SweynTooth: Starting full suite on $displayName")
        ActiveTaskManager.add(taskId, "SweynTooth Full Suite", "Targeting $displayName")

        attackJob = viewModelScope.launch {
            try {
                sweynToothModule.startFullSuite(device).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("SweynTooth: Full suite finished on $displayName")
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
     * Stops the running attack.
     */
    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        sweynToothModule.stopAttack()
    }

    /**
     * Cancels any running attack and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        sweynToothModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
