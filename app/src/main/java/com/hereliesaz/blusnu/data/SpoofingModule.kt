package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor

/**
 * Module for Bluetooth identity spoofing (MAC address and adapter name changes).
 *
 * Requires root access. MAC spoofing is attempted via multiple methods in order:
 * 1. The `bdaddr` utility (works on most chipsets)
 * 2. CSR vendor-specific HCI command (0x3F 0x0001)
 * 3. Broadcom vendor-specific HCI command (0x3F 0x0001 with forward byte order)
 * 4. Generic `bdaddr` with reset flag
 *
 * Only one method needs to succeed. The module reports which method was used
 * or returns an error if all methods fail (indicating an unsupported chipset).
 */
class SpoofingModule {

    companion object {
        private const val TAG = "SpoofingModule"
    }

    /**
     * Reads the current BD_ADDR from hciconfig output.
     *
     * @return The current MAC address in uppercase colon-separated format, or null on failure.
     */
    suspend fun readCurrentMac(): String? {
        val output = RootExecutor.execute("hciconfig hci0")
        if (isRootError(output)) {
            Log.w(TAG, "Failed to read current MAC: $output")
            return null
        }
        val regex = Regex("BD Address:\\s*([0-9A-Fa-f:]{17})")
        val match = regex.find(output)
        return match?.groupValues?.get(1)?.uppercase()
    }

    /**
     * Attempts to change the Bluetooth adapter's MAC address (BD_ADDR).
     *
     * Tries multiple chipset-specific methods in sequence. Returns a [SpoofResult]
     * indicating success (with the method used) or failure (with the reason).
     *
     * @param macAddress The target MAC address in colon-separated format (XX:XX:XX:XX:XX:XX).
     * @return A [SpoofResult] describing the outcome.
     */
    suspend fun spoofMacAddress(macAddress: String): SpoofResult {
        if (!MacValidator.isValid(macAddress)) {
            return SpoofResult.Failure("Invalid MAC address format: $macAddress (expected XX:XX:XX:XX:XX:XX)")
        }

        if (!RootExecutor.isRootAvailable()) {
            return SpoofResult.Failure("Root is not available on this device.")
        }

        Log.i(TAG, "Attempting to spoof BD_ADDR to $macAddress")

        // Step 1: Bring the adapter down
        val downResult = RootExecutor.execute("hciconfig hci0 down")
        if (isRootError(downResult)) {
            Log.e(TAG, "Failed to bring hci0 down: $downResult")
            RootExecutor.execute("hciconfig hci0 up")
            return SpoofResult.Failure("Failed to bring adapter down: $downResult")
        }

        // Step 2: Try multiple methods to write the new BD_ADDR
        val methodResult = tryBdaddr(macAddress)
            ?: tryCsrHciCommand(macAddress)
            ?: tryBroadcomHciCommand(macAddress)
            ?: tryGenericBdaddrTool(macAddress)

        if (methodResult == null) {
            Log.e(TAG, "All MAC spoofing methods failed. Chipset may be unsupported.")
            RootExecutor.execute("hciconfig hci0 up")
            return SpoofResult.Failure(
                "Unsupported chipset: all MAC spoofing methods failed. " +
                    "This device's Bluetooth controller may not support BD_ADDR changes."
            )
        }

        // Step 3: Reset the adapter
        val resetResult = RootExecutor.execute("hciconfig hci0 reset")
        if (isRootError(resetResult)) {
            Log.w(TAG, "hciconfig reset returned error (may still work): $resetResult")
        }

        // Step 4: Bring the adapter back up
        val upResult = RootExecutor.execute("hciconfig hci0 up")
        if (isRootError(upResult)) {
            Log.e(TAG, "Failed to bring hci0 back up: $upResult")
            return SpoofResult.Failure("Failed to bring adapter back up: $upResult")
        }

        // Step 5: Verify the address change
        val verifyResult = RootExecutor.execute("hciconfig hci0")
        val normalizedTarget = macAddress.uppercase()
        val spoofSuccessful = verifyResult.uppercase().contains(normalizedTarget)

        return if (spoofSuccessful) {
            Log.i(TAG, "MAC address successfully spoofed to $macAddress (method: $methodResult)")
            SpoofResult.Success(methodResult)
        } else {
            Log.w(TAG, "MAC spoof verification failed. hciconfig output: $verifyResult")
            SpoofResult.Failure("Verification failed: adapter did not report the new address after applying via $methodResult.")
        }
    }

