package com.hereliesaz.blusnu.ui.injectable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.InjectableModule
import com.hereliesaz.blusnu.data.InjectionMode
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the InjectaBLE (Packet Injection) attack.
 *
 * Bridges the UI and the [InjectableModule]. Coordinates hardware connection
 * state from [HardwareManager] with the injection attack lifecycle, including
 * cancellation and resource cleanup.
 */
class InjectableViewModel(
    application: Application,
    private val hardwareManager: HardwareManager,
    private val injectableModule: InjectableModule,
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

    private val _selectedMode = MutableStateFlow(InjectionMode.PACKET_INJECT)
    val selectedMode: StateFlow<InjectionMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _hardwareConnected = MutableStateFlow(false)
    val hardwareConnected: StateFlow<Boolean> = _hardwareConnected

    /** Reference to the running injection job for cancellation. */
    private var attackJob: Job? = null

    /** Task ID for ActiveTaskManager tracking. */
    private var currentTaskId: String? = null

    init {
        // Observe hardware connection state.
        hardwareManager.hardwareState
            .onEach { state ->
                _hardwareConnected.value =
                    state == HardwareState.CONNECTED_BTLEJACK ||
                    state == HardwareState.CONNECTED_DUAL
            }
            .launchIn(viewModelScope)

        // Observe available BLE devices.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // InjectaBLE targets BLE Link Layer. Filter out Classic-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectMode(mode: InjectionMode) {
        _selectedMode.value = mode
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
     * Executes the InjectaBLE attack.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val displayName = deviceDisplayName(device)
        val mode = _selectedMode.value
        val taskId = "injectable_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("InjectaBLE: Starting ${mode.name} on $displayName")
        ActiveTaskManager.add(taskId, "InjectaBLE", "${mode.name} on $displayName")

        attackJob = viewModelScope.launch {
            try {
                injectableModule.startInjection(device, mode).collect { log ->
                    val currentLogs = _logs.value
                    _logs.value = if (currentLogs.size >= MAX_LOG_ENTRIES) {
                        currentLogs.drop(1) + log
                    } else {
                        currentLogs + log
                    }
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("InjectaBLE: Finished on $displayName")
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
     * Stops the running injection attack.
     */
    fun stopAttack() {
        injectableModule.stopInjection()
        attackJob?.cancel()
        attackJob = null
    }

    /**
     * Cancels any running injection and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        injectableModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
