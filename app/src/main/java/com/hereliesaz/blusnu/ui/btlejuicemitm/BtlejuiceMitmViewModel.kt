package com.hereliesaz.blusnu.ui.btlejuicemitm

import android.app.Application
import androidx.lifecycle.ViewModel
import com.hereliesaz.blusnu.data.device.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BtlejuiceMitmHardwareState(val isConnected: Boolean = false)
data class BtlejuiceMitmState(val isProxying: Boolean = false)
data class GattMitmTraffic(val entries: List<String> = emptyList())

class BtlejuiceMitmViewModel(application: Application) : ViewModel() {
    private val _hardwareState = MutableStateFlow(BtlejuiceMitmHardwareState())
    val hardwareState: StateFlow<BtlejuiceMitmHardwareState> = _hardwareState.asStateFlow()

    private val _btlejuiceState = MutableStateFlow(BtlejuiceMitmState())
    val btlejuiceState: StateFlow<BtlejuiceMitmState> = _btlejuiceState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TargetDevice>> = _discoveredDevices.asStateFlow()

    private val _gattTraffic = MutableStateFlow(GattMitmTraffic())
    val gattTraffic: StateFlow<GattMitmTraffic> = _gattTraffic.asStateFlow()

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
