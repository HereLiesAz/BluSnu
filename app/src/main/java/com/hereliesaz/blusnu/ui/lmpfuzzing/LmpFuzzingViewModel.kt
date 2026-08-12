package com.hereliesaz.blusnu.ui.lmpfuzzing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.LmpFuzzVector
import com.hereliesaz.blusnu.data.LmpFuzzingModule
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for the LMP/Baseband Fuzzing screen.
 *
 * Interfaces with [LmpFuzzingModule] to control ESP32 hardware or root binary
 * for injecting malformed LMP packets. Integrates with [ActionLogger] for
 * audit trail and [ActiveTaskManager] for dashboard task tracking.
 *
 * Only Classic and Dual-mode devices are shown as targets, since LMP operates
 * on BR/EDR links.
 */
class LmpFuzzingViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val lmpFuzzingModule: LmpFuzzingModule
) : AndroidViewModel(application) {

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice.asStateFlow()

    private val _selectedVector = MutableStateFlow<LmpFuzzVector?>(null)
    val selectedVector: StateFlow<LmpFuzzVector?> = _selectedVector.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    /** Handle to the current fuzzing coroutine, used for cancellation. */
    private var fuzzingJob: Job? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                // LMP operates on BR/EDR -- filter to Classic and Dual devices.
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectVector(vector: LmpFuzzVector) {
        _selectedVector.value = vector
    }

    /**
     * Starts fuzzing with the currently selected vector against the selected device.
     *
     * The entire flow collection is wrapped in try-catch so that any exception
     * from the module (serial I/O failure, MAC validation, etc.) surfaces as an
     * error log instead of freezing the UI in the "running" state.
     */
    fun startFuzzing() {
        val device = _selectedDevice.value ?: return
        val vector = _selectedVector.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val taskId = "lmp_fuzz_${System.currentTimeMillis()}"
        ActionLogger.log("LMP Fuzzing: Starting ${vector.name} on ${device.name ?: device.macAddress}")
        ActiveTaskManager.add(taskId, "LMP/Baseband Fuzzer", "${vector.name} on ${device.name ?: device.macAddress}")

        fuzzingJob = viewModelScope.launch {
            try {
                lmpFuzzingModule.startFuzzing(device, vector).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("LMP Fuzzing: Finished ${vector.name} on ${device.name ?: device.macAddress}")
                ActionLogger.log("Result: $resultSummary")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = "ERROR: ${e.message ?: "Unknown error"}"
                _logs.value = _logs.value + errorMsg
                ActionLogger.log("LMP Fuzzing: Error -- ${e.message}")
            } finally {
                _isRunning.value = false
                ActiveTaskManager.remove(taskId)
                fuzzingJob = null
            }
        }
    }

    /**
     * Runs the full suite of all LMP fuzz vectors sequentially.
     */
    fun runFullSuite() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val taskId = "lmp_fuzz_suite_${System.currentTimeMillis()}"
        ActionLogger.log("LMP Fuzzing: Starting full suite on ${device.name ?: device.macAddress}")
        ActiveTaskManager.add(taskId, "LMP/Baseband Fuzzer", "Full suite on ${device.name ?: device.macAddress}")

        fuzzingJob = viewModelScope.launch {
            try {
                lmpFuzzingModule.startFullSuite(device).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("LMP Fuzzing: Full suite finished on ${device.name ?: device.macAddress}")
                ActionLogger.log("Result: $resultSummary")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = "ERROR: ${e.message ?: "Unknown error"}"
                _logs.value = _logs.value + errorMsg
                ActionLogger.log("LMP Fuzzing: Error during full suite -- ${e.message}")
            } finally {
                _isRunning.value = false
                ActiveTaskManager.remove(taskId)
                fuzzingJob = null
            }
        }
    }

    /**
     * Stops any in-progress fuzzing session.
     *
     * Cancels the coroutine and signals the module to halt hardware/process activity.
     */
    fun stopFuzzing() {
        lmpFuzzingModule.stopFuzzing()
        fuzzingJob?.cancel()
        fuzzingJob = null
        _isRunning.value = false
        _logs.value = _logs.value + "Fuzzing stopped by user."
        ActionLogger.log("LMP Fuzzing: Stopped by user")
    }

    override fun onCleared() {
        super.onCleared()
        lmpFuzzingModule.stopFuzzing()
        fuzzingJob?.cancel()
        fuzzingJob = null
    }
}
