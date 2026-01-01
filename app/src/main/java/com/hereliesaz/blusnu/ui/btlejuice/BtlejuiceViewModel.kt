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
        // TODO: Implement hardware connection logic
    }

    fun onConnectDual() {
        // TODO: Implement dual connection logic
    }

    fun onStartProxy(targetDevice: TargetDevice?) {
        // TODO: Implement proxy start logic
    }

    fun onStopProxy() {
        // TODO: Implement proxy stop logic
    }
}
