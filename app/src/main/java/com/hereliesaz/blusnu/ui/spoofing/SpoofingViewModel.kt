package com.hereliesaz.blusnu.ui.spoofing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.SpoofingModule
import com.hereliesaz.blusnu.data.TargetDevice
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
        viewModelScope.launch {
            log("Applying new MAC address: $macAddress")
            // Call the data module to execute root commands.
            val success = spoofingModule.spoofMacAddress(macAddress)
            if (success) {
                log("Successfully changed MAC address to $macAddress")
            } else {
                log("Failed to change MAC address.")
            }
        }
    }

    /**
     * Validates MAC address format (XX:XX:XX:XX:XX:XX).
     */
    private fun isValidMacAddress(mac: String): Boolean {
        val regex = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return regex.matches(mac)
    }

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
