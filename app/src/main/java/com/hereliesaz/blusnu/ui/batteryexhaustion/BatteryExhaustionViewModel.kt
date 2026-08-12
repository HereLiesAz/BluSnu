package com.hereliesaz.blusnu.ui.batteryexhaustion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.BatteryExhaustionModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.ExhaustionMode
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for BLE Battery Exhaustion.
 *
 * Bridges the UI and the [BatteryExhaustionModule]. Manages the attack lifecycle
 * including mode selection, cancellation, and resource cleanup.
 */
class BatteryExhaustionViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val batteryExhaustionModule = BatteryExhaustionModule(application)

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedMode = MutableStateFlow(ExhaustionMode.CONNECTION_FLOOD)
    val selectedMode: StateFlow<ExhaustionMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /** Reference to the running attack job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // Battery Exhaustion targets BLE devices. Filter out Classic-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectMode(mode: ExhaustionMode) {
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
     * Executes the battery exhaustion attack.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val mode = _selectedMode.value
        val taskId = "batteryexhaustion_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("BLE Battery Exhaustion: Starting ${mode.displayName} on $displayName")
        ActiveTaskManager.add(taskId, "BLE Battery Exhaustion", "Targeting $displayName (${mode.displayName})")

        attackJob = viewModelScope.launch {
            try {
                batteryExhaustionModule.startAttack(device, mode).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("BLE Battery Exhaustion: Finished on $displayName")
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
        batteryExhaustionModule.stopAttack()
        attackJob?.cancel()
        attackJob = null
    }

    /**
     * Cancels any running attack and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        batteryExhaustionModule.close()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
