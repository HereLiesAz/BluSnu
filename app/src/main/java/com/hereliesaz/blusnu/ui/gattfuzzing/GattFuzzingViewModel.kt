package com.hereliesaz.blusnu.ui.gattfuzzing

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GattFuzzingModule
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the GATT Fuzzing feature.
 *
 * Handles target selection (BLE only) and triggers the [GattFuzzingModule] logic.
 */
class GattFuzzingViewModel(application: Application, deviceRepository: DeviceRepository) : AndroidViewModel(application) {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    private val gattFuzzingModule = GattFuzzingModule()
    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    var hasPermissions = false

    init {
        // Collect BLE devices only.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    /**
     * Starts the fuzzing session.
     */
    fun startAttack() {
        if (!hasPermissions) {
            _status.value = "Bluetooth connect permission is required"
            return
        }

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

        viewModelScope.launch {
            _status.value = "Starting attack..."
            // Execute on IO thread.
            withContext(Dispatchers.IO) {
                gattFuzzingModule.executeAttack(device)
            }
            _status.value = "Attack finished."
        }
    }
}
