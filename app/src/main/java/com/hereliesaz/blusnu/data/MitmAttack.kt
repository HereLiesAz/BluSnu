package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MitmAttack(
    private val spoofingState: MutableStateFlow<com.hereliesaz.blusnu.ui.spoofing.SpoofingState>,
    private val hardwareManager: HardwareManager,
    private val coroutineScope: CoroutineScope
) {

    suspend fun start() {
        coroutineScope.launch {
            hardwareManager.deviceLogs.collect { log ->
                spoofingState.update {
                    it.copy(
                        mitmLogs = it.mitmLogs + ("MITM" to (it.mitmLogs["MITM"] ?: emptyList()) + log)
                    )
                }
            }
        }
        hardwareManager.connect()
        delay(2000)
        hardwareManager.sendCommand("scan ble")
        delay(5000)
        hardwareManager.sendCommand("jam_and_listen")
        delay(10000)
        hardwareManager.disconnect()
    }
}
