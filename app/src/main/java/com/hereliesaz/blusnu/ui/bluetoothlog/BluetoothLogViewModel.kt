package com.hereliesaz.blusnu.ui.bluetoothlog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BluetoothLogState(
    val logs: List<String> = emptyList(),
    val originalLogs: List<String> = emptyList(),
    val filter: String = "",
    val isFiltered: Boolean = false,
    val selectedDevice: com.hereliesaz.blusnu.data.TargetDevice? = null
)

class BluetoothLogViewModel(
    application: Application,
    private val bluetoothLog: com.hereliesaz.blusnu.data.BluetoothLog,
    private val deviceRepository: com.hereliesaz.blusnu.data.DeviceRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(BluetoothLogState())
    val state: StateFlow<BluetoothLogState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            bluetoothLog.logs.collect { log ->
                _state.update { it.copy(
                    logs = it.logs + log,
                    originalLogs = it.originalLogs + log
                ) }
            }
        }
    }

    fun onFilterChanged(newFilter: String) {
        _state.update { it.copy(filter = newFilter) }
        if (_state.value.isFiltered) {
            val filteredLogs = _state.value.originalLogs.filter { it.contains(newFilter, ignoreCase = true) }
            _state.update { it.copy(logs = filteredLogs) }
        }
    }

    fun onFilterEnabled(isEnabled: Boolean) {
        _state.update { it.copy(isFiltered = isEnabled) }
        if (isEnabled) {
            val filteredLogs = _state.value.originalLogs.filter { it.contains(_state.value.filter, ignoreCase = true) }
            _state.update { it.copy(logs = filteredLogs) }
        } else {
            _state.update { it.copy(logs = _state.value.originalLogs) }
        }
    }

    fun onDeviceSelected(device: com.hereliesaz.blusnu.data.TargetDevice) {
        _state.update { it.copy(selectedDevice = device) }
    }

    fun onSaveToNotes() {
        val device = _state.value.selectedDevice ?: return
        val logs = _state.value.logs.joinToString("\n")
        viewModelScope.launch {
            deviceRepository.updateNotes(device.macAddress, logs)
        }
    }

    fun onSaveToFile() {
        val logs = _state.value.logs.joinToString("\n")
        val file = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "bluetooth_log.txt"
        )
        file.writeText(logs)
        log("Saved logs to ${file.absolutePath}")
    }

    private fun log(message: String) {
        _state.update { it.copy(logs = it.logs + message) }
    }
}
