package com.hereliesaz.blusnu.ui.gattfuzzing

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.GattFuzzingModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GattFuzzingViewModel : ViewModel() {

    private val _macAddress = MutableStateFlow("")
    val macAddress: StateFlow<String> = _macAddress

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val gattFuzzingModule = GattFuzzingModule()
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    var hasPermissions = false

    fun onMacAddressChanged(macAddress: String) {
        _macAddress.value = macAddress
    }

    fun startAttack() {
        if (!hasPermissions) {
            _status.value = "Bluetooth connect permission is required"
            return
        }

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
