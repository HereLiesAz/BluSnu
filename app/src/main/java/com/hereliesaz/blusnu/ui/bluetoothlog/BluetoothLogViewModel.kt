package com.hereliesaz.blusnu.ui.bluetoothlog

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
    val selectedDevice: com.hereliesaz.blusnu.data.TargetDevice? = null,
    val devices: List<com.hereliesaz.blusnu.data.TargetDevice> = emptyList(),
    /** Set to true when the UI should launch the SAF CreateDocument picker. */
    val requestSaveFile: Boolean = false
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
            bluetoothLog.logs.collect { entry ->
                val formatted = "[${entry.level}] ${entry.message}"
                _state.update { it.copy(
                    logs = it.logs + formatted,
                    originalLogs = it.originalLogs + formatted
                ) }
            }
        }

        // Populate device list for the picker.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _state.update { it.copy(devices = allDevices) }
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

    /**
     * Signals the UI to launch the SAF CreateDocument picker.
     */
    fun onSaveToFile() {
        _state.update { it.copy(requestSaveFile = true) }
    }

    /**
     * Called after the SAF picker returns. Resets the request flag.
     */
    fun onSaveFileRequestHandled() {
        _state.update { it.copy(requestSaveFile = false) }
    }

    /**
     * Writes the current logs to the user-chosen URI via the Storage Access Framework.
     */
    fun writeLogsToUri(uri: Uri) {
        val logs = _state.value.logs.joinToString("\n")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(logs.toByteArray())
                }
                log("Saved logs to selected file.")
            } catch (e: Exception) {
                log("Failed to save logs: ${e.message}")
            }
        }
    }

    private fun log(message: String) {
        _state.update { it.copy(logs = it.logs + message) }
    }
}
