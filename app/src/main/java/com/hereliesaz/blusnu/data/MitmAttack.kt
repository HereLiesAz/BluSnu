package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * Coordinates a Man-in-the-Middle (MitM) attack sequence using external hardware.
 *
 * Orchestrates the [HardwareManager] through scanning, jamming, and listening phases.
 * UI state is updated via [onLog] callback rather than direct MutableStateFlow mutation.
 *
 * The [logCollectorJob] is tracked so it can be cancelled when the attack stops.
 */
class MitmAttack(
    private val hardwareManager: HardwareManager,
    private val coroutineScope: CoroutineScope,
    private val onLog: (String) -> Unit
) {

    companion object {
        private const val TAG = "MitmAttack"
    }

    private var logCollectorJob: Job? = null

    /**
     * Runs the automated MitM sequence. This is a suspend function that completes
     * when the attack sequence finishes or fails. Callers should wrap in a Job
     * for cancellation support.
     *
     * @return true if the sequence completed successfully, false on failure.
     */
    suspend fun start(): Boolean {
        // Launch a collector to pipe hardware logs to the UI via callback.
        logCollectorJob = coroutineScope.launch {
            hardwareManager.deviceLogs.collect { log ->
                onLog(log)
            }
        }

        return try {
            // Step 1: Connect to the hardware dongle.
            onLog("Connecting to hardware dongle...")
            hardwareManager.connect()
            delay(2000)

            // Verify connection
            val state = hardwareManager.hardwareState.value
            if (state == HardwareState.CONNECTION_FAILED || state == HardwareState.DISCONNECTED) {
                onLog("Failed to connect to hardware dongle.")
                return false
            }
            onLog("Hardware connected (state: $state)")

            // Step 2: Scan for BLE devices using the dongle.
            onLog("Scanning for BLE devices...")
            hardwareManager.sendCommand("scan ble")
            delay(5000)

            // Step 3: Initiate Jamming and Sniffing (Listen).
            onLog("Starting jam-and-listen phase...")
            hardwareManager.sendCommand("jam_and_listen")
            delay(10000)

            onLog("MITM sequence completed.")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MITM attack failed", e)
            onLog("MITM attack failed: ${e.message}")
            false
        } finally {
            // Clean up the log collector
            logCollectorJob?.cancel()
            logCollectorJob = null
            hardwareManager.disconnect()
        }
    }

    /**
     * Cancels the log collector job. Called externally when stopping the MITM attack.
     */
    fun cancelLogCollector() {
        logCollectorJob?.cancel()
        logCollectorJob = null
    }
}
