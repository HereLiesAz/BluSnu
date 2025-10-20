package com.hereliesaz.blusnu.ui.btlejuice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BtlejuiceModule
import com.hereliesaz.blusnu.data.BtlejuiceState
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BtlejuiceViewModel(
    application: Application,
    private val btlejuiceModule: BtlejuiceModule,
    private val hardwareManager: HardwareManager
) : AndroidViewModel(application) {

    val btlejuiceState: StateFlow<BtlejuiceState> = btlejuiceModule.state
    val hardwareState: StateFlow<HardwareState> = hardwareManager.hardwareState

    private val _deviceLogs = MutableStateFlow<List<String>>(emptyList())
    val deviceLogs: StateFlow<List<String>> = _deviceLogs.asStateFlow()

    private val _gattTraffic = MutableStateFlow<List<String>>(emptyList())
    val gattTraffic: StateFlow<List<String>> = _gattTraffic.asStateFlow()

    init {
        viewModelScope.launch {
            hardwareManager.deviceLogs.collect { log ->
                _deviceLogs.value = _deviceLogs.value + log
            }
        }
        viewModelScope.launch {
            btlejuiceModule.gattTraffic.collect { traffic ->
                _gattTraffic.value = _gattTraffic.value + traffic
            }
        }
    }

    fun connectHardware() {
        hardwareManager.connect()
    }

    fun startProxy(target: TargetDevice) {
        btlejuiceModule.startProxy(target)
    }

    fun stopProxy() {
        btlejuiceModule.stopProxy()
    }
}
