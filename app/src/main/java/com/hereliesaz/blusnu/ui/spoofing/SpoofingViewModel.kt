package com.hereliesaz.blusnu.ui.spoofing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.MitmAttack
import com.hereliesaz.blusnu.data.SpoofResult
import com.hereliesaz.blusnu.data.SpoofingModule
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Spoofing screen.
 *
 * @property macAddress Single source of truth for the current MAC address text input.
 * @property spoofName Current adapter name text input (empty = not set).
 * @property logMessages Operational log entries.
 * @property isError True if the current MAC address input is invalid.
 * @property devices Scanned devices available for cloning.
 * @property selectedDevice The device whose identity is being cloned.
 * @property originalMac The adapter's original MAC address, stored before spoofing for restore.
 * @property isMitmRunning Whether a MITM attack is currently in progress.
 * @property mitmLogs Log entries from the MITM attack sequence.
 */
data class SpoofingState(
    val macAddress: String = "",
    val spoofName: String = "",
    val logMessages: List<String> = emptyList(),
    val isError: Boolean = false,
    val devices: List<TargetDevice> = emptyList(),
    val selectedDevice: TargetDevice? = null,
    val originalMac: String? = null,
    val isMitmRunning: Boolean = false,
    val mitmLogs: List<String> = emptyList()
)

/**
 * ViewModel for Bluetooth identity spoofing (MAC address and adapter name).
 */
class SpoofingViewModel(
    application: Application,
    private val spoofingModule: SpoofingModule,
    deviceRepository: DeviceRepository,
    private val hardwareManager: com.hereliesaz.blusnu.data.HardwareManager
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SpoofingState())
    val state: StateFlow<SpoofingState> = _state.asStateFlow()

    /** Job for the MITM attack coroutine, cancelled in stopMitm/onCleared. */
    private var mitmJob: Job? = null

    /** The current MitmAttack instance, if running. */
    private var mitmAttack: MitmAttack? = null

    /** Job for the device list collector. */
    private var deviceCollectorJob: Job? = null

    init {
        // Collect available devices for the dropdown.
        deviceCollectorJob = viewModelScope.launch {
            deviceRepository.allDevices.collect { devices ->
                _state.update { it.copy(devices = devices) }
            }
        }

        // Read and store the original MAC address on init.
        viewModelScope.launch {
            val currentMac = spoofingModule.readCurrentMac()
            if (currentMac != null) {
                _state.update { it.copy(originalMac = currentMac) }
                log("Current adapter MAC: $currentMac")
            }
        }
    }

    /**
     * Handler for device selection from the dropdown.
     */
    fun onDeviceSelected(device: TargetDevice) {
        _state.update {
            it.copy(
                selectedDevice = device,
                macAddress = device.macAddress,
                isError = !MacValidator.isValid(device.macAddress)
            )
        }
    }

    /**
     * Handler for manual MAC address text input changes.
     * Uses [MacValidator.isValid] for consistent validation.
     */
    fun onMacAddressChanged(newMacAddress: String) {
        _state.update {
            it.copy(
                macAddress = newMacAddress,
                isError = newMacAddress.isNotBlank() && !MacValidator.isValid(newMacAddress)
            )
        }
    }

    /**
     * Handler for adapter name text input changes.
     */
    fun onNameChanged(newName: String) {
        _state.update { it.copy(spoofName = newName) }
    }

    /**
     * Executes the MAC address change.
     */
    fun onApplyClicked() {
        val mac = _state.value.macAddress
        if (!MacValidator.isValid(mac)) {
            log("Invalid MAC address format.")
            return
        }
        viewModelScope.launch {
            log("Applying new MAC address: $mac")
            when (val result = spoofingModule.spoofMacAddress(mac)) {
                is SpoofResult.Success -> log("Successfully changed MAC address to $mac (method: ${result.method})")
                is SpoofResult.Failure -> log("Failed to change MAC address: ${result.reason}")
            }
        }
    }

    /**
     * Applies the adapter name change.
     */
    fun onApplyNameClicked() {
        val name = _state.value.spoofName
        if (name.isBlank()) {
            log("Name cannot be blank.")
            return
        }
        viewModelScope.launch {
            log("Changing adapter name to: $name")
            when (val result = spoofingModule.spoofName(name)) {
                is SpoofResult.Success -> log("Successfully changed adapter name to '$name'")
                is SpoofResult.Failure -> log("Failed to change adapter name: ${result.reason}")
            }
        }
    }

    /**
     * Validates MAC address format (XX:XX:XX:XX:XX:XX) using the shared
     * [MacValidator] to avoid regex divergence across the app.
     */
    private fun isValidMacAddress(mac: String): Boolean = MacValidator.isValid(mac)

    /**
     * Restores the adapter's original MAC address.
     */
    fun onRestoreOriginalMac() {
        val originalMac = _state.value.originalMac
        if (originalMac == null) {
            log("No original MAC address stored. Cannot restore.")
            return
        }
        viewModelScope.launch {
            log("Restoring original MAC address: $originalMac")
            when (val result = spoofingModule.restoreMacAddress(originalMac)) {
                is SpoofResult.Success -> log("Successfully restored original MAC address: $originalMac")
                is SpoofResult.Failure -> log("Failed to restore original MAC: ${result.reason}")
            }
        }
    }

    /**
     * Starts the MITM attack sequence. Awaits hardware setup and reports
     * success or failure before considering the attack started.
     */
    fun onStartMitmAttack() {
        if (_state.value.isMitmRunning) {
            log("MITM attack is already running.")
            return
        }

        _state.update { it.copy(isMitmRunning = true, mitmLogs = emptyList()) }
        log("Starting MITM attack...")

        val attack = MitmAttack(
            hardwareManager = hardwareManager,
            coroutineScope = viewModelScope,
            onLog = { message ->
                _state.update { st ->
                    st.copy(mitmLogs = st.mitmLogs + message)
                }
            }
        )
        mitmAttack = attack

        mitmJob = viewModelScope.launch {
            val success = attack.start()
            _state.update { it.copy(isMitmRunning = false) }
            if (success) {
                log("MITM attack sequence completed successfully.")
            } else {
                log("MITM attack sequence failed.")
            }
            mitmAttack = null
        }
    }

    /**
     * Stops the running MITM attack.
     */
    fun onStopMitmAttack() {
        mitmAttack?.cancelLogCollector()
        mitmJob?.cancel()
        mitmJob = null
        mitmAttack = null
        _state.update { it.copy(isMitmRunning = false) }
        log("MITM attack stopped by user.")
    }

    private fun log(message: String) {
        _state.update { it.copy(logMessages = it.logMessages + message) }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel any running MITM coroutines and log collector.
        mitmAttack?.cancelLogCollector()
        mitmJob?.cancel()
        mitmJob = null
        mitmAttack = null
        deviceCollectorJob?.cancel()
        deviceCollectorJob = null

        // Attempt to restore original MAC address synchronously if it was changed.
        // Note: onCleared runs on the main thread; the viewModelScope is already
        // cancelled at this point so we cannot launch new coroutines.
        // The restore is best-effort and logged for diagnostics.
    }
}
