package com.hereliesaz.blusnu.ui.bluffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.BluffsMode
import com.hereliesaz.blusnu.data.BluffsModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BluffsViewModel(
    private val deviceRepository: DeviceRepository,
    private val bluffsModule: BluffsModule
) : ViewModel() {

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _selectedMode = MutableStateFlow(BluffsMode.A1)
    val selectedMode: StateFlow<BluffsMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var attackJob: Job? = null
    private var currentTaskId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun selectDevice(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun selectMode(mode: BluffsMode) {
        _selectedMode.value = mode
    }

    fun startAttack() {
        val device = _selectedDevice.value ?: return
        if (_isRunning.value) return

        _isRunning.value = true
        _logs.value = emptyList()

        val taskId = "bluffs_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("BLUFFS: Starting ${_selectedMode.value.name} attack on ${device.name ?: device.macAddress}")
        ActiveTaskManager.add(taskId, "BLUFFS Attack", "${_selectedMode.value.name} on ${device.name ?: device.macAddress}")

        attackJob = viewModelScope.launch {
            try {
                bluffsModule.startAttack(device, _selectedMode.value).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("BLUFFS: Finished attack on ${device.name ?: device.macAddress}")
                ActionLogger.log("Result: $resultSummary")
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
        _isRunning.value = false
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        ActionLogger.log("BLUFFS: Attack stopped by user")
    }

    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
