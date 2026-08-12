package com.hereliesaz.blusnu.ui.screamingchannels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.ScreamingChannelsModule
import com.hereliesaz.blusnu.data.ScreamingMode
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the Screaming Channels (Side-Channel) attack screen.
 *
 * Bridges the UI and the [ScreamingChannelsModule]. Manages the capture lifecycle
 * including hardware connection awareness, cancellation, and resource cleanup.
 */
class ScreamingChannelsViewModel(
    application: Application,
    private val hardwareManager: HardwareManager,
    private val screamingChannelsModule: ScreamingChannelsModule,
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

    private val _selectedMode = MutableStateFlow(ScreamingMode.CAPTURE_TRACES)
    val selectedMode: StateFlow<ScreamingMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _hardwareConnected = MutableStateFlow(false)
    val hardwareConnected: StateFlow<Boolean> = _hardwareConnected

    /** Reference to the running capture job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        // Observe available BLE devices from the repository.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
        }

        // Observe hardware connection status.
        hardwareManager.hardwareState
            .onEach { state ->
                _hardwareConnected.value =
                    state == HardwareState.CONNECTED_BTLEJACK ||
                    state == HardwareState.CONNECTED_DUAL
            }
            .launchIn(viewModelScope)

        // Observe hardware logs for the log display.
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

    fun selectMode(mode: ScreamingMode) {
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
     * Initiates a hardware connection via the HardwareManager.
     */
    fun connectHardware() {
        viewModelScope.launch {
            hardwareManager.connect()
        }
    }

    /**
     * Disconnects from the SDR hardware.
     */
    fun disconnectHardware() {
        viewModelScope.launch {
            hardwareManager.disconnect()
        }
    }

    /**
     * Executes the screaming channels capture.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val mode = _selectedMode.value
        val taskId = "screaming_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("Screaming Channels: Starting ${mode.name} on $displayName")
        ActiveTaskManager.add(taskId, "Screaming Channels", "${mode.name} on $displayName")

        attackJob = viewModelScope.launch {
            try {
                screamingChannelsModule.startCapture(device, mode).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("Screaming Channels: Finished ${mode.name} on $displayName")
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
     * Stops the running capture.
     */
    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        screamingChannelsModule.stopCapture()
    }

    /**
     * Cancels any running capture and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        screamingChannelsModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