    /**
     * Attempts to change the adapter's Bluetooth name via hciconfig.
     *
     * @param name The new adapter name.
     * @return A [SpoofResult] describing the outcome.
     */
    suspend fun spoofName(name: String): SpoofResult {
        if (name.isBlank()) {
            return SpoofResult.Failure("Name cannot be blank.")
        }
        if (!RootExecutor.isRootAvailable()) {
            return SpoofResult.Failure("Root is not available on this device.")
        }

        // Sanitize name for shell: escape single quotes
        val sanitized = name.replace("'", "'\\''")
        val result = RootExecutor.execute("hciconfig hci0 name '$sanitized'")
        return if (isRootError(result)) {
            SpoofResult.Failure("Failed to change adapter name: $result")
        } else {
            SpoofResult.Success("hciconfig name")
        }
    }

    /**
     * Restores a previously saved MAC address.
     *
     * @param originalMac The original MAC address to restore.
     * @return A [SpoofResult] describing the outcome.
     */
    suspend fun restoreMacAddress(originalMac: String): SpoofResult {
        return spoofMacAddress(originalMac)
    }

    // -- Private chipset-specific methods --

    private suspend fun tryBdaddr(macAddress: String): String? {
        val result = RootExecutor.execute("bdaddr -i hci0 $macAddress")
        return if (!isRootError(result) && !result.contains("not found")) {
            Log.d(TAG, "bdaddr succeeded: $result")
            "bdaddr"
        } else {
            Log.d(TAG, "bdaddr failed or not found: $result")
            null
        }
    }

    /**
     * CSR (Cambridge Silicon Radio) chipset vendor-specific HCI command.
     * Uses OGF 0x3F (vendor), OCF 0x0001 with reversed MAC bytes.
     */
    private suspend fun tryCsrHciCommand(macAddress: String): String? {
        val macBytes = macAddress.split(":").reversed().joinToString(" ") { "0x$it" }
        val result = RootExecutor.execute("hcitool cmd 0x3F 0x0001 $macBytes")
        return if (!isRootError(result)) {
            Log.d(TAG, "CSR vendor HCI command succeeded: $result")
            "CSR vendor HCI (0x3F 0x0001)"
        } else {
            Log.d(TAG, "CSR vendor HCI command failed: $result")
            null
        }
    }

    /**
     * Broadcom chipset variant. Uses OGF 0x3F, OCF 0x0001 with forward MAC byte order.
     */
    private suspend fun tryBroadcomHciCommand(macAddress: String): String? {
        val macBytes = macAddress.split(":").joinToString(" ") { "0x$it" }
        val result = RootExecutor.execute("hcitool cmd 0x3F 0x0001 $macBytes")
        return if (!isRootError(result)) {
            Log.d(TAG, "Broadcom vendor HCI command succeeded: $result")
            "Broadcom vendor HCI (0x3F 0x0001)"
        } else {
            Log.d(TAG, "Broadcom vendor HCI command failed: $result")
            null
        }
    }

    /**
     * Generic fallback using the bdaddr tool with the --reset flag.
     */
    private suspend fun tryGenericBdaddrTool(macAddress: String): String? {
        val result = RootExecutor.execute("bdaddr -i hci0 -r $macAddress")
        return if (!isRootError(result) && !result.contains("not found")) {
            Log.d(TAG, "bdaddr with reset succeeded: $result")
            "bdaddr (with reset)"
        } else {
            Log.d(TAG, "bdaddr with reset failed: $result")
            null
        }
    }

    /**
     * Checks whether a RootExecutor result indicates an error.
     * Detects both the "Error (exit code N):" prefix from non-zero exit codes
     * and lowercase "error" from command output.
     */
    private fun isRootError(result: String): Boolean {
        return result.startsWith("Error") || result.startsWith("error")
    }
}

/**
 * Sealed result type for spoofing operations.
 */
sealed class SpoofResult {
    /** The operation succeeded. [method] describes which technique was used. */
    data class Success(val method: String) : SpoofResult()
    /** The operation failed. [reason] describes what went wrong. */
    data class Failure(val reason: String) : SpoofResult()
}
