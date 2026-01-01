package com.hereliesaz.blusnu.ui.braktooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BrakToothModule
import com.hereliesaz.blusnu.data.BrakToothVector
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BrakToothViewModel(private val deviceRepository: DeviceRepository) : ViewModel() {
    private val brakToothModule = BrakToothModule()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedVector = MutableStateFlow(BrakToothVector.V1_LMP_Feature_Response_Flooding)
    val selectedVector: StateFlow<BrakToothVector> = _selectedVector

    private val _hardwareConnected = MutableStateFlow(false)
    val hardwareConnected: StateFlow<Boolean> = _hardwareConnected

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // BrakTooth targets Classic/BR/EDR stacks
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectVector(vector: BrakToothVector) {
        _selectedVector.value = vector
    }

    fun checkHardware() {
        viewModelScope.launch {
            _logs.value = _logs.value + "Scanning for ESP32 (BrakTooth) Dongle..."
            val connected = brakToothModule.checkHardware()
            _hardwareConnected.value = connected
            if (connected) {
                _logs.value = _logs.value + "Hardware CONNECTED."
            } else {
                _logs.value = _logs.value + "Hardware NOT FOUND. Please check USB-OTG connection."
            }
        }
    }

    fun startFuzzing() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return
        if (!_hardwareConnected.value) {
            _logs.value = _logs.value + "ERROR: Hardware not connected."
            return
        }

        _isRunning.value = true
        _logs.value = emptyList()

        viewModelScope.launch {
            brakToothModule.startFuzzing(device, _selectedVector.value).collect { log ->
                _logs.value = _logs.value + log
            }
            _isRunning.value = false
        }
    }
}
