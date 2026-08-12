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
import kotlinx.coroutines.flow.onCompletion

/**
 * Enum representing the available RF jamming modes.
 */
enum class JammingMode {
    /** Jam the entire 2.4 GHz ISM band (2400-2483.5 MHz). */
    BROADBAND_24GHZ,
    /** Jam specific Bluetooth channels (selective denial). */
    SELECTIVE_CHANNEL,
    /** Synchronized to the target's Adaptive Frequency Hopping (AFH) sequence. */
    HOPPING_SYNCHRONIZED,
    /** Jam BLE advertising channels 37, 38, and 39 only. */
    BLE_ADV_CHANNELS
}

/**
 * Module responsible for RF Jamming / Selective Denial attacks.
 *
 * Performs targeted RF jamming of Bluetooth connections by transmitting noise
 * on exact frequencies and time slots. When synchronized to a target's
 * frequency hopping sequence, this forces supervision timeout disconnections
 * with precision.
 *
 * Requires external hardware -- SDR (HackRF/YARD Stick) or ESP32-BlueJammer --
 * because standard Android Bluetooth chipsets cannot transmit arbitrary RF noise.
 * This class acts as the controller for that external hardware, sending commands
 * via the [HardwareManager] and collecting device log output.
 *
 * Targets both BR/EDR and BLE at the PHY / Baseband layer.
 *
 * @property hardwareManager Interface to the external radio hardware.
 */
class RfJammingModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "RfJammingModule"
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var jammingJob: Job? = null

    /**
     * Checks whether the external hardware is currently connected and ready.
     *
     * @return true if hardware is in a connected state.
     */
    fun isHardwareConnected(): Boolean {
        val state = hardwareManager.hardwareState.value
        return state == HardwareState.CONNECTED_BTLEJACK ||
                state == HardwareState.CONNECTED_DUAL
    }

    /**
     * Starts RF jamming in the specified mode.
     *
     * For modes that target a specific device ([JammingMode.SELECTIVE_CHANNEL],
     * [JammingMode.HOPPING_SYNCHRONIZED]), a [targetDevice] must be provided.
     * The target's MAC address is validated via [MacValidator.requireValid]
     * before being interpolated into the hardware command.
     *
     * For broadband modes ([JammingMode.BROADBAND_24GHZ], [JammingMode.BLE_ADV_CHANNELS]),
     * [targetDevice] is optional and ignored if null.
     *
     * Sends commands to external hardware via [HardwareManager.sendCommand] in
     * the format: `jam -m MODE [-t MAC]`
     *
     * @param mode The jamming mode to use.
     * @param targetDevice The device to target (required for selective/synchronized modes).
     * @return A [Flow] of log strings from the hardware during the jamming operation.
     * @throws IllegalArgumentException if a targeted mode is used without a valid target MAC.
     */
    fun startJamming(mode: JammingMode, targetDevice: TargetDevice?): Flow<String> = flow {
        // Validate target for modes that require one
        val requiresTarget = mode == JammingMode.SELECTIVE_CHANNEL ||
                mode == JammingMode.HOPPING_SYNCHRONIZED
        if (requiresTarget) {
            requireNotNull(targetDevice) {
                "Target device required for ${mode.name} jamming mode."
            }
            // Validate MAC before interpolating into serial command
            MacValidator.requireValid(targetDevice.macAddress)
        }

        if (!isHardwareConnected()) {
            emit("[ERROR] No hardware connected. Connect SDR or ESP32 first.")
            return@flow
        }

        _isRunning.value = true

        // Build the hardware command
        val modeArg = when (mode) {
            JammingMode.BROADBAND_24GHZ -> "broadband"
            JammingMode.SELECTIVE_CHANNEL -> "selective"
            JammingMode.HOPPING_SYNCHRONIZED -> "hopping"
            JammingMode.BLE_ADV_CHANNELS -> "adv"
        }

        val command = if (targetDevice != null && requiresTarget) {
            "jam -m $modeArg -t ${targetDevice.macAddress}"
        } else {
            "jam -m $modeArg"
        }

        emit("[*] Starting RF jamming in ${mode.name} mode...")
        if (targetDevice != null && requiresTarget) {
            emit("[*] Target: ${targetDevice.name ?: "Unknown"} (${targetDevice.macAddress})")
        }
        emit("[*] Sending command: $command")

        hardwareManager.sendCommand(command)

        // Collect hardware logs and re-emit them
        hardwareManager.deviceLogs
            .onCompletion {
                _isRunning.value = false
            }
            .collect { logLine ->
                emit(logLine)
            }
    }

    /**
     * Stops any active jamming operation and sends the stop command to hardware.
     */
    fun stopJamming() {
        jammingJob?.cancel()
        jammingJob = null
        _isRunning.value = false
        hardwareManager.sendCommand("stop")
        Log.d(TAG, "Jamming stopped.")
    }

    /**
     * Releases all resources: stops any active jamming and cancels the coroutine scope.
     */
    fun close() {
        stopJamming()
        scope.cancel()
    }
}
