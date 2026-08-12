package com.hereliesaz.blusnu.ui.esp32hci

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Esp32HciCommand
import com.hereliesaz.blusnu.data.Esp32HciModule
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for ESP32 HCI Exploitation.
 *
 * Bridges the UI and the [Esp32HciModule]. Manages command execution lifecycle,
 * hardware connection state observation, and resource cleanup.
 *
 * Unlike device-targeting ViewModels, this module targets the connected ESP32
 * itself via USB-OTG, so no device selection or filtering is needed.
 */
class Esp32HciViewModel(
    application: Application,
    private val hardwareManager: HardwareManager,
    private val esp32HciModule: Esp32HciModule,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    companion object {
        /** Maximum number of log entries kept in memory. */
        private const val MAX_LOG_ENTRIES = 1000
    }

    private val _selectedCommand = MutableStateFlow(Esp32HciCommand.SCAN_VENDOR_CMDS)
    val selectedCommand: StateFlow<Esp32HciCommand> = _selectedCommand

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _hardwareConnected = MutableStateFlow(false)
    val hardwareConnected: StateFlow<Boolean> = _hardwareConnected

    /** Reference to the running command job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        // Observe hardware connection state.
        hardwareManager.hardwareState
            .onEach { hardwareState ->
                _hardwareConnected.value =
                    hardwareState == HardwareState.CONNECTED_BTLEJACK ||
                    hardwareState == HardwareState.CONNECTED_DUAL
            }
            .launchIn(viewModelScope)

        // Observe hardware logs and append to our log list.
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

    /**
     * Selects the HCI command to execute.
     */
    fun selectCommand(command: Esp32HciCommand) {
        _selectedCommand.value = command
    }

    /**
     * Executes the selected HCI command on the connected ESP32.
     *
     * @param params Command-specific parameters (e.g., address, length, data, mac).
     */
    fun startAttack(params: Map<String, String> = emptyMap()) {
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val command = _selectedCommand.value
        val taskId = "esp32hci_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("ESP32 HCI Exploitation: Executing ${command.displayName}")
        ActiveTaskManager.add(taskId, "ESP32 HCI Exploitation", "Running ${command.displayName}")

        attackJob = viewModelScope.launch {
            try {
                esp32HciModule.executeCommand(command, params).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("ESP32 HCI Exploitation: Finished ${command.displayName}")
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
     * Stops the running command execution.
     */
    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        esp32HciModule.stop()
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
     * Cancels any running command and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        esp32HciModule.close()
    }
}
