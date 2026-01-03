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
    CONNECTED_BTLEJACK,
    CONNECTED_DUAL,
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
            log("Connecting to BtleJack...")
            delay(2000) // Simulate connection delay
            _hardwareState.value = HardwareState.CONNECTED_BTLEJACK
            log("Connected to BtleJack MkII.")
            log("Firmware version: 1.3.3.7")
        }
    }

    fun connectDual() {
        scope.launch {
            if (_hardwareState.value != HardwareState.CONNECTED_BTLEJACK) {
                log("BtleJack must be connected first.")
                return@launch
            }
            log("Connecting secondary USB BLE dongle...")
            delay(1500)
            _hardwareState.value = HardwareState.CONNECTED_DUAL
            log("Connected to Realtek RTL8761B.")
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

    /**
     * Reads the RSSI from the secondary hardware for a specific MAC address.
     * In a real implementation, this would query the USB dongle.
     * Here we simulate a reading that might differ slightly from the primary antenna due to placement/sensitivity.
     */
    fun getSecondaryRssi(macAddress: String): Int? {
        if (_hardwareState.value != HardwareState.CONNECTED_DUAL) return null
        // Simulate reading: In reality, this would be a blocking call or callback to the USB serial stream.
        // For the purpose of this interface, we return a placeholder value derived from "physics" (randomness here for sim)
        // or -1 if device not seen by dongle.
        return ((-90..-40).random())
    }

    private fun log(message: String) {
        scope.launch {
            _deviceLogs.emit(message)
            Log.d("HardwareManager", message)
        }
    }
}
