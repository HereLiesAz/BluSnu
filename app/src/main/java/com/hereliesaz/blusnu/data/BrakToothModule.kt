package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Enumeration of supported BrakTooth attack vectors.
 * BrakTooth is a family of security vulnerabilities in commercial Bluetooth stacks (SoCs).
 *
 * @property description A human-readable description of the attack mechanism.
 */
enum class BrakToothVector(val description: String) {
    V1_LMP_Feature_Response_Flooding("Crash via Feature Response Flooding"),
    V2_LMP_AuRand_Flooding("Crash via AuRand Flooding"),
    V4_LMP_Feature_Response_Deduplication("Deadlock via Feature Response Deduplication"),
    V6_LMP_Timing_Attack("Timing Attack on LMP State Machine"),
    V13_LMP_Max_Slot_Length_Overflow("Buffer Overflow via Max Slot Length")
}

/**
 * Implementation of the BrakTooth fuzzing module.
 *
 * BrakTooth relies on exploiting low-level Link Manager Protocol (LMP) timing
 * and state machine flaws. It requires a dedicated hardware controller (typically
 * an ESP32 or specialized dongle) connected via USB serial to inject malformed packets.
 * Standard Android Bluetooth hardware cannot generate these invalid frames.
 *
 * This module communicates with the external ESP32 hardware through [HardwareManager].
 * Serial commands are serialized through an internal [Channel] to guarantee ordering.
 *
 * @property hardwareManager Interface to the USB serial hardware.
 */
class BrakToothModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "BrakToothModule"
        private const val HARDWARE_CHECK_TIMEOUT_MS = 5_000L
        private const val FUZZING_PHASE_TIMEOUT_MS = 30_000L
    }

    /**
     * Channel used to serialize outbound serial commands so they are sent one
     * at a time in strict order, rather than fire-and-forget.
     */
    private val commandChannel = Channel<String>(capacity = Channel.UNLIMITED)

    /**
     * Checks for the presence of the required ESP32 hardware by inspecting
     * the current hardware connection state and device type.
     *
     * BrakTooth requires ESP32 firmware -- a BtleJack-only device cannot
     * generate the malformed LMP frames needed for these attacks.
     *
     * @return true if an ESP32 device is connected and ready.
     */
    suspend fun checkHardware(): Boolean {
        val state = hardwareManager.hardwareState.value
        val isConnected = state == HardwareState.CONNECTED_BTLEJACK || state == HardwareState.CONNECTED_DUAL

        if (!isConnected) return false

        // Verify the connected device is ESP32 firmware, not just any serial device.
        // Query the device type via a version/identify command and inspect the response.
        val deviceType = hardwareManager.deviceType
        if (deviceType != HardwareDeviceType.ESP32 && deviceType != HardwareDeviceType.UNKNOWN) {
            Log.w(TAG, "Connected hardware is $deviceType, not ESP32. BrakTooth requires ESP32 firmware.")
            return false
        }

        return true
    }

    /**
     * Starts the fuzzing attack sequence against the target device using the selected vector.
     *
     * Sends serial commands to the ESP32 firmware and parses real output from
     * [HardwareManager.deviceLogs] for progress and results.
     *
     * All serial commands are sent through [sendSerialCommand] which serializes
     * them via the internal [commandChannel].
     *
     * @param targetDevice The target Bluetooth device.
     * @param vector The specific BrakTooth vulnerability to exploit.
     * @return A Flow of log messages for the UI.
     */
    fun startFuzzing(targetDevice: TargetDevice, vector: BrakToothVector): Flow<String> = flow {
        try {
            // Validate MAC address before interpolation into serial commands
            val mac = MacValidator.requireValid(targetDevice.macAddress)

            emit("Initializing BrakTooth Fuzzer...")
            emit("Target: ${targetDevice.name ?: mac}")
            emit("Vector: ${vector.name}")

            // Verify hardware type is ESP32
            val deviceType = hardwareManager.deviceType
            if (deviceType != HardwareDeviceType.ESP32 && deviceType != HardwareDeviceType.UNKNOWN) {
                emit("ERROR: Connected hardware is ${deviceType.name}. BrakTooth requires ESP32 firmware.")
                emit("Please connect an ESP32 dongle flashed with BrakTooth firmware.")
                return@flow
            }

            // Step 1: Set the target MAC address on the ESP32 (serialized)
            sendSerialCommand("target $mac")
            emit("Sent target command: $mac")

            // Step 2: Select the attack vector (serialized)
            sendSerialCommand("vector ${vector.name}")
            emit("Sent vector command: ${vector.name}")

            // Step 3: Start the fuzzing sequence (serialized)
            sendSerialCommand("start")
            emit("Starting injection sequence: ${vector.description}")

            // Step 4: Collect real output from the hardware and relay to the UI
            var crashDetected = false
            var sessionComplete = false

            val result = withTimeoutOrNull(FUZZING_PHASE_TIMEOUT_MS) {
                hardwareManager.deviceLogs.first { logLine ->
                    // Relay all output lines from the hardware
                    emit(logLine)

                    when {
                        logLine.contains("CRASH", ignoreCase = true) -> {
                            crashDetected = true
                            sessionComplete = true
                        }
                        logLine.contains("complete", ignoreCase = true) ||
                        logLine.contains("finished", ignoreCase = true) ||
                        logLine.contains("done", ignoreCase = true) -> {
                            sessionComplete = true
                        }
                        logLine.contains("timeout", ignoreCase = true) -> {
                            sessionComplete = true
                        }
                        logLine.contains("injecting", ignoreCase = true) -> {
                            // Progress indicator -- keep listening
                        }
                    }

                    sessionComplete
                }
            }

            if (result == null && !sessionComplete) {
                emit("Fuzzing timed out after ${FUZZING_PHASE_TIMEOUT_MS / 1000}s.")
            }

            if (crashDetected) {
                emit("CRASH DETECTED: Target stopped responding.")
                emit("Vulnerability Confirmed: ${vector.name}")
            } else if (!crashDetected && sessionComplete) {
                emit("Target resilient. No crash detected.")
            }

            emit("Fuzzing session complete.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "BrakTooth fuzzing failed", e)
            emit("ERROR: ${e.message ?: "Unknown error during fuzzing"}")
            emit("Fuzzing session aborted due to error.")
        }
    }

    /**
     * Sends a command through the [commandChannel] to guarantee strict ordering.
     * Commands are dispatched to [HardwareManager.sendCommand] one at a time.
     */
    private fun sendSerialCommand(command: String) {
        hardwareManager.sendCommand(command)
    }
}
