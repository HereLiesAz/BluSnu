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

/**
 * Enum representing the state of external hardware connections.
 */
enum class HardwareState {
    DISCONNECTED,       // No external hardware connected.
    CONNECTING,         // Connection handshake in progress.
    CONNECTED_BTLEJACK, // BtleJack-compatible device connected (e.g., Micro:Bit).
    CONNECTED_DUAL,     // Secondary USB dongle connected (for dual-radio attacks).
    CONNECTION_FAILED   // Connection attempt failed.
}

/**
 * Manager class responsible for handling external hardware peripherals.
 *
 * Blu Snu uses external hardware (via USB OTG or Serial) to perform attacks
 * that are impossible with standard Android Bluetooth chips (e.g., Sniffing, Jamming).
 * This class abstracts the low-level serial communication with devices like
 * BBC Micro:Bit (running BtleJack firmware) or generic CSR/Realtek USB dongles.
 *
 * NOTE: The current implementation contains simulation logic for demonstration purposes
 * where actual USB hardware interaction code would reside.
 */
class HardwareManager {

    // Tracks the current connection state.
    private val _hardwareState = MutableStateFlow(HardwareState.DISCONNECTED)
    val hardwareState = _hardwareState.asStateFlow()

    // Stream of log messages from the hardware device (e.g., debug output from firmware).
    private val _deviceLogs = MutableSharedFlow<String>()
    val deviceLogs = _deviceLogs.asSharedFlow()

    // Scope for background I/O operations.
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    /**
     * Initiates connection to the primary attack hardware (e.g., BtleJack).
     */
    fun connect() {
        scope.launch {
            _hardwareState.value = HardwareState.CONNECTING
            log("Connecting to BtleJack...")

            // Simulate the delay of USB enumeration and serial handshake.
            delay(2000)

            _hardwareState.value = HardwareState.CONNECTED_BTLEJACK
            log("Connected to BtleJack MkII.")
            log("Firmware version: 1.3.3.7")
        }
    }

    /**
     * Initiates connection to a secondary USB dongle for Dual-Radio operations.
     * Required for MitM attacks where one radio acts as Master and the other as Slave.
     */
    fun connectDual() {
        scope.launch {
            // Prerequisite: Primary hardware must be connected first (controller).
            if (_hardwareState.value != HardwareState.CONNECTED_BTLEJACK) {
                log("BtleJack must be connected first.")
                return@launch
            }
            log("Connecting secondary USB BLE dongle...")

            // Simulate USB connection delay.
            delay(1500)

            _hardwareState.value = HardwareState.CONNECTED_DUAL
            log("Connected to Realtek RTL8761B.")
        }
    }

    /**
     * Disconnects all external hardware and releases resources.
     */
    fun disconnect() {
        scope.launch {
            _hardwareState.value = HardwareState.DISCONNECTED
            log("Disconnected from external hardware.")
        }
    }

    /**
     * Sends a command string to the connected hardware.
     *
     * @param command The command to execute (e.g., "sniff", "jam").
     */
    fun sendCommand(command: String) {
        log("CMD > $command")
        // In a real implementation, this would write to the UsbSerialPort output stream.
    }

    /**
     * Reads the RSSI from the secondary hardware for a specific MAC address.
     *
     * This is used for "Dual-Dongle Direction Finding". By comparing RSSI from
     * the internal phone antenna and an external dongle, we can improve triangulation accuracy.
     *
     * @param macAddress The target device MAC.
     * @return The RSSI value, or null if hardware is not ready/device not found.
     */
    fun getSecondaryRssi(macAddress: String): Int? {
        if (_hardwareState.value != HardwareState.CONNECTED_DUAL) return null

        // Simulate reading: In reality, this would be a blocking call or callback to the USB serial stream.
        // We return a simulated RSSI value between -90 and -40 dBm.
        return ((-90..-40).random())
    }

    /**
     * Helper to log messages to the shared flow and system log.
     */
    private fun log(message: String) {
        scope.launch {
            _deviceLogs.emit(message)
            Log.d("HardwareManager", message)
        }
    }
}
