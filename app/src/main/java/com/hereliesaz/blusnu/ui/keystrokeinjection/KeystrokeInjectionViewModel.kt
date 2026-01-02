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

data class KeystrokeInjectionState(
    val isPared: Boolean = false,
    val logMessages: List<String> = emptyList(),
    val devices: List<TargetDevice> = emptyList(),
    val selectedDevice: TargetDevice? = null
)

class KeystrokeInjectionViewModel(
    application: Application,
    private val keystrokeInjectionModule: KeystrokeInjectionModule,
    deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val duckyScriptParser = DuckyScriptParser()
    private val _state = MutableStateFlow(KeystrokeInjectionState())
    val state: StateFlow<KeystrokeInjectionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { devices ->
                _state.update { it.copy(devices = devices) }
            }
        }
    }

    fun onRunDuckyScript(script: String) {
        ActionLogger.log("Running DuckyScript")
        viewModelScope.launch {
            log("Executing DuckyScript...")
            duckyScriptParser.execute(script) { cmd ->
                when (cmd.type) {
                    DuckyScriptParser.CommandType.STRING -> {
                        log("TYPE: ${cmd.args}")
                        keystrokeInjectionModule.sendKeystrokes(cmd.args)
                    }
                    DuckyScriptParser.CommandType.ENTER -> {
                        log("PRESS: ENTER")
                        // Simulate ENTER (implementation depends on module support, assuming generic send for now)
                        keystrokeInjectionModule.sendKeystrokes("\n")
                    }
                    DuckyScriptParser.CommandType.GUI -> {
                        log("PRESS: GUI ${cmd.args}")
                        // Placeholder for GUI key
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
