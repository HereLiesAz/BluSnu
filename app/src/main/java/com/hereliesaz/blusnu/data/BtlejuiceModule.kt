package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class BtlejuiceState {
    IDLE,
    PROXYING,
    INTERCEPTING
}

/**
 * A placeholder for the Btlejuice (BLE Man-in-the-Middle) attack module.
 *
 * In a real implementation, this class would use two Bluetooth adapters
 * (one internal, one external via HardwareManager) to create a proxy
 * between a central and a peripheral.
 */
class BtlejuiceModule(private val hardwareManager: HardwareManager) {

    private val _state = MutableStateFlow(BtlejuiceState.IDLE)
    val state = _state.asStateFlow()

    private val _gattTraffic = MutableSharedFlow<String>()
    val gattTraffic = _gattTraffic.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun startProxy(target: TargetDevice) {
        scope.launch {
            _state.value = BtlejuiceState.PROXYING
            Log.d("BtlejuiceModule", "Starting proxy for ${target.macAddress}")
            hardwareManager.sendCommand("btlejuice --target ${target.macAddress}")
            delay(3000)
            _state.value = BtlejuiceState.INTERCEPTING
            Log.d("BtlejuiceModule", "Proxy started, now intercepting traffic.")
            // Simulate GATT traffic
            _gattTraffic.emit("READ Request: Handle 0x0001")
            delay(500)
            _gattTraffic.emit("READ Response: Handle 0x0001, Value: 'Hello'")
            delay(500)
            _gattTraffic.emit("WRITE Request: Handle 0x0002, Value: 'World'")
            delay(500)
            _gattTraffic.emit("WRITE Response: Handle 0x0002")
        }
    }

    fun stopProxy() {
        scope.launch {
            _state.value = BtlejuiceState.IDLE
            hardwareManager.sendCommand("stop")
            Log.d("BtlejuiceModule", "Proxy stopped.")
        }
    }
}
