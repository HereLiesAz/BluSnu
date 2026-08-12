package com.hereliesaz.blusnu.ui.androidbtrce

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.AndroidBtRceModule
import com.hereliesaz.blusnu.data.AndroidRceVector
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Android BT Stack RCE assessment.
 *
 * Bridges the UI and the [AndroidBtRceModule]. Manages the assessment lifecycle
 * including cancellation and resource cleanup.
 */
class AndroidBtRceViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val androidBtRceModule = AndroidBtRceModule()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedVector = MutableStateFlow(AndroidRceVector.GATT_SERVER_OVERFLOW)
    val selectedVector: StateFlow<AndroidRceVector> = _selectedVector

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /** Reference to the running assessment job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // Android BT RCE targets BR/EDR + BLE (HCI / ACL / GATT).
                // All protocol types are eligible.
                _devices.value = allDevices
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectVector(vector: AndroidRceVector) {
        _selectedVector.value = vector
    }

    /**
     * Returns a display name for a device, using the MAC address as fallback
     * when the name is null.
     */
    private fun deviceDisplayName(device: TargetDevice): String {
        return device.name ?: "Unknown Device (${device.macAddress})"
    }

    /**
     * Executes the RCE vulnerability assessment.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val vector = _selectedVector.value
        val taskId = "androidbtrce_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("Android BT RCE: Starting ${vector.cve} assessment on $displayName")
        ActiveTaskManager.add(taskId, "Android BT RCE", "${vector.cve} on $displayName")

        attackJob = viewModelScope.launch {
            try {
                androidBtRceModule.startAttack(device, vector).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("Android BT RCE: Finished assessment on $displayName")
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
     * Stops the running assessment.
     */
    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        viewModelScope.launch {
            androidBtRceModule.stopAttack()
        }
    }

    /**
     * Cancels any running assessment and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
