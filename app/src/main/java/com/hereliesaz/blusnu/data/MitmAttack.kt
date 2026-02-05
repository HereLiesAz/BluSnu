package com.hereliesaz.blusnu.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Helper class to coordinate a Man-in-the-Middle (MitM) attack sequence.
 *
 * This class orchestrates the `HardwareManager` to perform a sequence of
 * scanning, jamming, and listening, while updating the UI state.
 * It is likely used by the `BtlejuiceMitmViewModel`.
 *
 * @property spoofingState The MutableStateFlow from the ViewModel to update with logs.
 * @property hardwareManager The interface to the external hardware.
 * @property coroutineScope The scope in which to run the attack logic.
 */
class MitmAttack(
    private val spoofingState: MutableStateFlow<com.hereliesaz.blusnu.ui.spoofing.SpoofingState>,
    private val hardwareManager: HardwareManager,
    private val coroutineScope: CoroutineScope
) {

    /**
     * Starts the automated MitM sequence.
     */
    suspend fun start() {
        // Launch a collector to pipe logs from HardwareManager to the UI state.
        coroutineScope.launch {
            hardwareManager.deviceLogs.collect { log ->
                spoofingState.update {
                    // Append new log to the "MITM" key in the map.
                    it.copy(
                        mitmLogs = it.mitmLogs + ("MITM" to (it.mitmLogs["MITM"] ?: emptyList()) + log)
                    )
                }
            }
        }

        // Step 1: Connect to the hardware dongle.
        hardwareManager.connect()
        delay(2000)

        // Step 2: Scan for BLE devices using the dongle.
        hardwareManager.sendCommand("scan ble")
        delay(5000)

        // Step 3: Initiate Jamming and Sniffing (Listen).
        // This command likely tells the firmware to follow the target and jam the master.
        hardwareManager.sendCommand("jam_and_listen")
        delay(10000)

        // Step 4: Disconnect/Cleanup.
        hardwareManager.disconnect()
    }
}
