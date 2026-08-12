package com.hereliesaz.blusnu.ui.spoofing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.SpoofingModule
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holding the data for the Spoofing screen.
 *
 * @property logMessages General operational logs.
 * @property isError True if the current MAC address input is invalid.
 * @property devices List of available devices to clone.
 * @property selectedDevice The currently selected target to clone.
 * @property mitmDevices List of devices involved in an active MitM session.
 * @property mitmLogs Logs specific to MitM interception, keyed by device MAC.
 */
data class SpoofingState(
    val logMessages: List<String> = emptyList(),
    val isError: Boolean = false,
    val devices: List<TargetDevice> = emptyList(),
    val selectedDevice: TargetDevice? = null,
    val mitmDevices: List<TargetDevice> = emptyList(),
    val mitmLogs: Map<String, List<String>> = emptyMap()
)

/**
 * ViewModel for Spoofing and Identity management.
 */
class SpoofingViewModel(
    application: Application,
    private val spoofingModule: SpoofingModule,
    deviceRepository: DeviceRepository,
    private val hardwareManager: com.hereliesaz.blusnu.data.HardwareManager
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SpoofingState())
    val state: StateFlow<SpoofingState> = _state.asStateFlow()

    // Transient state for the text field before application.
    private var macAddress: String = ""

    init {
        // Collect available devices for the dropdown/roller.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { devices ->
                _state.update { it.copy(devices = devices) }
            }
        }
    }

    /**
     * Handler for device selection from the Roller.
     */
    fun onDeviceSelected(device: TargetDevice) {
        macAddress = device.macAddress
        // Validate and update state.
        _state.update { it.copy(selectedDevice = device, isError = !isValidMacAddress(macAddress)) }
    }

    /**
     * Handler for manual text input changes.
     */
    fun onMacAddressChanged(newMacAddress: String) {
        macAddress = newMacAddress
        _state.update { it.copy(isError = !isValidMacAddress(newMacAddress)) }
    }

    /**
     * Executes the MAC change request.
     */
    fun onApplyClicked() {
        val taskId = "spoofing_${System.currentTimeMillis()}"
        ActionLogger.log("Spoofing: Applying MAC address $macAddress")
        ActiveTaskManager.add(taskId, "MAC Spoofing", "Changing to $macAddress")

        viewModelScope.launch {
            try {
                log("Applying new MAC address: $macAddress")
                // Call the data module to execute root commands.
                val success = spoofingModule.spoofMacAddress(macAddress)
                if (success) {
                    log("Successfully changed MAC address to $macAddress")
                    ActionLogger.log("Spoofing: Successfully changed MAC to $macAddress")
                } else {
                    log("Failed to change MAC address.")
                    ActionLogger.log("Spoofing: Failed to change MAC address")
                }
            } finally {
                ActiveTaskManager.remove(taskId)
            }
        }
    }

    /**
     * Validates MAC address format (XX:XX:XX:XX:XX:XX) using the shared
     * [MacValidator] to avoid regex divergence across the app.
     */
    private fun isValidMacAddress(mac: String): Boolean = MacValidator.isValid(mac)

    /**
     * Helper to append to the status log.
     */
    private fun log(message: String) {
        _state.update { it.copy(logMessages = it.logMessages + message) }
    }

    /**
     * Triggers the multi-stage MitM attack sequence.
     */
    fun onStartMitmAttack() {
        viewModelScope.launch {
            log("Starting MITM attack...")
            // Create a MitM controller instance.
            // Note: passing _state (MutableStateFlow) allows the controller to update logs directly.
            val mitmAttack = com.hereliesaz.blusnu.data.MitmAttack(_state, hardwareManager, viewModelScope)
            mitmAttack.start()
        }
    }
}
