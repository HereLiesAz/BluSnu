package com.hereliesaz.blusnu.ui.bluffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluffsMode
import com.hereliesaz.blusnu.data.BluffsModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the BLUFFS attack screen.
 *
 * Coordinates device selection and attack execution via [BluffsModule].
 */
class BluffsViewModel(
    private val deviceRepository: DeviceRepository,
    private val bluffsModule: BluffsModule
) : ViewModel() {

    // List of eligible targets.
    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    // Currently selected target.
    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    // Selected attack vector/mode.
    private val _selectedMode = MutableStateFlow(BluffsMode.A1)
    val selectedMode: StateFlow<BluffsMode> = _selectedMode

    // Busy state.
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    // Real-time logs from the attack module.
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // BLUFFS targets BR/EDR (Classic). Filter out BLE-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    /**
     * Updates selected device.
     */
    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    /**
     * Updates selected attack mode.
     */
    fun selectMode(mode: BluffsMode) {
        _selectedMode.value = mode
    }

    /**
     * Starts the attack coroutine.
     */
    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        viewModelScope.launch {
            // Collect logs from the Flow returned by the module.
            bluffsModule.startAttack(device, _selectedMode.value).collect { log ->
                _logs.value = _logs.value + log
            }
            _isRunning.value = false
        }
    }
}
