package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Categories of BLUR vulnerability (CVE-2020-15802).
 *
 * Each variant describes a different weakness in Cross-Transport Key Derivation
 * (CTKD) between BR/EDR and BLE transports as documented by the Bluetooth SIG
 * advisory and Celosia & Cunche, "BLUR: a new attack on Bluetooth devices"
 * (ACM WiSec 2020).
 */
enum class BlurVulnerability(val description: String) {
    CTKD_UNRESTRICTED("CTKD active without CT2 restrictions"),
    KEY_OVERWRITE("BR/EDR key can be overwritten via LE pairing"),
    DUAL_PAIRING_MISMATCH("Different security levels between transports")
}

/**
 * Implementation of the BLUR (Bluetooth Low-energy Unreachability) attack.
 *
 * This module targets Cross-Transport Key Derivation (CTKD) between BR/EDR and
 * BLE on dual-mode Bluetooth devices. BLUR exploits the fact that CTKD allows
 * keys negotiated on one transport to overwrite keys on the other transport
 * without mutual authentication, enabling an attacker to downgrade link key
 * strength or overwrite authenticated keys with unauthenticated ones.
 *
 * The module first checks for a dedicated native binary (`blur_tester`). If
 * that binary is not present, it falls back to hcitool to probe CTKD support
 * by establishing connections on both BR/EDR and BLE transports to the same
 * device and inspecting link key derivation behavior through the kernel debug
 * filesystem.
 *
 * Root is required for all approaches (HCI manipulation, debugfs access).
 * All privileged operations are executed through [RootExecutor].
 */
class BlurModule {

