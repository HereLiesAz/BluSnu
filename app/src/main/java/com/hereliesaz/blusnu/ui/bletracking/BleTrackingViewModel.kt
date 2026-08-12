package com.hereliesaz.blusnu.ui.bletracking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.BleTrackingModule
import com.hereliesaz.blusnu.data.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for BLE Tracking (MAC Randomization Bypass).
 *
 * Bridges the UI and the [BleTrackingModule]. Manages the tracking lifecycle
 * including cancellation and resource cleanup. No device pre-selection is
 * needed -- this module discovers and tracks devices passively via BLE
 * advertisement scanning.
 */
class BleTrackingViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val bleTrackingModule = BleTrackingModule(application)

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    val trackedDeviceCount: StateFlow<Int> = bleTrackingModule.trackedDeviceCount

    /** Reference to the running tracking job for cancellation. */
    private var trackingJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    /**
     * Starts passive BLE tracking.
     */
    fun startTracking() {
        if (_isTracking.value) return

        _isTracking.value = true
        _logs.value = emptyList()

        val taskId = "bletracking_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("BLE Tracking: Starting passive device tracking")
        ActiveTaskManager.add(taskId, "BLE Tracking", "Scanning for BLE advertisements")

        trackingJob = viewModelScope.launch {
            try {
                bleTrackingModule.startTracking().collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("BLE Tracking: Finished")
                ActionLogger.log("Result: $resultSummary")
            } finally {
                _isTracking.value = false
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
                trackingJob = null
            }
        }
    }

    /**
     * Stops the running tracking scan.
     */
    fun stopTracking() {
        bleTrackingModule.stopTracking()
        trackingJob?.cancel()
        trackingJob = null
    }

    /**
     * Cancels any running tracking and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        trackingJob?.cancel()
        trackingJob = null
        bleTrackingModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
