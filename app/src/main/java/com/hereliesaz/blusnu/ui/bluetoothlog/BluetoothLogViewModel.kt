package com.hereliesaz.blusnu.ui.bluetoothlog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluetoothLogEntry
import com.hereliesaz.blusnu.data.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BluetoothLogState(
    val logs: List<BluetoothLogEntry> = emptyList(),
    val originalLogs: List<BluetoothLogEntry> = emptyList(),
    val filter: String = "",
    val minLogLevel: LogLevel = LogLevel.VERBOSE,
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
            bluetoothLog.logs.collect { logEntry ->
                _state.update { currentState ->
                    val newOriginalLogs = currentState.originalLogs + logEntry
                    val filteredLogs = filterLogs(newOriginalLogs, currentState.filter, currentState.minLogLevel)
                    currentState.copy(
                        logs = filteredLogs,
                        originalLogs = newOriginalLogs
                    )
                }
            }
        }
    }

    fun onFilterChanged(newFilter: String) {
        _state.update {
            val filteredLogs = filterLogs(it.originalLogs, newFilter, it.minLogLevel)
            it.copy(filter = newFilter, logs = filteredLogs)
        }
    }

    fun onLogLevelChanged(level: LogLevel) {
        _state.update {
            val filteredLogs = filterLogs(it.originalLogs, it.filter, level)
            it.copy(minLogLevel = level, logs = filteredLogs)
        }
    }

    private fun filterLogs(logs: List<BluetoothLogEntry>, filter: String, minLevel: LogLevel): List<BluetoothLogEntry> {
        return logs.filter { entry ->
            entry.level.ordinal >= minLevel.ordinal &&
            (filter.isEmpty() || entry.message.contains(filter, ignoreCase = true))
        }
    }

    fun onDeviceSelected(device: com.hereliesaz.blusnu.data.TargetDevice) {
        _state.update { it.copy(selectedDevice = device) }
    }

    fun onSaveToNotes() {
        val device = _state.value.selectedDevice ?: return
        val logs = _state.value.logs.joinToString("\n") { "${it.timestamp} [${it.level}] ${it.message}" }
        viewModelScope.launch {
            deviceRepository.updateNotes(device.macAddress, logs)
        }
    }

    fun onSaveToFile() {
        val logs = _state.value.logs.joinToString("\n") { "${it.timestamp} [${it.level}] ${it.message}" }
        val file = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "bluetooth_log.txt"
        )
        // Note: Writing to external storage requires permissions and careful handling in modern Android.
        // Assuming permissions are handled elsewhere or this is for debug builds.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                file.writeText(logs)
                bluetoothLog.log("Saved logs to ${file.absolutePath}", LogLevel.INFO)
            } catch (e: Exception) {
                bluetoothLog.log("Failed to save logs: ${e.message}", LogLevel.ERROR)
            }
        }
    }
}