    companion object {
        private const val TAG = "BlurModule"
        private const val BLUR_BINARY_PATH = "/data/local/tmp/blur_tester"
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
     * Executes the BLUR attack workflow against the target device.
     *
     * The flow:
     * 1. Validate the MAC address
     * 2. Verify root access (required, no simulation fallback)
     * 3. Check if the native blur_tester binary exists
     * 4. If it exists, run it with the target MAC
     * 5. Otherwise, probe CTKD support via hcitool and debugfs
     *
     * @param targetDevice The dual-mode Bluetooth device to test.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting BLUR attack (CVE-2020-15802) on ${targetDevice.name ?: mac}")
        emit("Target must be a dual-mode (BR/EDR + BLE) device for CTKD testing.")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BLUR requires root for HCI manipulation and debugfs access. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $BLUR_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native blur_tester binary
            emit("Native blur_tester binary found at $BLUR_BINARY_PATH")
            emit("Executing: $BLUR_BINARY_PATH -t $mac")

            val output = RootExecutor.execute("$BLUR_BINARY_PATH -t $mac")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("blur_tester execution failed.")
            } else {
                emit("blur_tester execution completed.")
            }
        } else {
            // Fallback: use hcitool to probe CTKD behavior
            emit("Native binary not found at $BLUR_BINARY_PATH")
            emit("Falling back to hcitool-based CTKD probing...")

            executeCtkdProbe(mac)
        }
    }

    /**
     * Probes Cross-Transport Key Derivation (CTKD) behavior using hcitool and
     * the kernel debug filesystem. The probe establishes connections on both
     * BR/EDR and BLE transports to the same device and inspects whether link
     * keys are derived across transports.
     *
     * Steps:
     *   a. Read remote device features via HCI to check for CTKD bit
     *   b. Create ACL (BR/EDR) connection
     *   c. Enable encryption on the BR/EDR link
     *   d. Attempt LE connection to the same device (dual-mode behavior)
     *   e. Read link keys before and after LE pairing from debugfs
     *   f. Check if BR/EDR key was derived from LE key (CTKD active)
     *   g. If CTKD active without restrictions: device is vulnerable
     *   h. Evaluate CT2 (Cross-Transport Key Derivation restrictions)
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeCtkdProbe(
        mac: String
    ) {
        // Step a: Read remote device features to check for CTKD support
        emit("--- Step 1: Reading remote device features ---")
        emit("Querying remote extended features (HCI cmd 0x04 0x001B)...")
        val featuresResult = RootExecutor.execute("hcitool cmd 0x04 0x001B")
        if (featuresResult.startsWith("Error") || featuresResult.isBlank()) {
            emit("Could not read remote features: ${featuresResult.ifBlank { "(empty)" }}")
            emit("Continuing with connection-based probing...")
        } else {
            emit("Remote features response: $featuresResult")
            // Check for CTKD-related feature bits
            val supportsCtkd = featuresResult.contains("Cross", ignoreCase = true) ||
                    featuresResult.contains("CTKD", ignoreCase = true)
            if (supportsCtkd) {
                emit("CTKD feature bit detected in remote features.")
            } else {
                emit("CTKD feature bit not explicitly visible. Probing via connection behavior...")
            }
        }

        // Step b: Create ACL (BR/EDR) connection
        emit("")
        emit("--- Step 2: Creating BR/EDR ACL connection ---")
        emit("Executing: hcitool cc $mac")
        val ccResult = RootExecutor.execute("hcitool cc $mac")
        if (ccResult.startsWith("Error")) {
            emit("Failed to create ACL connection: $ccResult")
            emit("The target may be out of range, unpaired, or not accepting connections.")
            return
        }
        emit("ACL connection result: ${ccResult.ifBlank { "OK (no output = success)" }}")

        // Step c: Enable encryption on the BR/EDR link
        emit("")
        emit("--- Step 3: Enabling BR/EDR encryption ---")
        emit("Executing: hcitool enc $mac")
        val encResult = RootExecutor.execute("hcitool enc $mac")
        if (encResult.startsWith("Error")) {
            emit("Encryption setup failed: $encResult")
            emit("Cannot proceed with CTKD testing without an encrypted BR/EDR link.")
            return
        }
        emit("Encryption result: ${encResult.ifBlank { "OK" }}")

        // Step e (before LE pairing): Read link keys from debugfs
        emit("")
        emit("--- Step 4: Reading BR/EDR link keys before LE pairing ---")
        val keysBefore = readLinkKeys()
        if (keysBefore == null) {
            emit("Link key debug info unavailable. Requires debugfs access.")
            emit("Ensure debugfs is mounted: mount -t debugfs none /sys/kernel/debug")
        } else {
            val keyCountBefore = keysBefore.lines().count { it.isNotBlank() }
            emit("Found $keyCountBefore link key entries before LE pairing.")
        }

        // Step d: Attempt LE connection to the same device
        emit("")
        emit("--- Step 5: Attempting LE connection (dual-mode test) ---")
        emit("Executing: hcitool lecc $mac")
        val leccResult = RootExecutor.execute("hcitool lecc $mac")
        if (leccResult.startsWith("Error")) {
            emit("LE connection failed: $leccResult")
            emit("The target may not support BLE or may not be advertising.")
            emit("CTKD requires both BR/EDR and LE connections to the same device.")
            evaluatePartialResults(mac, keysBefore)
            return
        }
        emit("LE connection result: $leccResult")

        // Extract LE connection handle for later cleanup
        val leHandleMatch = Regex("handle\\s+(\\d+|0x[0-9a-fA-F]+)", RegexOption.IGNORE_CASE)
            .find(leccResult)
        val leHandle = leHandleMatch?.groupValues?.get(1)
        if (leHandle != null) {
            emit("LE connection handle: $leHandle")
        }

        // Step e (after LE pairing): Read link keys again from debugfs
        emit("")
        emit("--- Step 6: Reading link keys after LE connection ---")
        val keysAfter = readLinkKeys()
        if (keysAfter == null) {
            emit("Link key debug info unavailable after LE connection.")
        } else {
            val keyCountAfter = keysAfter.lines().count { it.isNotBlank() }
            emit("Found $keyCountAfter link key entries after LE connection.")
        }

        // Step f: Compare link keys to detect CTKD activity
        emit("")
        emit("--- Step 7: Analyzing CTKD behavior ---")
        val ctkdActive = analyzeCtkdBehavior(keysBefore, keysAfter, mac)

        // Step g/h: Check for CT2 restrictions
        emit("")
        emit("--- Step 8: Evaluating CT2 restrictions ---")
        evaluateCt2Restrictions(mac, ctkdActive)

        // Cleanup: disconnect LE connection if established
        if (leHandle != null) {
            emit("")
            emit("Disconnecting LE connection (handle $leHandle)...")
            RootExecutor.execute("hcitool ledc $leHandle")
            emit("LE connection closed.")
        }
    }

    /**
     * Reads link keys from the kernel debug filesystem.
     *
     * @return The raw content of the link_keys debugfs file, or null if unavailable.
     */
    private suspend fun readLinkKeys(): String? {
        val result = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/link_keys 2>/dev/null"
        )
        return if (result.startsWith("Error") || result.isBlank()) null else result
    }

    /**
     * Compares link keys before and after LE pairing to determine if CTKD
     * derived or overwrote a BR/EDR key from the LE transport.
     *
     * @return true if CTKD activity was detected (keys changed or new key derived).
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.analyzeCtkdBehavior(
        keysBefore: String?,
        keysAfter: String?,
        mac: String
    ): Boolean {
        if (keysBefore == null && keysAfter == null) {
            emit("Cannot compare link keys -- debugfs not accessible.")
            emit("Manual analysis required to determine CTKD behavior.")
            return false
        }

        val beforeLines = keysBefore?.lines()?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val afterLines = keysAfter?.lines()?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

        val newKeys = afterLines - beforeLines
        val removedKeys = beforeLines - afterLines

        if (newKeys.isEmpty() && removedKeys.isEmpty()) {
            emit("Link keys unchanged after LE connection.")
            emit("CTKD does not appear to be active for this device.")
            return false
        }

        if (newKeys.isNotEmpty()) {
            emit("New or modified link key entries detected after LE connection:")
            for (key in newKeys) {
                emit("  + $key")
            }
        }

        if (removedKeys.isNotEmpty()) {
            emit("Removed or replaced link key entries:")
            for (key in removedKeys) {
                emit("  - $key")
            }
        }

        // Check if the target MAC's key specifically changed
        val macUpper = mac.uppercase()
        val targetKeyBefore = beforeLines.firstOrNull { it.uppercase().contains(macUpper) }
        val targetKeyAfter = afterLines.firstOrNull { it.uppercase().contains(macUpper) }

        if (targetKeyBefore != null && targetKeyAfter != null && targetKeyBefore != targetKeyAfter) {
            emit("CTKD DETECTED: BR/EDR link key for $mac was modified after LE connection.")
            emit("The BR/EDR key appears to have been derived from the LE key.")
            Log.w(TAG, "CTKD key overwrite detected on $mac")
            return true
        } else if (targetKeyBefore == null && targetKeyAfter != null) {
            emit("CTKD DETECTED: New BR/EDR link key appeared for $mac after LE connection.")
            emit("A cross-transport derived key was created.")
            Log.w(TAG, "CTKD new key derivation detected on $mac")
            return true
        }

        emit("Link key changes detected but not conclusively tied to CTKD for target $mac.")
        return false
    }

    /**
     * Evaluates whether the device enforces CT2 (Cross-Transport Key Derivation
     * restrictions) as recommended by the Bluetooth SIG advisory for CVE-2020-15802.
     *
     * CT2 requires that cross-transport derived keys carry authentication
     * information from the originating transport. Without CT2, an unauthenticated
     * LE key can overwrite an authenticated BR/EDR key.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluateCt2Restrictions(
        mac: String,
        ctkdActive: Boolean
    ) {
        // Read remote extended features to check for CT2 support
        val extFeaturesResult = RootExecutor.execute("hcitool cmd 0x04 0x001C")
        val ct2Detected = extFeaturesResult.contains("CT2", ignoreCase = true) ||
                extFeaturesResult.contains("cross.transport", ignoreCase = true)

        emit("Remote extended features page 2: ${extFeaturesResult.ifBlank { "(empty)" }}")

        // Check the encryption key size on the BR/EDR link for security level comparison
        val brEdrKeySize = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/encryption_key_size 2>/dev/null"
        )
        val brEdrKeySizeParsed = Regex("(\\d+)").find(brEdrKeySize)?.groupValues?.get(1)?.toIntOrNull()

        if (brEdrKeySizeParsed != null) {
            emit("BR/EDR encryption key size: $brEdrKeySizeParsed bytes")
        }

        emit("")
        emit("=== BLUR Vulnerability Assessment ===")

        val vulnerabilities = mutableListOf<BlurVulnerability>()

        if (ctkdActive && !ct2Detected) {
            vulnerabilities.add(BlurVulnerability.CTKD_UNRESTRICTED)
            emit("VULNERABILITY: ${BlurVulnerability.CTKD_UNRESTRICTED.description}")
            emit("  CTKD is active and CT2 restrictions are not enforced.")
            emit("  An attacker on the LE transport can derive keys that overwrite")
            emit("  BR/EDR keys without the authentication level being preserved.")
        }

        if (ctkdActive) {
            vulnerabilities.add(BlurVulnerability.KEY_OVERWRITE)
            emit("VULNERABILITY: ${BlurVulnerability.KEY_OVERWRITE.description}")
            emit("  The BR/EDR link key was modified via LE pairing, confirming")
            emit("  that cross-transport key overwrite is possible.")
        }

        // Check for security level mismatch between transports
        if (ctkdActive && brEdrKeySizeParsed != null && brEdrKeySizeParsed < 16) {
            vulnerabilities.add(BlurVulnerability.DUAL_PAIRING_MISMATCH)
            emit("VULNERABILITY: ${BlurVulnerability.DUAL_PAIRING_MISMATCH.description}")
            emit("  BR/EDR key size ($brEdrKeySizeParsed bytes) is below maximum,")
            emit("  indicating potentially different security levels between transports.")
        }

        if (vulnerabilities.isEmpty()) {
            if (ctkdActive && ct2Detected) {
                emit("CTKD is active but CT2 restrictions appear to be enforced.")
                emit("The device follows the Bluetooth SIG mitigation for CVE-2020-15802.")
                emit("Cross-transport derived keys should preserve authentication levels.")
            } else if (!ctkdActive) {
                emit("CTKD does not appear to be active on this device.")
                emit("The device is not susceptible to BLUR key overwrite attacks.")
            } else {
                emit("Assessment inconclusive. Manual analysis recommended.")
            }
        } else {
            emit("")
            emit("Total vulnerabilities found: ${vulnerabilities.size}")
            emit("Recommendation: Update device firmware to enforce CT2 restrictions")
            emit("as specified in the Bluetooth SIG advisory for CVE-2020-15802.")
            Log.w(TAG, "BLUR vulnerabilities found on $mac: ${vulnerabilities.map { it.name }}")
        }
    }

    /**
     * Evaluates partial results when the LE connection could not be established
     * but a BR/EDR link was active. Reports what can be determined from the
     * BR/EDR side alone.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluatePartialResults(
        mac: String,
        keysBefore: String?
    ) {
        emit("")
        emit("=== Partial BLUR Assessment (BR/EDR only) ===")
        emit("LE connection failed -- full CTKD probing not possible.")

        if (keysBefore != null) {
            val macUpper = mac.uppercase()
            val hasKey = keysBefore.lines().any { it.uppercase().contains(macUpper) }
            if (hasKey) {
                emit("BR/EDR link key exists for $mac.")
                emit("If LE pairing can be established by other means, CTKD testing")
                emit("should be repeated to check for key overwrite.")
            }
        }

        emit("Ensure the target device is in BLE advertising mode and within range.")
        emit("BLUR requires successful connections on both transports.")
    }
}
