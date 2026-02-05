package com.hereliesaz.blusnu.ui.keystrokeinjection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.DuckyScriptParser
import com.hereliesaz.blusnu.data.KeystrokeInjectionModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State object for Keystroke Injection screen.
 *
 * @property isPared True if the "Just Works" pairing succeeded.
 * @property logMessages Attack logs.
 * @property devices List of available targets.
 * @property selectedDevice Current target.
 */
data class KeystrokeInjectionState(
    val isPared: Boolean = false,
    val logMessages: List<String> = emptyList(),
    val devices: List<TargetDevice> = emptyList(),
    val selectedDevice: TargetDevice? = null
)

/**
 * ViewModel for Keystroke Injection.
 *
 * Coordinates device pairing via [KeystrokeInjectionModule] and script execution
 * via [DuckyScriptParser].
 */
class KeystrokeInjectionViewModel(
    application: Application,
    private val keystrokeInjectionModule: KeystrokeInjectionModule,
    deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val duckyScriptParser = DuckyScriptParser()
    private val _state = MutableStateFlow(KeystrokeInjectionState())
    val state: StateFlow<KeystrokeInjectionState> = _state.asStateFlow()

    init {
        // Collect all devices (both Classic and BLE can technically be paired with).
        viewModelScope.launch {
            deviceRepository.allDevices.collect { devices ->
                _state.update { it.copy(devices = devices) }
            }
        }
    }

    /**
     * Executes a DuckyScript payload.
     */
    fun onRunDuckyScript(script: String) {
        ActionLogger.log("Running DuckyScript")
        viewModelScope.launch {
            log("Executing DuckyScript...")

            // Parse and run line by line.
            duckyScriptParser.execute(script) { cmd ->
                when (cmd.type) {
                    DuckyScriptParser.CommandType.STRING -> {
                        log("TYPE: ${cmd.args}")
                        keystrokeInjectionModule.sendKeystrokes(cmd.args)
                    }
                    DuckyScriptParser.CommandType.ENTER -> {
                        log("PRESS: ENTER")
                        // Send newline char to simulate Enter.
                        keystrokeInjectionModule.sendKeystrokes("\n")
                    }
                    DuckyScriptParser.CommandType.GUI -> {
                        log("PRESS: GUI ${cmd.args}")
                        // Placeholder for special key support.
                    }
                    else -> log("Skipping unsupported command: ${cmd.type}")
                }
            }
            log("Script execution finished.")
        }
    }

    fun onDeviceSelected(device: TargetDevice) {
        _state.update { it.copy(selectedDevice = device) }
    }

    /**
     * Triggers the initial pairing attempt.
     */
    fun onAttemptAttack() {
        val selected = state.value.selectedDevice ?: return
        ActionLogger.log("Keystroke injection attack started against ${selected.macAddress}.")
        viewModelScope.launch {
            log("Attempting silent pairing with ${selected.name ?: selected.macAddress}...")
            val success = keystrokeInjectionModule.attemptPairing(selected)
            if (success) {
                log("Pairing successful!")
                _state.update { it.copy(isPared = true) }
            } else {
                log("Pairing failed.")
            }
        }
    }

    /**
     * Sends a simple text string.
     */
    fun onSendKeystrokes(text: String) {
        ActionLogger.log("Sending keystrokes: '$text'")
        viewModelScope.launch {
            log("Sending keystrokes: '$text'")
            val success = keystrokeInjectionModule.sendKeystrokes(text)
            if (!success) {
                log("Failed to send keystrokes.")
            }
        }
    }

    private fun log(message: String) {
        _state.update { it.copy(logMessages = it.logMessages + message) }
    }
}
