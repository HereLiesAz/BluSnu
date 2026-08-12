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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Enum defining the state of the Btlejuice Proxy.
 */
enum class BtlejuiceState {
    IDLE,
    PROXYING,
    INTERCEPTING
}

/**
 * Module responsible for executing the Btlejuice-style Man-in-the-Middle (MitM) attack.
 *
 * Sends serial commands to external hardware via [HardwareManager] to proxy BLE
 * traffic through a single USB-connected radio. The hardware firmware handles the
 * low-level BLE advertisement cloning and GATT relay; this module orchestrates
 * the session lifecycle and surfaces intercepted traffic to the UI.
 *
 * @property hardwareManager Interface to control the external Bluetooth dongle/SDR.
 */
class BtlejuiceModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "BtlejuiceModule"
        private const val PROXY_SETUP_TIMEOUT_MS = 30_000L
    }

    private val _state = MutableStateFlow(BtlejuiceState.IDLE)
    val state = _state.asStateFlow()

    private val _interceptedTraffic = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val interceptedTraffic = _interceptedTraffic.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var proxyJob: Job? = null

    /**
     * Starts the MitM proxy against a specific target.
     *
     * Sends the btlejuice command to hardware and then listens for real output
     * from the device log stream to track proxy setup and traffic interception.
     *
     * @param target The BLE peripheral to impersonate and intercept.
     * @throws IllegalArgumentException if the target MAC address is invalid.
     */
    fun startProxy(target: TargetDevice) {
        if (proxyJob?.isActive == true) return

        // 3.8: Validate MAC before interpolating into serial command
        MacValidator.requireValid(target.macAddress)

        // 3.7: Launch collection in a Job that stopProxy() cancels.
        // The previous approach of `if (!isActive) return@collect` inside the
        // collect lambda does not actually stop collection -- cancelling the Job does.
        proxyJob = scope.launch {
            _state.value = BtlejuiceState.PROXYING

            hardwareManager.sendCommand("btlejuice --target ${target.macAddress}")
            _interceptedTraffic.emit("Sending proxy command for ${target.name ?: target.macAddress}...")

            // Collect hardware output and look for proxy establishment or traffic.
            // Collection ends when proxyJob is cancelled (via stopProxy/close).
            hardwareManager.deviceLogs.collect { logLine ->
                when {
                    // Proxy setup confirmed by hardware
                    logLine.contains("proxy established", ignoreCase = true) ||
                    logLine.contains("intercepting", ignoreCase = true) -> {
                        if (_state.value == BtlejuiceState.PROXYING) {
                            _state.value = BtlejuiceState.INTERCEPTING
                            _interceptedTraffic.emit("Proxy established. Intercepting traffic...")
                        }
                    }
                    // Hardware reports connection failure
                    logLine.contains("failed", ignoreCase = true) ||
                    logLine.contains("error", ignoreCase = true) -> {
                        _interceptedTraffic.emit("Hardware: $logLine")
                    }
                }

                // Forward all GATT traffic lines to the intercepted traffic flow
                if (logLine.contains("[READ]", ignoreCase = true) ||
                    logLine.contains("[WRITE]", ignoreCase = true) ||
                    logLine.contains("[NOTIFY]", ignoreCase = true) ||
                    logLine.contains("Handle", ignoreCase = true) ||
                    logLine.contains("0x", ignoreCase = true)) {
                    _interceptedTraffic.emit(logLine)
                }
            }
        }
    }

    /**
     * Stops the proxy and resets the hardware.
     * Cancelling [proxyJob] terminates the collect lambda (3.7).
     */
    fun stopProxy() {
        proxyJob?.cancel()
        proxyJob = null
        _state.value = BtlejuiceState.IDLE
        hardwareManager.sendCommand("stop")
    }

    /**
     * Releases all resources: cancels the proxy, stops hardware,
     * and cancels the coroutine scope.
     */
    fun close() {
        stopProxy()
        scope.cancel()
    }
}
