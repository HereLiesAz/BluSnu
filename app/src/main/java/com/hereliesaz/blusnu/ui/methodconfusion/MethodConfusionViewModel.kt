package com.hereliesaz.blusnu.ui.methodconfusion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.ConfusionMode
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.MethodConfusionModule
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Method Confusion Attack.
 *
 * Bridges the UI and the [MethodConfusionModule]. Manages the attack lifecycle
 * including cancellation and resource cleanup.
 */
class MethodConfusionViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val methodConfusionModule = MethodConfusionModule()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

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
                // Method Confusion affects both BR/EDR (SSP) and BLE (SMP).
                // Include Classic, BLE, and Dual devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC ||
                    it.protocol == Protocol.BLE ||
                    it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    /**
     * Returns a display name for a device, using the MAC address as fallback
     * when the name is null.
     */
    private fun deviceDisplayName(device: TargetDevice): String {
        return device.name ?: "Unknown Device (${device.macAddress})"
    }

    /**
     * Executes the Method Confusion attack.
     */
    fun startAttack(mode: ConfusionMode = ConfusionMode.PASSKEY_VS_NUMERIC) {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val taskId = "methodconfusion_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("Method Confusion Attack: Starting on $displayName (mode: ${mode.name})")
        ActiveTaskManager.add(taskId, "Method Confusion Attack", "Targeting $displayName")

        attackJob = viewModelScope.launch {
            try {
                methodConfusionModule.startAttack(device, mode).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("Method Confusion Attack: Finished on $displayName")
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
    }

    /**
     * Cancels any running attack and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
