package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HardwareState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CONNECTION_FAILED
}

/**
 * A simulated class that manages a connection to an external hardware device,
 * such as a BtleJack or a secondary Bluetooth dongle.
 */
class HardwareManager {

    private val _hardwareState = MutableStateFlow(HardwareState.DISCONNECTED)
    val hardwareState = _hardwareState.asStateFlow()

    private val _deviceLogs = MutableSharedFlow<String>()
    val deviceLogs = _deviceLogs.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun connect() {
        scope.launch {
            _hardwareState.value = HardwareState.CONNECTING
            log("Connecting to external hardware...")
            delay(2000) // Simulate connection delay
            _hardwareState.value = HardwareState.CONNECTED
            log("Connected to BtleJack MkII.")
            log("Firmware version: 1.3.3.7")
        }
    }

    fun disconnect() {
        scope.launch {
            _hardwareState.value = HardwareState.DISCONNECTED
            log("Disconnected from external hardware.")
        }
    }

    /**
     * Sends a command to the external hardware.
     * @param command The command to send.
     */
    fun sendCommand(command: String) {
        log("CMD > $command")
        // In a real implementation, this would send the command over USB serial.
    }

    private fun log(message: String) {
        scope.launch {
            _deviceLogs.emit(message)
            Log.d("HardwareManager", message)
        }
    }
}
