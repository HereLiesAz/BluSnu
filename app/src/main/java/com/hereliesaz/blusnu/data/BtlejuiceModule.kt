package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class BtlejuiceState {
    IDLE,
    PROXYING,
    INTERCEPTING
}

/**
 * A simulated Btlejuice (BLE Man-in-the-Middle) attack module.
 *
 * This class uses two Bluetooth adapters (one internal, one external via
 * HardwareManager) to create a proxy between a central and a peripheral.
 */
class BtlejuiceModule(private val hardwareManager: HardwareManager) {

    private val _state = MutableStateFlow(BtlejuiceState.IDLE)
    val state = _state.asStateFlow()

    private val _interceptedTraffic = MutableSharedFlow<String>()
    val interceptedTraffic = _interceptedTraffic.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var proxyJob: Job? = null

    fun startProxy(target: TargetDevice) {
        if (proxyJob?.isActive == true) return

        proxyJob = scope.launch {
            _state.value = BtlejuiceState.PROXYING
            hardwareManager.sendCommand("btlejuice --target ${target.macAddress}")
            _interceptedTraffic.emit("Spoofing ${target.name}...")
            delay(3000)

            _state.value = BtlejuiceState.INTERCEPTING
            _interceptedTraffic.emit("Proxy established. Intercepting traffic...")

            while (isActive) {
                delay(2500)
                _interceptedTraffic.emit("[READ] Handle 0x002A (Device Name)")
                delay(500)
                _interceptedTraffic.emit("[WRITE] Handle 0x0010, Value=0x01")
                delay(3000)
                _interceptedTraffic.emit("[NOTIFY] Handle 0x0012, Value=0x54-0x45-0x4D-0x50-3A-32-33-43")
            }
        }
    }

    fun stopProxy() {
        proxyJob?.cancel()
        proxyJob = null
        scope.launch {
            _state.value = BtlejuiceState.IDLE
            hardwareManager.sendCommand("stop")
            _interceptedTraffic.emit("Proxy stopped.")
        }
    }
}
