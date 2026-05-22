package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Enum defining the state of the Btlejuice Proxy.
 */
enum class BtlejuiceState {
    IDLE,           // Proxy is not running.
    PROXYING,       // Setup phase: Hardware is configuring itself to spoof the target.
    INTERCEPTING    // Active phase: Traffic is flowing through the proxy.
}

/**
 * Module responsible for executing the Btlejuice-style Man-in-the-Middle (MitM) attack.
 *
 * Btlejuice (originally a Node.js tool) works by placing an attacker machine between
 * a BLE peripheral and a Central. It requires two Bluetooth radios:
 * 1. One to act as the "Fake Peripheral" (advertises to the Central).
 * 2. One to act as the "Fake Central" (connects to the real Peripheral).
 *
 * This module controls the external hardware required to achieve this dual-role capability.
 *
 * @property hardwareManager Interface to control the external Bluetooth dongle/SDR.
 */
class BtlejuiceModule(private val hardwareManager: HardwareManager) {

    // Internal state flow for UI updates.
    private val _state = MutableStateFlow(BtlejuiceState.IDLE)
    // Exposed immutable state.
    val state = _state.asStateFlow()

    // SharedFlow to emit real-time logs of intercepted packets (Read/Write/Notify).
    // SharedFlow is used here because we want to broadcast events to the UI log console.
    private val _interceptedTraffic = MutableSharedFlow<String>()
    val interceptedTraffic = _interceptedTraffic.asSharedFlow()

    // Coroutine scope for managing the attack lifecycle.
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    // Handle to the active job.
    private var proxyJob: Job? = null

    /**
     * Starts the MitM proxy against a specific target.
     *
     * @param target The BLE peripheral to impersonate and intercept.
     */
    fun startProxy(target: TargetDevice) {
        // Prevent starting if already active.
        if (proxyJob?.isActive == true) return

        proxyJob = scope.launch {
            _state.value = BtlejuiceState.PROXYING

            // Command the hardware to clone the target's advertisement data.
            hardwareManager.sendCommand("btlejuice --target ${target.macAddress}")
            _interceptedTraffic.emit("Spoofing ${target.name}...")

            // Simulate setup delay (cloning advertisement, waiting for victim connection).
            delay(3000)

            _state.value = BtlejuiceState.INTERCEPTING
            _interceptedTraffic.emit("Proxy established. Intercepting traffic...")

            // Main interception loop.
            // In a real implementation, this would read from the hardware interface's stdout/socket.
            while (isActive) {
                // Simulate a READ request from the Central.
                delay(2500)
                _interceptedTraffic.emit("[READ] Handle 0x002A (Device Name)")

                // Simulate a WRITE command from the Central (e.g., unlock command).
                delay(500)
                _interceptedTraffic.emit("[WRITE] Handle 0x0010, Value=0x01")

                // Simulate a NOTIFICATION from the Peripheral (e.g., status update).
                delay(3000)
                _interceptedTraffic.emit("[NOTIFY] Handle 0x0012, Value=0x54-0x45-0x4D-0x50-3A-32-33-43")
            }
        }
    }

    /**
     * Stops the proxy and resets the hardware.
     */
    fun stopProxy() {
        // Cancel the loop.
        proxyJob?.cancel()
        proxyJob = null

        scope.launch {
            _state.value = BtlejuiceState.IDLE
            // Reset hardware.
            hardwareManager.sendCommand("stop")
            _interceptedTraffic.emit("Proxy stopped.")
        }
    }
}
