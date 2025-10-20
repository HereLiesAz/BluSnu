package com.hereliesaz.blusnu.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BtlejackingState {
    IDLE,
    SNIFFING,
    JAMMING,
    HIJACKING,
    CONNECTED
}

/**
 * A simulated implementation of the Btlejacking attack module.
 *
 * This class orchestrates the sniffing, jamming, and hijacking of a BLE
 * connection using the simulated HardwareManager.
 */
class BtlejackingModule(private val hardwareManager: HardwareManager) {

    private val _state = MutableStateFlow(BtlejackingState.IDLE)
    val state = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var attackJob: Job? = null

    fun startSniffing(target: TargetDevice) {
        attackJob = scope.launch {
            _state.value = BtlejackingState.SNIFFING
            hardwareManager.sendCommand("sniff -t ${target.macAddress}")
            delay(5000) // Simulate sniffing duration
            if (_state.value == BtlejackingState.SNIFFING) { // Check if cancelled
                startJamming()
            }
        }
    }

    private suspend fun startJamming() {
        _state.value = BtlejackingState.JAMMING
        hardwareManager.sendCommand("jam")
        delay(3000) // Simulate jamming duration
        if (_state.value == BtlejackingState.JAMMING) { // Check if cancelled
            startHijacking()
        }
    }

    private suspend fun startHijacking() {
        _state.value = BtlejackingState.HIJACKING
        hardwareManager.sendCommand("hijack")
        delay(2000) // Simulate hijacking duration
        if (_state.value == BtlejackingState.HIJACKING) { // Check if cancelled
            _state.value = BtlejackingState.CONNECTED
            hardwareManager.sendCommand("connected")
        }
    }

    fun stop() {
        attackJob?.cancel()
        scope.launch {
            _state.value = BtlejackingState.IDLE
            hardwareManager.sendCommand("stop")
        }
    }
}
