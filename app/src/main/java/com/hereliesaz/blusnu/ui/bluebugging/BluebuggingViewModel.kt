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
import kotlinx.coroutines.CancellationException
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

    /** Holds the response from the last custom AT command. */
    private val _customCommandResult = MutableStateFlow("")
    val customCommandResult: StateFlow<String> = _customCommandResult

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
            } catch (e: CancellationException) {
                // Finding 8.5: Rethrow CancellationException — do not swallow it
                throw e
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
                // Finding 8.7: Disconnect on error to clean up resources
                bluebuggingModule.disconnect()
            } finally {
                _isRunning.value = false
            }
        }
    }

    /**
     * Sends a custom AT command over the existing RFCOMM connection.
     * The device must already be connected (e.g. via startAttack or a prior connect).
     */
    fun sendCustomCommand(command: String) {
        if (command.isBlank()) return

        viewModelScope.launch {
            try {
                val result = bluebuggingModule.sendAtCommand(command)
                if (result.isSuccess) {
                    val response = result.getOrDefault("")
                    _customCommandResult.value = response.ifBlank { "(empty response)" }
                } else {
                    _customCommandResult.value = "Error: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: CancellationException) {
                // Finding 8.5: Rethrow CancellationException
                throw e
            } catch (e: Exception) {
                _customCommandResult.value = "Error: ${e.message}"
                // Finding 8.7: Disconnect on error
                bluebuggingModule.disconnect()
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
