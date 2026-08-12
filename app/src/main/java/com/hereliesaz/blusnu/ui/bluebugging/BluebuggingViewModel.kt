package com.hereliesaz.blusnu.ui.bluebugging

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.BluebuggingModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BluebuggingViewModel(application: Application, deviceRepository: DeviceRepository) : AndroidViewModel(application) {

    private val bluebuggingModule = BluebuggingModule()

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

    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    private var attackJob: Job? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // Bluebugging targets BR/EDR (Classic). Filter out BLE-only devices.
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

        if (bluetoothAdapter == null) {
            _status.value = "Bluetooth is not supported on this device"
            return
        }

        val selected = _selectedDevice.value ?: return
        val device = try {
            bluetoothAdapter.getRemoteDevice(selected.macAddress)
        } catch (e: IllegalArgumentException) {
            _status.value = "Invalid MAC address"
            return
        }

        ActionLogger.log("Bluebugging attack started against ${selected.macAddress}")

        _isRunning.value = true
        attackJob = viewModelScope.launch {
            try {
                val output = StringBuilder()
                bluebuggingModule.executeCommonCommands(device).collect { line ->
                    _status.value = line
                    output.appendLine(line)
                }
                _result.value = output.toString()
                ActionLogger.log("Bluebugging attack finished against ${selected.macAddress}")
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
        bluebuggingModule.disconnect()
        _status.value = "Attack stopped."
    }

    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        bluebuggingModule.disconnect()
    }
}
