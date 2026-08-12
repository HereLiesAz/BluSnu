package com.hereliesaz.blusnu.ui.bluesnarfing

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.BluesnarfingModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class BluesnarfingViewModel(application: Application, deviceRepository: DeviceRepository) : AndroidViewModel(application) {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _result = MutableStateFlow("")
    val result: StateFlow<String> = _result

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val bluesnarfingModule = BluesnarfingModule()
    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    private var attackJob: Job? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // Bluesnarfing targets BR/EDR (Classic). Filter out BLE-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun startAttack() {
        if (_isRunning.value) return

        val selected = _selectedDevice.value ?: return
        ActionLogger.log("Bluesnarfing attack started against ${selected.macAddress}.")

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

        // Finding 4.12: Clear stale results before starting a new attack run
        _result.value = ""
        _status.value = ""

        _isRunning.value = true
        attackJob = viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                // Finding 4.7: Rethrow CancellationException instead of swallowing it
                if (e is CancellationException) throw e
                _status.value = "Error: ${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stopAttack() {
        // Finding 4.8: Close the socket to interrupt any blocking RFCOMM read,
        // then cancel the coroutine. The blocking read() will throw IOException
        // which the module's catch block handles gracefully.
        bluesnarfingModule.closeActiveSocket()
        attackJob?.cancel()
        attackJob = null
        _isRunning.value = false
        _status.value = "Attack stopped."
    }

    override fun onCleared() {
        super.onCleared()
        bluesnarfingModule.closeActiveSocket()
        attackJob?.cancel()
    }
}
