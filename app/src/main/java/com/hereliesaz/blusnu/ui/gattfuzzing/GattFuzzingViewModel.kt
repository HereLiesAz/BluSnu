package com.hereliesaz.blusnu.ui.gattfuzzing

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.GattFuzzingModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GattFuzzingViewModel(application: Application) : AndroidViewModel(application) {

    private val _macAddress = MutableStateFlow("")
    val macAddress: StateFlow<String> = _macAddress

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val gattFuzzingModule = GattFuzzingModule()
    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    var hasPermissions = false

    fun onMacAddressChanged(macAddress: String) {
        _macAddress.value = macAddress
    }

    fun startAttack() {
        if (!hasPermissions) {
            _status.value = "Bluetooth connect permission is required"
            return
        }

        ActionLogger.log("GATT Fuzzing attack started against ${_macAddress.value}.")

        if (bluetoothAdapter == null) {
            _status.value = "Bluetooth is not supported on this device"
            return
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(_macAddress.value)
        } catch (e: IllegalArgumentException) {
            _status.value = "Invalid MAC address"
            return
        }

        viewModelScope.launch {
            _status.value = "Starting attack..."
            withContext(Dispatchers.IO) {
                gattFuzzingModule.executeAttack(device)
            }
            _status.value = "Attack finished."
        }
    }
}
