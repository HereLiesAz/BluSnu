package com.hereliesaz.blusnu.ui.btlejuice

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BtlejuiceModule
import com.hereliesaz.blusnu.data.BtlejuiceState
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class BtlejuiceHardwareState(val isConnected: Boolean = false)
data class GattTraffic(val entries: List<String> = emptyList())

class BtlejuiceViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val hardwareManager: HardwareManager,
    private val btlejuiceModule: BtlejuiceModule
) : ViewModel() {
    private val _hardwareState = MutableStateFlow(BtlejuiceHardwareState())
    val hardwareState: StateFlow<BtlejuiceHardwareState> = _hardwareState.asStateFlow()

    private val _btlejuiceState = MutableStateFlow(BtlejuiceState.IDLE)
    val btlejuiceState: StateFlow<BtlejuiceState> = _btlejuiceState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TargetDevice>> = _discoveredDevices.asStateFlow()

    private val _gattTraffic = MutableStateFlow(GattTraffic())
    val gattTraffic: StateFlow<GattTraffic> = _gattTraffic.asStateFlow()

    init {
        // Observe discovered BLE devices from the repository
        deviceRepository.allDevices
            .onEach { allDevices ->
                _discoveredDevices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
            .launchIn(viewModelScope)

        // Observe hardware connection state
        hardwareManager.hardwareState
            .onEach { state ->
                val isConnected = state == HardwareState.CONNECTED_BTLEJACK ||
                        state == HardwareState.CONNECTED_DUAL
                _hardwareState.value = BtlejuiceHardwareState(isConnected = isConnected)
            }
            .launchIn(viewModelScope)

        // Observe btlejuice module state
        btlejuiceModule.state
            .onEach { state ->
                _btlejuiceState.value = state
            }
            .launchIn(viewModelScope)

        // Observe intercepted GATT traffic from the module
        btlejuiceModule.interceptedTraffic
            .onEach { trafficLine ->
                _gattTraffic.value = GattTraffic(
                    entries = _gattTraffic.value.entries + trafficLine
                )
            }
            .launchIn(viewModelScope)

        // Observe hardware device logs
        hardwareManager.deviceLogs
            .onEach { logMessage ->
                _logs.value = _logs.value + logMessage
            }
            .launchIn(viewModelScope)
    }

    fun onConnectHardware() {
        hardwareManager.connect()
    }

    fun onConnectDual() {
        hardwareManager.connectDual()
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
        if (_btlejuiceState.value != BtlejuiceState.IDLE) return

        btlejuiceModule.startProxy(targetDevice)
    }

    fun onStopProxy() {
        if (_btlejuiceState.value == BtlejuiceState.IDLE) return
        btlejuiceModule.stopProxy()
    }

    private fun log(message: String) {
        _logs.value = _logs.value + message
    }
}
