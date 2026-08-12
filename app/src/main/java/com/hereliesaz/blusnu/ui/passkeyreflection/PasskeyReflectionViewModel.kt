package com.hereliesaz.blusnu.ui.passkeyreflection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.PasskeyReflectionModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Passkey Reflection MITM.
 *
 * Bridges the UI and the [PasskeyReflectionModule]. Manages the attack lifecycle
 * including cancellation and resource cleanup.
 *
 * Both BR/EDR (SSP) and BLE (SMP) devices are eligible targets because the
 * passkey reflection attack applies to any connection using passkey-based
 * association during Secure Simple Pairing.
 */
class PasskeyReflectionViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val passkeyReflectionModule = PasskeyReflectionModule()

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
                // Passkey reflection affects both BR/EDR (SSP) and BLE (SMP).
                // All protocol types are eligible targets.
                _devices.value = allDevices
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
     * Executes the passkey reflection attack.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val taskId = "passkeyreflection_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("Passkey Reflection MITM: Starting on $displayName")
        ActiveTaskManager.add(taskId, "Passkey Reflection MITM", "Targeting $displayName")

        attackJob = viewModelScope.launch {
            try {
                passkeyReflectionModule.startAttack(device).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("Passkey Reflection MITM: Finished on $displayName")
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
        viewModelScope.launch {
            passkeyReflectionModule.stopAttack()
        }
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
