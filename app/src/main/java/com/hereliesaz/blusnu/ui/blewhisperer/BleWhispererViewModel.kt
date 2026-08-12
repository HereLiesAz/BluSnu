package com.hereliesaz.blusnu.ui.blewhisperer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.BleWhispererModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.WhispererMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for BLEWhisperer (Covert Data Exfiltration).
 *
 * Bridges the UI and the [BleWhispererModule]. Manages mode selection,
 * transmit data input, and the exfiltration lifecycle including cancellation
 * and resource cleanup.
 */
class BleWhispererViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val whispererModule = BleWhispererModule(application.applicationContext)

    private val _selectedMode = MutableStateFlow(WhispererMode.TRANSMIT)
    val selectedMode: StateFlow<WhispererMode> = _selectedMode

    private val _transmitData = MutableStateFlow("")
    val transmitData: StateFlow<String> = _transmitData

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /** Reference to the running job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    /**
     * Updates the selected operating mode.
     * Only allowed when not currently running.
     */
    fun setMode(mode: WhispererMode) {
        if (!_isRunning.value) {
            _selectedMode.value = mode
        }
    }

    /**
     * Updates the data string to be transmitted.
     */
    fun setTransmitData(data: String) {
        _transmitData.value = data
    }

    /**
     * Starts the exfiltration operation in the currently selected mode.
     */
    fun start() {
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val mode = _selectedMode.value
        val taskId = "blewhisperer_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("BLEWhisperer: Starting in ${mode.name} mode")
        ActiveTaskManager.add(taskId, "BLEWhisperer", "Mode: ${mode.name}")

        attackJob = viewModelScope.launch {
            try {
                val dataFlow = when (mode) {
                    WhispererMode.TRANSMIT -> {
                        val data = _transmitData.value
                        if (data.isEmpty()) {
                            _logs.value = _logs.value + "ERROR: No data to transmit."
                            return@launch
                        }
                        whispererModule.startTransmit(data)
                    }
                    WhispererMode.RECEIVE -> {
                        whispererModule.startReceive()
                    }
                }

                dataFlow.collect { log ->
                    _logs.value = _logs.value + log
                }

                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("BLEWhisperer: Finished in ${mode.name} mode")
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
     * Stops the running operation.
     */
    fun stop() {
        whispererModule.stop()
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
        whispererModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
