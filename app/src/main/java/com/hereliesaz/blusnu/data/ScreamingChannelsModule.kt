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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Enum representing the available Screaming Channels capture modes.
 */
enum class ScreamingMode {
    /** Capture radio emissions from the target BLE chip via SDR. */
    CAPTURE_TRACES,
    /** Analyze captured emissions for crypto leakage patterns. */
    ANALYZE_LEAKAGE,
    /** Attempt AES key recovery from captured traces. */
    KEY_RECOVERY
}

/**
 * Module for the Screaming Channels (BlueScream) side-channel attack.
 *
 * Exploits electromagnetic leakage from mixed-signal BLE chips where digital
 * crypto activity couples into the radio transceiver and gets broadcast.
 * Demonstrated AES key recovery from an off-the-shelf BLE stack at several meters.
 *
 * Requires external hardware: an SDR (e.g., HackRF) connected via USB-OTG,
 * plus a signal processing pipeline running on the dongle firmware.
 *
 * Commands sent to the SDR via serial:
 * - `screaming -t MAC -m CAPTURE_TRACES`  : Capture radio emissions
 * - `screaming -t MAC -m ANALYZE_LEAKAGE` : Analyze for crypto leakage patterns
 * - `screaming -t MAC -m KEY_RECOVERY`    : Attempt AES key recovery from captured traces
 *
 * @property hardwareManager Interface to the external SDR hardware via USB serial.
 */
class ScreamingChannelsModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "ScreamingChannelsModule"
    }

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null

    /**
     * Start capturing electromagnetic emissions from the target BLE device.
     *
     * Sends the screaming command to the SDR hardware and returns a [Flow] of
     * parsed log lines from the device output.
     *
     * @param targetDevice The BLE device to target.
     * @param mode The [ScreamingMode] determining the capture/analysis strategy.
     * @return A [Flow] emitting raw log strings from the SDR hardware.
     * @throws IllegalArgumentException if the target MAC address is invalid.
     */
    fun startCapture(targetDevice: TargetDevice, mode: ScreamingMode): Flow<String> {
        MacValidator.requireValid(targetDevice.macAddress)

        stopCapture()
        _isCapturing.value = true
        hardwareManager.sendCommand("screaming -t ${targetDevice.macAddress} -m ${mode.name}")
        Log.d(TAG, "Started capture: mode=${mode.name}, target=${targetDevice.macAddress}")

        return hardwareManager.deviceLogs
            .onEach { line ->
                if (line.contains("error", ignoreCase = true) ||
                    line.contains("timeout", ignoreCase = true)
                ) {
                    Log.w(TAG, "SDR warning: $line")
                }
            }
            .filter { line ->
                line.contains("trace", ignoreCase = true) ||
                line.contains("leakage", ignoreCase = true) ||
                line.contains("key", ignoreCase = true) ||
                line.contains("aes", ignoreCase = true) ||
                line.contains("capture", ignoreCase = true) ||
                line.contains("signal", ignoreCase = true) ||
                line.contains("correlation", ignoreCase = true) ||
                line.contains("CMD", ignoreCase = false)
            }
    }

    /**
     * Stop any active capture operation.
     *
     * Sends a stop command to the SDR hardware and cancels the collection job.
     */
    fun stopCapture() {
        if (_isCapturing.value) {
            captureJob?.cancel()
            captureJob = null
            hardwareManager.sendCommand("screaming -s")
            _isCapturing.value = false
            Log.d(TAG, "Capture stopped.")
        }
    }

    /**
     * Check whether the SDR hardware is currently connected.
     *
     * @return `true` if the [HardwareManager] reports a connected state.
     */
    fun isHardwareConnected(): Boolean {
        val state = hardwareManager.hardwareState.value
        return state == HardwareState.CONNECTED_BTLEJACK ||
                state == HardwareState.CONNECTED_DUAL
    }

    /**
     * Releases all resources: stops capture and cancels the coroutine scope.
     */
    fun close() {
        stopCapture()
        scope.cancel()
    }
}
