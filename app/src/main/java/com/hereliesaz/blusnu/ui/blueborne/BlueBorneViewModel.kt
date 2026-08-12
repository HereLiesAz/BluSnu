package com.hereliesaz.blusnu.ui.blueborne

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.BlueBorneModule
import com.hereliesaz.blusnu.data.BlueBorneVector
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the BlueBorne attack screen.
 *
 * Bridges the UI and the [BlueBorneModule]. Manages device selection, vector
 * selection, attack lifecycle, and cancellation/cleanup. When no vector is
 * selected the module runs a full vulnerability assessment across all four CVEs.
 */
class BlueBorneViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val blueBorneModule = BlueBorneModule()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedVector = MutableStateFlow<BlueBorneVector?>(null)
    val selectedVector: StateFlow<BlueBorneVector?> = _selectedVector

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
                // BlueBorne targets BR/EDR (Classic). Filter out BLE-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectVector(vector: BlueBorneVector?) {
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
     * Executes the BlueBorne scan/attack.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val vectorName = _selectedVector.value?.name ?: "Full Assessment"
        val taskId = "blueborne_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("BlueBorne: Starting $vectorName on $displayName")
        ActiveTaskManager.add(taskId, "BlueBorne Scan", "$vectorName on $displayName")

        attackJob = viewModelScope.launch {
            try {
                blueBorneModule.startAttack(device, _selectedVector.value).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("BlueBorne: Finished on $displayName")
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
     * Stops the running scan.
     */
    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
    }

    /**
     * Cancels any running scan and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
