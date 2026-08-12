package com.hereliesaz.blusnu.ui.gattfuzzing

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GattFuzzingModule
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
    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    private var attackJob: Job? = null
    private var currentTaskId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL
                }
            }
        }
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
        if (_isRunning.value) return
        val selected = _selectedDevice.value ?: return

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

        val taskId = "gatt_fuzz_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("GATT Fuzzing: Starting against ${selected.macAddress}")
        ActiveTaskManager.add(taskId, "GATT Fuzzer", "Fuzzing ${selected.name ?: selected.macAddress}")

        attackJob = viewModelScope.launch {
            try {
                _status.value = "Connecting to GATT server on ${device.address}..."
                val fuzzResult = withContext(Dispatchers.IO) {
                    gattFuzzingModule.executeAttack(getApplication(), device)
                }
                _result.value = fuzzResult
                _status.value = "GATT fuzzing finished."
                ActionLogger.log("GATT Fuzzing: Finished against ${selected.macAddress}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
                ActionLogger.log("GATT Fuzzing: Error — ${e.message}")
            } finally {
                _isRunning.value = false
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
                attackJob = null
            }
        }
    }

    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
    }

    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
