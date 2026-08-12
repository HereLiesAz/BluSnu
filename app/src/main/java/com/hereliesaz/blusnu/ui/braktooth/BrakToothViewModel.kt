package com.hereliesaz.blusnu.ui.braktooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.BrakToothModule
import com.hereliesaz.blusnu.data.BrakToothVector
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for BrakTooth functionality.
 *
 * Interfaces with [BrakToothModule] to control the ESP32 hardware and execute attacks.
 * Integrates with [ActionLogger] for audit trail and [ActiveTaskManager] for dashboard
 * task tracking.
 */
class BrakToothViewModel(
    private val deviceRepository: DeviceRepository,
    private val brakToothModule: BrakToothModule
) : ViewModel() {

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    // Default to the most common vector (V1).
    private val _selectedVector = MutableStateFlow(BrakToothVector.V1_LMP_Feature_Response_Flooding)
    val selectedVector: StateFlow<BrakToothVector> = _selectedVector

    private val _hardwareConnected = MutableStateFlow(false)
    val hardwareConnected: StateFlow<Boolean> = _hardwareConnected

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var fuzzingJob: Job? = null
    private var currentTaskId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // BrakTooth targets Classic/BR/EDR stacks.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectVector(vector: BrakToothVector) {
        _selectedVector.value = vector
    }

    /**
     * Attempts to handshake with the external hardware.
     */
    fun checkHardware() {
        viewModelScope.launch {
            _logs.value = _logs.value + "Scanning for ESP32 (BrakTooth) Dongle..."
            val connected = brakToothModule.checkHardware()
            _hardwareConnected.value = connected
            if (connected) {
                _logs.value = _logs.value + "Hardware CONNECTED (ESP32 detected)."
            } else {
                _logs.value = _logs.value + "ESP32 hardware NOT FOUND. BrakTooth requires an ESP32 dongle flashed with BrakTooth firmware."
            }
        }
    }

    /**
     * Initiates the selected fuzzing vector.
     *
     * The entire flow collection is wrapped in try-catch so that any exception
     * from the module (serial I/O failure, MAC validation, etc.) surfaces as an
     * error log instead of freezing the UI in the "running" state.
     */
    fun startFuzzing() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return
        if (!_hardwareConnected.value) {
            _logs.value = _logs.value + "ERROR: Hardware not connected."
            return
        }

        _isRunning.value = true
        _logs.value = emptyList()

        val taskId = "braktooth_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("BrakTooth: Starting ${_selectedVector.value.name} on ${device.name ?: device.macAddress}")
        ActiveTaskManager.add(taskId, "BrakTooth Fuzzer", "${_selectedVector.value.name} on ${device.name ?: device.macAddress}")

        fuzzingJob = viewModelScope.launch {
            try {
                brakToothModule.startFuzzing(device, _selectedVector.value).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("BrakTooth: Finished fuzzing ${device.name ?: device.macAddress}")
                ActionLogger.log("Result: $resultSummary")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = "ERROR: ${e.message ?: "Unknown error"}"
                _logs.value = _logs.value + errorMsg
                ActionLogger.log("BrakTooth: Error during fuzzing -- ${e.message}")
            } finally {
                _isRunning.value = false
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
                fuzzingJob = null
            }
        }
    }

    fun stopFuzzing() {
        fuzzingJob?.cancel()
        fuzzingJob = null
        _isRunning.value = false
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        ActionLogger.log("BrakTooth: Fuzzing stopped by user")
    }

    override fun onCleared() {
        super.onCleared()
        fuzzingJob?.cancel()
        fuzzingJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
