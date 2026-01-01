package com.hereliesaz.blusnu.ui.btlejacking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BtlejackingModule
import com.hereliesaz.blusnu.data.BtlejackingState
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class BtlejackingScreenState(
    val hardwareState: HardwareState = HardwareState.DISCONNECTED,
    val btlejackingState: BtlejackingState = BtlejackingState.IDLE,
    val discoveredDevices: List<TargetDevice> = emptyList(),
    val deviceLogs: List<String> = emptyList()
)

class BtlejackingViewModel(
    application: Application,
    private val hardwareManager: HardwareManager,
    private val btlejackingModule: BtlejackingModule,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(BtlejackingScreenState())
    val state = _state.asStateFlow()

    init {
        hardwareManager.hardwareState
            .onEach { hardwareState ->
                _state.value = _state.value.copy(hardwareState = hardwareState)
            }
            .launchIn(viewModelScope)

        btlejackingModule.state
            .onEach { btlejackingState ->
                _state.value = _state.value.copy(btlejackingState = btlejackingState)
            }
            .launchIn(viewModelScope)

        deviceRepository.allDevices
            .onEach { devices ->
                val bleDevices = devices.filter { it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL }
                _state.value = _state.value.copy(discoveredDevices = bleDevices)
            }
            .launchIn(viewModelScope)

        hardwareManager.deviceLogs
            .onEach { log ->
                _state.value = _state.value.copy(deviceLogs = _state.value.deviceLogs + log)
            }
            .launchIn(viewModelScope)
    }

    fun connectHardware() {
        viewModelScope.launch {
            hardwareManager.connect()
        }
    }

    fun disconnectHardware() {
        viewModelScope.launch {
            hardwareManager.disconnect()
        }
    }

    fun startAttack(target: TargetDevice) {
        viewModelScope.launch {
            btlejackingModule.startSniffing(target)
        }
    }

    fun stopAttack() {
        viewModelScope.launch {
            btlejackingModule.stop()
        }
    }
}
