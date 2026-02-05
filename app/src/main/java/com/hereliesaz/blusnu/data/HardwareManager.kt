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
 * Enumeration of possible states for external hardware connections.
 */
enum class HardwareState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_BTLEJACK, // Specifically connected to a BtleJack sniffer
    CONNECTED_DUAL,     // Connected to both BtleJack and a secondary USB dongle
    CONNECTION_FAILED
}

/**
 * Manages connections to external hardware peripherals via USB-OTG or Serial.
 *
 * <p>
 * Blu Snu supports external hardware to perform attacks not possible with standard Android radios:
 * 1. <b>BtleJack (Micro:bit):</b> For sniffing and connection jamming.
 * 2. <b>USB Dongles (e.g., CSR8510, RTL8761B):</b> For promiscuous mode and dual-antenna direction finding.
 * </p>
 *
 * This class abstracts the serial communication (usually via UsbSerial library) and state management.
 * Currently, it simulates these interactions for demonstration purposes.
 */
class HardwareManager {

    // StateFlows for UI observability
    private val _hardwareState = MutableStateFlow(HardwareState.DISCONNECTED)
    val hardwareState = _hardwareState.asStateFlow()

    // SharedFlow for a stream of log messages (Console output)
    private val _deviceLogs = MutableSharedFlow<String>()
    val deviceLogs = _deviceLogs.asSharedFlow()

    // Dedicated scope for I/O operations to prevent blocking the Main Thread
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    /**
     * Initiates connection to the primary BtleJack device.
     */
    fun connect() {
        scope.launch {
            _hardwareState.value = HardwareState.CONNECTING
            log("Connecting to BtleJack...")

            // Simulation: Delay mimics USB handshake and baud rate negotiation
            delay(2000)

            _hardwareState.value = HardwareState.CONNECTED_BTLEJACK
            log("Connected to BtleJack MkII.")
            log("Firmware version: 1.3.3.7")
        }
    }

    /**
     * Connects a secondary USB dongle for Dual-Antenna operations.
     * Required for high-precision RSSI Triangulation (Diversity Reception).
     */
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

    /**
     * Closes all external connections.
     */
    fun disconnect() {
        scope.launch {
            _hardwareState.value = HardwareState.DISCONNECTED
            log("Disconnected from external hardware.")
        }
    }

    /**
     * Sends a raw command string to the connected hardware.
     * @param command The command (e.g., "STOP", "JAM 0x1234").
     */
    fun sendCommand(command: String) {
        log("CMD > $command")
        // TODO: Integrate UsbSerial library here to write to the output stream.
    }

    /**
     * Reads the RSSI from the secondary hardware for a specific MAC address.
     *
     * <p>
     * <b>Physics Note:</b> By comparing RSSI from the internal phone antenna and an external directional antenna,
     * we can determine directionality much more accurately than with a single omni-directional antenna.
     * </p>
     *
     * @param macAddress The target to query.
     * @return The simulated RSSI value (e.g., -55) or null if not found.
     */
    fun getSecondaryRssi(macAddress: String): Int? {
        if (_hardwareState.value != HardwareState.CONNECTED_DUAL) return null

        // Simulation: Return a random RSSI in a realistic range.
        // In production, this would parse the HCI Event or Serial output from the dongle.
        return ((-90..-40).random())
    }

    /**
     * Internal helper to log messages to the [deviceLogs] flow.
     */
    private fun log(message: String) {
        scope.launch {
            _deviceLogs.emit(message)
            Log.d("HardwareManager", message)
        }
    }
}
