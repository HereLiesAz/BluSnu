package com.hereliesaz.blusnu.ui.sniffle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.SniffleModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Enum representing the available Sniffle sniffing modes.
 */
enum class SniffMode {
    /** Sniff BLE advertisements on all three advertising channels. */
    ADVERTISING,
    /** Follow a specific device's connection by MAC address. */
    CONNECTION_FOLLOW,
    /** Passively follow the first new connection observed. */
    PASSIVE
}

/**
 * State object for the Sniffle BLE Sniffer UI.
 */
data class SniffleScreenState(
    val hardwareState: HardwareState = HardwareState.DISCONNECTED,
    val discoveredDevices: List<TargetDevice> = emptyList(),
    val selectedDevice: TargetDevice? = null,
    val isSniffing: Boolean = false,
    val sniffMode: SniffMode = SniffMode.ADVERTISING,
    val deviceLogs: List<String> = emptyList()
)

/**
 * ViewModel for the Sniffle BLE Sniffer screen.
 *
 * Coordinates [HardwareManager] connection state and [SniffleModule] sniffing logic.
 * Uses [ActionLogger] for audit trail and [ActiveTaskManager] for dashboard task cards.
 */
class SniffleViewModel(
    application: Application,
    private val hardwareManager: HardwareManager,
    private val sniffleModule: SniffleModule,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SniffleViewModel"
        /** Maximum number of log entries kept in memory. */
        private const val MAX_LOG_ENTRIES = 1000
        /** Task ID for ActiveTaskManager registration. */
        private const val TASK_ID = "sniffle_sniff"
    }

    private val _state = MutableStateFlow(SniffleScreenState())
    val state = _state.asStateFlow()

    private var sniffCollectionJob: Job? = null

    init {
        // Observe hardware connection status.
        hardwareManager.hardwareState
            .onEach { hardwareState ->
                _state.value = _state.value.copy(hardwareState = hardwareState)
            }
            .launchIn(viewModelScope)

        // Observe Sniffle module sniffing state.
        sniffleModule.isSniffing
            .onEach { sniffing ->
                _state.value = _state.value.copy(isSniffing = sniffing)
            }
            .launchIn(viewModelScope)

        // Observe available BLE devices from the repository.
        deviceRepository.allDevices
            .onEach { devices ->
                val bleDevices = devices.filter { it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL }
                _state.value = _state.value.copy(discoveredDevices = bleDevices)
            }
            .launchIn(viewModelScope)

        // Observe hardware logs for the log display.
        hardwareManager.deviceLogs
            .onEach { log ->
                val currentLogs = _state.value.deviceLogs
                val updatedLogs = if (currentLogs.size >= MAX_LOG_ENTRIES) {
                    currentLogs.drop(1) + log
                } else {
                    currentLogs + log
                }
                _state.value = _state.value.copy(deviceLogs = updatedLogs)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Selects a target device for CONNECTION_FOLLOW mode.
     *
     * @param device The [TargetDevice] to follow, or `null` to clear selection.
     */
    fun selectDevice(device: TargetDevice?) {
        _state.value = _state.value.copy(selectedDevice = device)
    }

    /**
     * Initiates a hardware connection to the Sniffle dongle.
     */
    fun connectHardware() {
        viewModelScope.launch {
            hardwareManager.connect()
        }
    }

    /**
     * Disconnects from the Sniffle dongle hardware.
     */
    fun disconnectHardware() {
        viewModelScope.launch {
            hardwareManager.disconnect()
        }
    }

    /**
     * Starts sniffing in the specified mode.
     *
     * Registers an active task and logs the action. For [SniffMode.CONNECTION_FOLLOW],
     * a device must be selected first.
     *
     * @param mode The [SniffMode] to use.
     */
    fun startSniffing(mode: SniffMode) {
        _state.value = _state.value.copy(sniffMode = mode)

        sniffCollectionJob?.cancel()
        sniffCollectionJob = viewModelScope.launch {
            val sniffFlow = when (mode) {
                SniffMode.ADVERTISING -> {
                    ActionLogger.log("Sniffle: Started advertising scan on all channels.")
                    ActiveTaskManager.add(TASK_ID, "Sniffle Sniffer", "Scanning BLE advertisements")
                    sniffleModule.startAdvertisingScan()
                }
                SniffMode.CONNECTION_FOLLOW -> {
                    val target = _state.value.selectedDevice
                    if (target == null) {
                        ActionLogger.log("Sniffle: No target device selected for connection follow.")
                        return@launch
                    }
                    ActionLogger.log("Sniffle: Following connections for ${target.macAddress}.")
                    ActiveTaskManager.add(TASK_ID, "Sniffle Sniffer", "Following ${target.macAddress}")
                    sniffleModule.startConnectionFollow(target.macAddress)
                }
                SniffMode.PASSIVE -> {
                    ActionLogger.log("Sniffle: Started passive sniff - following first new connection.")
                    ActiveTaskManager.add(TASK_ID, "Sniffle Sniffer", "Passive connection follow")
                    sniffleModule.startPassiveSniff()
                }
            }

            // Collect sniff output into logs (the hardware log observer already captures
            // raw device output, but sniff-flow-specific lines may be filtered differently).
            sniffFlow.collect { /* collected by hardware log observer */ }
        }
    }

    /**
     * Stops any active sniffing operation.
     */
    fun stopSniffing() {
        sniffCollectionJob?.cancel()
        sniffCollectionJob = null
        sniffleModule.stopSniffing()
        ActiveTaskManager.remove(TASK_ID)
        ActionLogger.log("Sniffle: Sniffing stopped.")
    }

    override fun onCleared() {
        super.onCleared()
        stopSniffing()
        sniffleModule.close()
    }
}
