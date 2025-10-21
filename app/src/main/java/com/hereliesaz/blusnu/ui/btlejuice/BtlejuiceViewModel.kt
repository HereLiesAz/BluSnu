package com.hereliesaz.blusnu.ui.btlejuice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BtlejuiceViewModel(
    private val hardwareManager: HardwareManager,
    private val btlejuiceModule: BtlejuiceModule,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    val hardwareState = hardwareManager.hardwareState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HardwareState.DISCONNECTED
    )

    val btlejuiceState = btlejuiceModule.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.hereliesaz.blusnu.data.BtlejuiceState.IDLE
    )

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _gattTraffic = MutableStateFlow<List<String>>(emptyList())
    val gattTraffic = _gattTraffic.asStateFlow()

    val discoveredDevices = deviceRepository.discoveredDevices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            hardwareManager.deviceLogs.collect {
                _logs.value = _logs.value + it
            }
        }
    }

    fun onConnectHardware() {
        hardwareManager.connect()
    }

    fun onConnectDual() {
        hardwareManager.connectDual()
    }

    fun onStartProxy(targetDevice: TargetDevice) {
        btlejuiceModule.startProxy(targetDevice)
    }

    fun onStopProxy() {
        btlejuiceModule.stopProxy()
    }
}
