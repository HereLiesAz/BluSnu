package com.hereliesaz.blusnu.ui.btlejuice

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BtlejuiceHardwareState(val isConnected: Boolean = false)
data class BtlejuiceState(val isProxying: Boolean = false)
data class GattTraffic(val entries: List<String> = emptyList())

class BtlejuiceViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    private val _hardwareState = MutableStateFlow(BtlejuiceHardwareState())
    val hardwareState: StateFlow<BtlejuiceHardwareState> = _hardwareState.asStateFlow()

    private val _btlejuiceState = MutableStateFlow(BtlejuiceState())
    val btlejuiceState: StateFlow<BtlejuiceState> = _btlejuiceState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TargetDevice>> = _discoveredDevices.asStateFlow()

    private val _gattTraffic = MutableStateFlow(GattTraffic())
    val gattTraffic: StateFlow<GattTraffic> = _gattTraffic.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _discoveredDevices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun onConnectHardware() {
        _hardwareState.value = _hardwareState.value.copy(isConnected = true)
        log("Single Bluetooth dongle connected (simulated).")
    }

    fun onConnectDual() {
        _hardwareState.value = _hardwareState.value.copy(isConnected = true)
        log("Dual Bluetooth dongles connected — ready for MitM proxy (simulated).")
    }

    fun onStartProxy(targetDevice: TargetDevice?) {
        if (!_hardwareState.value.isConnected) {
            log("ERROR: Connect hardware before starting the proxy.")
            return
        }
        if (targetDevice == null) {
            log("ERROR: No target device selected.")
            return
        }
        if (_btlejuiceState.value.isProxying) return

        _btlejuiceState.value = _btlejuiceState.value.copy(isProxying = true)
        val label = targetDevice.name ?: targetDevice.macAddress
        log("Proxy started against $label — impersonating peripheral (simulated).")
        // Represent the intercepted-traffic view honestly as a simulation placeholder.
        _gattTraffic.value = GattTraffic(
            entries = _gattTraffic.value.entries + "[SIM] Proxy established for $label."
        )
    }

    fun onStopProxy() {
        if (!_btlejuiceState.value.isProxying) return
        _btlejuiceState.value = _btlejuiceState.value.copy(isProxying = false)
        log("Proxy stopped.")
    }

    private fun log(message: String) {
        _logs.value = _logs.value + message
    }
}
