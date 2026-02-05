package com.hereliesaz.blusnu.ui.gattrelay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.GattRelayModule
import com.hereliesaz.blusnu.data.RelayRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the GATT Relay feature.
 *
 * Interfaces with [GattRelayModule] to manage the relay lifecycle.
 */
class GattRelayViewModel : ViewModel() {
    private val gattRelayModule = GattRelayModule()

    private val _selectedRole = MutableStateFlow(RelayRole.NODE_A_CAR_SIDE)
    val selectedRole: StateFlow<RelayRole> = _selectedRole

    private val _targetAddress = MutableStateFlow("")
    val targetAddress: StateFlow<String> = _targetAddress

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    fun selectRole(role: RelayRole) {
        _selectedRole.value = role
    }

    fun updateTargetAddress(address: String) {
        _targetAddress.value = address
    }

    /**
     * Starts the relay logic.
     */
    fun startRelay() {
        if (_isRunning.value) return
        _isRunning.value = true
        _logs.value = emptyList()

        viewModelScope.launch {
            gattRelayModule.startRelay(_selectedRole.value, _targetAddress.value).collect { log ->
                _logs.value = _logs.value + log
            }
            _isRunning.value = false
        }
    }
}
