package com.hereliesaz.blusnu.ui.gattfuzzing

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GattFuzzingModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GattFuzzingViewModel(application: Application, deviceRepository: DeviceRepository) : AndroidViewModel(application) {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _result = MutableStateFlow("")
    val result: StateFlow<String> = _result

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val gattFuzzingModule = GattFuzzingModule()
    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect {
                _devices.value = it
            }
        }
        // Collect log messages from the module
        viewModelScope.launch {
            gattFuzzingModule.log.collect { msg ->
                _logMessages.value = _logMessages.value + msg
            }
        }
    }

    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun startAttack() {
        val selected = _selectedDevice.value ?: return
        ActionLogger.log("GATT Fuzzing attack started against ${selected.macAddress}.")

        if (bluetoothAdapter == null) {
            _status.value = "Bluetooth is not supported on this device"
            return
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(selected.macAddress)
        } catch (e: IllegalArgumentException) {
            _status.value = "Invalid MAC address"
            return
        }

        _isRunning.value = true
        _logMessages.value = emptyList()
        _result.value = ""

        viewModelScope.launch {
            _status.value = "Connecting to GATT server on ${device.address}..."
            val result = withContext(Dispatchers.IO) {
                gattFuzzingModule.executeAttack(getApplication(), device)
            }
            _result.value = result
            _status.value = "GATT fuzzing finished."
            _isRunning.value = false
        }
    }
}
