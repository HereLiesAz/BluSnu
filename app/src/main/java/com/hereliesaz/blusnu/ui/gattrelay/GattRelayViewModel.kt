package com.hereliesaz.blusnu.ui.gattrelay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.GattRelayModule
import com.hereliesaz.blusnu.data.RelayRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GattRelayViewModel(application: Application) : AndroidViewModel(application) {
    private val gattRelayModule = GattRelayModule(application)

    private val _selectedRole = MutableStateFlow(RelayRole.NODE_A_CAR_SIDE)
    val selectedRole: StateFlow<RelayRole> = _selectedRole

    private val _targetAddress = MutableStateFlow("")
    val targetAddress: StateFlow<String> = _targetAddress

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var relayJob: Job? = null
    private var currentTaskId: String? = null

    fun selectRole(role: RelayRole) {
        _selectedRole.value = role
    }

    fun updateTargetAddress(address: String) {
        _targetAddress.value = address
    }

    fun startRelay() {
        if (_isRunning.value) return
        _isRunning.value = true
        _logs.value = emptyList()

        val role = _selectedRole.value
        val taskId = "gattrelay_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("GATT Relay: Starting as ${role.name}")
        ActiveTaskManager.add(taskId, "GATT Relay", role.name)

        relayJob = viewModelScope.launch {
            try {
                gattRelayModule.startRelay(role, _targetAddress.value).collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("GATT Relay: Finished on ${role.name}")
                ActionLogger.log("Result: $resultSummary")
            } finally {
                _isRunning.value = false
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
                relayJob = null
            }
        }
    }

    fun stopRelay() {
        relayJob?.cancel()
        relayJob = null
        gattRelayModule.stop()
        _isRunning.value = false
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        ActionLogger.log("GATT Relay: Stopped by user")
    }

    override fun onCleared() {
        super.onCleared()
        relayJob?.cancel()
        relayJob = null
        gattRelayModule.stop()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
