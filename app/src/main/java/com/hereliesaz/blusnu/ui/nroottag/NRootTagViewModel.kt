package com.hereliesaz.blusnu.ui.nroottag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.NRootTagMode
import com.hereliesaz.blusnu.data.NRootTagModule
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for nRootTag (Find My Network Abuse).
 *
 * Bridges the UI and the [NRootTagModule]. Manages mode selection, device
 * selection (for tracking mode), and the attack lifecycle including
 * cancellation and resource cleanup.
 */
class NRootTagViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val nRootTagModule = NRootTagModule(application)

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedMode = MutableStateFlow(NRootTagMode.SCAN_FINDMY_DEVICES)
    val selectedMode: StateFlow<NRootTagMode> = _selectedMode

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
                // nRootTag tracking targets BLE advertising. Filter to BLE/Dual devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectMode(mode: NRootTagMode) {
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
     * Starts the selected nRootTag operation.
     *
     * For SCAN and BROADCAST modes, no target device is required.
     * For TRACK mode, a target device must be selected.
     */
    fun startAttack() {
        val mode = _selectedMode.value
        if (_isRunning.value) return

        // Track mode requires a selected device
        if (mode == NRootTagMode.TRACK_TARGET && _selectedDevice.value == null) return

        _isRunning.value = true
        _logs.value = emptyList()

        val taskId = "nroottag_${System.currentTimeMillis()}"
        currentTaskId = taskId

        val taskDescription = when (mode) {
            NRootTagMode.SCAN_FINDMY_DEVICES -> "Scanning for Find My beacons"
            NRootTagMode.BROADCAST_FINDMY -> "Broadcasting as Find My tag"
            NRootTagMode.TRACK_TARGET -> {
                val displayName = deviceDisplayName(_selectedDevice.value!!)
                "Tracking $displayName"
            }
        }

        ActionLogger.log("nRootTag: Starting ${mode.name} -- $taskDescription")
        ActiveTaskManager.add(taskId, "nRootTag", taskDescription)

        attackJob = viewModelScope.launch {
            try {
                val logFlow = when (mode) {
                    NRootTagMode.SCAN_FINDMY_DEVICES -> nRootTagModule.startScan()
                    NRootTagMode.BROADCAST_FINDMY -> nRootTagModule.startBroadcast()
                    NRootTagMode.TRACK_TARGET -> nRootTagModule.startTracking(_selectedDevice.value!!)
                }

                logFlow.collect { log ->
                    _logs.value = _logs.value + log
                }

                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("nRootTag: Finished ${mode.name}")
                ActionLogger.log("Result: $resultSummary")
            } finally {
                _isRunning.value = false
                nRootTagModule.stop()
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
                attackJob = null
            }
        }
    }

    /**
     * Stops the running operation.
     */
    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        nRootTagModule.stop()
    }

    /**
     * Cancels any running operation and cleans up resources on ViewModel disposal.
     */
    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        nRootTagModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
