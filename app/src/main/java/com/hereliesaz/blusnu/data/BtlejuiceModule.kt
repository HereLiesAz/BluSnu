package com.hereliesaz.blusnu.data

import android.util.Log
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

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
 * Uses real USB serial hardware via [HardwareManager] to perform dual-radio BLE proxying.
 * One radio acts as the "Fake Peripheral" (advertises to the Central), and the other
 * acts as the "Fake Central" (connects to the real Peripheral).
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
     */
    fun startProxy(target: TargetDevice) {
        if (proxyJob?.isActive == true) return

        proxyJob = scope.launch {
            _state.value = BtlejuiceState.PROXYING

            hardwareManager.sendCommand("btlejuice --target ${target.macAddress}")
            _interceptedTraffic.emit("Sending proxy command for ${target.name ?: target.macAddress}...")

            // Collect hardware output and look for proxy establishment or traffic
            hardwareManager.deviceLogs.collect { logLine ->
                if (!isActive) return@collect

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
     */
    fun stopProxy() {
        proxyJob?.cancel()
        proxyJob = null
        _state.value = BtlejuiceState.IDLE
        hardwareManager.sendCommand("stop")
    }

    fun close() {
        stopProxy()
        scope.cancel()
    }
}
