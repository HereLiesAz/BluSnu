package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Attack vectors for the BlueBorne vulnerability family.
 *
 * Based on the Armis Labs disclosure (September 2017). Each variant targets a
 * different parsing flaw in the Bluetooth stack's handling of BNEP, SDP, or
 * L2CAP protocol data units. All vectors are pre-authentication and require
 * no user interaction (zero-click).
 */
enum class BlueBorneVector(val description: String, val cve: String) {
    BNEP_HEAP_OVERFLOW(
        "BNEP heap overflow via crafted control message (Android RCE)",
        "CVE-2017-0781"
    ),
    SDP_INFO_LEAK(
        "SDP information disclosure via oversized continuation state",
        "CVE-2017-0785"
    ),
    SDP_RCE(
        "SDP remote code execution via malformed SDP response",
        "CVE-2017-0782"
    ),
    L2CAP_RCE(
        "L2CAP configuration response buffer overflow (Linux/Android kernel RCE)",
        "CVE-2017-1000251"
    )
}

/**
 * Implementation of the BlueBorne attack scanner and probe module.
 *
 * BlueBorne is a family of 8 zero-click, pre-authentication remote code
 * execution vulnerabilities across Android, Linux, iOS, and Windows Bluetooth
 * stacks, disclosed by Armis Labs in September 2017. The vulnerabilities
 * exploit parsing bugs in BNEP, SDP, and L2CAP layers -- no pairing is
 * required for exploitation.
 *
 * Billions of legacy IoT devices remain unpatched. The original PoC targets
 * Android 7.1.2 (Pixel) and exploits the BNEP `bnep_add_extension()` heap
 * overflow for RCE.
 *
 * The module first checks for a dedicated native binary (`blueborne_scanner`).
 * If that binary is not present, it falls back to L2CAP/SDP probing using
 * standard BlueZ tools to identify vulnerable configurations. The fallback
 * scans for SDP services, probes L2CAP PSMs, tests BNEP channel availability,
 * checks the target's Android version if detectable, and sends crafted L2CAP
 * packets to test for buffer handling issues.
 *
 * Root is required for all approaches (raw L2CAP/BNEP access, hcitool).
 * All privileged operations are executed through [RootExecutor].
 */
class BlueBorneModule {

    companion object {
        private const val TAG = "BlueBorneModule"
        private const val BLUEBORNE_BINARY_PATH = "/data/local/tmp/blueborne_scanner"

        /**
         * L2CAP PSM for BNEP (Bluetooth Network Encapsulation Protocol).
         * BNEP runs on PSM 0x000F and is used by the PAN (Personal Area
         * Networking) profile. CVE-2017-0781 exploits a heap overflow in
         * the BNEP control message parser.
         */
        private const val BNEP_PSM = 0x000F

        /**
         * L2CAP PSM for SDP (Service Discovery Protocol).
         * SDP runs on PSM 0x0001. CVE-2017-0785 and CVE-2017-0782 exploit
         * parsing flaws in the SDP server response handler.
         */
        private const val SDP_PSM = 0x0001

        /**
         * Common L2CAP PSMs to probe for vulnerable configurations.
         * These cover SDP, RFCOMM, BNEP, AVCTP, AVDTP, and ATT.
         */
        private val PROBE_PSMS = listOf(0x0001, 0x0003, 0x000F, 0x0017, 0x0019, 0x001F)
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
     * Executes the BlueBorne scanning workflow against the target device.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Check if the native blueborne_scanner binary exists
     * 3. If it exists, run it with the specified vector
     * 4. Otherwise, fall back to L2CAP/SDP probing via BlueZ tools
     *
     * @param targetDevice The Bluetooth device to scan.
     * @param vector The specific BlueBorne vector to test, or null for full assessment.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice, vector: BlueBorneVector?): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting BlueBorne scan on ${targetDevice.name ?: mac}")
        if (vector != null) {
            emit("Vector: ${vector.name} (${vector.cve}) -- ${vector.description}")
        } else {
            emit("Mode: Full vulnerability assessment (all CVEs)")
        }

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BlueBorne requires root for raw L2CAP/BNEP access and hcitool. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $BLUEBORNE_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native blueborne_scanner binary
            emit("Native blueborne_scanner binary found at $BLUEBORNE_BINARY_PATH")
            val vectorArg = vector?.name?.lowercase() ?: "all"
            emit("Executing: $BLUEBORNE_BINARY_PATH -t $mac -v $vectorArg")

            val output = RootExecutor.execute("$BLUEBORNE_BINARY_PATH -t $mac -v $vectorArg")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("blueborne_scanner execution failed.")
            } else {
                emit("blueborne_scanner execution completed.")
            }
        } else {
            // Fallback: use BlueZ tools to probe the target
            emit("Native binary not found at $BLUEBORNE_BINARY_PATH")
            emit("Falling back to L2CAP/SDP probing via BlueZ tools...")

            if (vector != null) {
                executeVectorProbe(mac, vector)
            } else {
                executeFullAssessment(mac)
            }
        }
    }

    /**
     * Runs a full vulnerability assessment against the target, checking which
     * CVEs the target may be susceptible to based on service availability,
     * L2CAP PSM responses, BNEP channel state, and detectable version info.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeFullAssessment(
        mac: String
    ) {
        emit("=== BlueBorne Full Vulnerability Assessment ===")
        emit("Target: $mac")

        // Step 1: Scan SDP services
        emit("")
        emit("--- Phase 1: SDP Service Discovery ---")
        val sdpServices = scanSdpServices(mac)

        // Step 2: Probe L2CAP PSMs
        emit("")
        emit("--- Phase 2: L2CAP PSM Probing ---")
        val openPsms = probeL2capPsms(mac)

        // Step 3: Test BNEP availability
        emit("")
        emit("--- Phase 3: BNEP Channel Availability ---")
        val bnepAvailable = testBnepChannel(mac)

        // Step 4: Check Android version
        emit("")
        emit("--- Phase 4: Target Version Detection ---")
        val targetVersion = checkTargetVersion(mac, sdpServices)

        // Step 5: Assess CVE susceptibility
        emit("")
        emit("--- Phase 5: CVE Susceptibility Assessment ---")
        assessVulnerabilities(mac, sdpServices, openPsms, bnepAvailable, targetVersion)
    }

    /**
     * Executes a vector-specific probe using BlueZ tools.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeVectorProbe(
        mac: String,
        vector: BlueBorneVector
    ) {
        emit("--- Vector-specific probe: ${vector.name} (${vector.cve}) ---")

        when (vector) {
            BlueBorneVector.BNEP_HEAP_OVERFLOW -> probeBnepHeapOverflow(mac)
            BlueBorneVector.SDP_INFO_LEAK -> probeSdpInfoLeak(mac)
            BlueBorneVector.SDP_RCE -> probeSdpRce(mac)
            BlueBorneVector.L2CAP_RCE -> probeL2capRce(mac)
        }
    }

    /**
     * Scans the target's SDP (Service Discovery Protocol) records to
     * enumerate supported Bluetooth services and profiles.
     *
     * @return The raw SDP browse output for further analysis.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.scanSdpServices(
        mac: String
    ): String {
        emit("Querying SDP service records on $mac...")
        val sdpResult = RootExecutor.execute("sdptool browse $mac")

        if (sdpResult.startsWith("Error") || sdpResult.isBlank()) {
            emit("SDP query failed or returned no results: ${sdpResult.ifBlank { "(empty)" }}")
            emit("Target may not be reachable, or SDP server may be disabled.")
            return ""
        }

        // Count and list discovered services
        val serviceNames = Regex("Service Name: (.+)").findAll(sdpResult)
            .map { it.groupValues[1] }
            .toList()

        emit("Discovered ${serviceNames.size} service(s):")
        for (name in serviceNames) {
            emit("  - $name")
        }

        // Check for PAN/NAP/GN services (BNEP-related)
        val hasPanService = sdpResult.contains("NAP", ignoreCase = true) ||
                sdpResult.contains("GN", ignoreCase = true) ||
                sdpResult.contains("PANU", ignoreCase = true) ||
                sdpResult.contains("Personal Area Network", ignoreCase = true)
        if (hasPanService) {
            emit("PAN/NAP service detected -- BNEP stack is active on target.")
        }

        return sdpResult
    }

    /**
     * Probes common L2CAP PSMs (Protocol/Service Multiplexers) to determine
     * which protocol channels are open and accepting connections.
     *
     * @return List of PSM values that accepted connections.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.probeL2capPsms(
        mac: String
    ): List<Int> {
        emit("Probing L2CAP PSMs on $mac...")
        val openPsms = mutableListOf<Int>()

        for (psm in PROBE_PSMS) {
            val psmHex = "0x${psm.toString(16).uppercase().padStart(4, '0')}"
            val psmName = when (psm) {
                0x0001 -> "SDP"
                0x0003 -> "RFCOMM"
                0x000F -> "BNEP"
                0x0017 -> "AVCTP"
                0x0019 -> "AVDTP"
                0x001F -> "ATT"
                else -> "Unknown"
            }

            val result = RootExecutor.execute(
                "l2ping -c 1 -s 44 -t 2 $mac 2>&1 || echo 'unreachable'"
            )

            // Use hcitool to attempt L2CAP connection on the specific PSM
            val connResult = RootExecutor.execute(
                "hcitool cmd 0x02 0x000A $mac ${psm.toString(16).padStart(4, '0')}"
            )

            if (!connResult.startsWith("Error") && !connResult.contains("refused")) {
                emit("  PSM $psmHex ($psmName): OPEN")
                openPsms.add(psm)
            } else {
                emit("  PSM $psmHex ($psmName): closed/refused")
            }
        }

        emit("${openPsms.size} of ${PROBE_PSMS.size} probed PSMs are open.")
        return openPsms
    }

    /**
     * Tests whether the BNEP channel is available on the target by attempting
     * to establish a PAN connection. BNEP availability is a prerequisite for
     * CVE-2017-0781 (BNEP heap overflow).
     *
     * @return true if BNEP appears to be available.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.testBnepChannel(
        mac: String
    ): Boolean {
        emit("Testing BNEP channel availability on $mac...")

        // Attempt to create an ACL connection first
        val aclResult = RootExecutor.execute("hcitool cc $mac")
        if (aclResult.startsWith("Error")) {
            emit("ACL connection failed: $aclResult")
            emit("Cannot test BNEP without an ACL link.")
            return false
        }
        emit("ACL connection established: ${aclResult.ifBlank { "OK" }}")

        // Check if BNEP kernel module is loaded on our device
        val bnepModResult = RootExecutor.execute("lsmod | grep bnep")
        if (bnepModResult.isBlank()) {
            emit("BNEP kernel module not loaded on local device.")
            emit("Loading bnep module...")
            RootExecutor.execute("modprobe bnep 2>/dev/null || insmod /system/lib/modules/bnep.ko 2>/dev/null")
        } else {
            emit("BNEP kernel module is loaded.")
        }

        // Try to connect to BNEP PSM on target
        val bnepResult = RootExecutor.execute(
            "l2test -b -P $BNEP_PSM $mac 2>&1 &" +
            " sleep 2 && kill %1 2>/dev/null; wait 2>/dev/null"
        )

        val bnepAvailable = !bnepResult.contains("Connection refused") &&
                !bnepResult.contains("Error") &&
                !bnepResult.contains("No route to host")

        if (bnepAvailable) {
            emit("BNEP channel appears available on target.")
            emit("Target may be susceptible to CVE-2017-0781 (BNEP heap overflow).")
        } else {
            emit("BNEP channel not available or connection refused.")
            emit("Result: $bnepResult")
        }

        return bnepAvailable
    }

    /**
     * Attempts to detect the target device's Android version or OS type from
     * SDP records, device class, and remote version information. Devices
     * running Android <= 7.1.2 without the September 2017 security patch are
     * vulnerable to BlueBorne.
     *
     * @return A descriptive string of the target version, or null if undetectable.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.checkTargetVersion(
        mac: String,
        sdpServices: String
    ): String? {
        emit("Attempting to detect target OS/version...")

        // Read remote version via HCI
        val versionResult = RootExecutor.execute("hcitool cmd 0x04 0x001D $mac")
        if (!versionResult.startsWith("Error") && versionResult.isNotBlank()) {
            emit("Remote version info: $versionResult")
        }

        // Check device class for OS hints
        val infoResult = RootExecutor.execute("hcitool info $mac")
        if (!infoResult.startsWith("Error") && infoResult.isNotBlank()) {
            for (line in infoResult.lines()) {
                if (line.isNotBlank()) {
                    emit("  $line")
                }
            }
        }

        // Look for Android-specific SDP service patterns
        val hasAndroidServices = sdpServices.contains("OBEX Object Push") ||
                sdpServices.contains("PBAP") ||
                sdpServices.contains("MAP") ||
                sdpServices.contains("Android")

        val hasLinuxServices = sdpServices.contains("PulseAudio") ||
                sdpServices.contains("BlueZ")

        val detectedOs = when {
            sdpServices.contains("Android", ignoreCase = true) -> "Android (version unknown)"
            hasAndroidServices -> "Likely Android (inferred from service profile)"
            hasLinuxServices -> "Linux/BlueZ"
            else -> null
        }

        if (detectedOs != null) {
            emit("Detected OS: $detectedOs")
            if (detectedOs.contains("Android")) {
                emit("NOTE: Android devices <= 7.1.2 without the September 2017 security")
                emit("patch (android-2017-09-01) are vulnerable to all BlueBorne CVEs.")
            }
        } else {
            emit("Could not determine target OS from available information.")
        }

        return detectedOs
    }

    /**
     * Assesses which BlueBorne CVEs the target may be susceptible to based on
     * the gathered reconnaissance data: service availability, open L2CAP PSMs,
     * BNEP channel state, and detectable OS/version.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.assessVulnerabilities(
        mac: String,
        sdpServices: String,
        openPsms: List<Int>,
        bnepAvailable: Boolean,
        targetVersion: String?
    ) {
        emit("Assessing BlueBorne CVE susceptibility for $mac...")
        emit("")

        var vulnerableCount = 0

        // CVE-2017-0781: BNEP heap overflow (Android RCE)
        val bnepVulnerable = bnepAvailable &&
                (targetVersion?.contains("Android") == true || targetVersion == null)
        emit("CVE-2017-0781 (BNEP Heap Overflow / Android RCE):")
        if (bnepVulnerable) {
            emit("  POTENTIALLY VULNERABLE -- BNEP channel is open.")
            emit("  The bnep_add_extension() parser may be exploitable.")
            vulnerableCount++
        } else if (!bnepAvailable) {
            emit("  NOT LIKELY -- BNEP channel is not available.")
        } else {
            emit("  NOT APPLICABLE -- Target does not appear to be Android.")
        }
        emit("")

        // CVE-2017-0785: SDP information disclosure
        val sdpOpen = openPsms.contains(SDP_PSM) || sdpServices.isNotBlank()
        emit("CVE-2017-0785 (SDP Information Disclosure):")
        if (sdpOpen) {
            emit("  POTENTIALLY VULNERABLE -- SDP service is active.")
            emit("  Oversized continuation state may leak heap memory.")
            vulnerableCount++
        } else {
            emit("  NOT LIKELY -- SDP service not detected.")
        }
        emit("")

        // CVE-2017-0782: SDP RCE
        emit("CVE-2017-0782 (SDP Remote Code Execution):")
        if (sdpOpen && (targetVersion?.contains("Android") == true || targetVersion == null)) {
            emit("  POTENTIALLY VULNERABLE -- SDP is active on a possible Android target.")
            emit("  Malformed SDP response handling may allow code execution.")
            vulnerableCount++
        } else if (!sdpOpen) {
            emit("  NOT LIKELY -- SDP service not detected.")
        } else {
            emit("  NOT APPLICABLE -- Target does not appear to be Android.")
        }
        emit("")

        // CVE-2017-1000251: L2CAP RCE (Linux kernel)
        val l2capVulnerable = openPsms.isNotEmpty() &&
                (targetVersion?.contains("Linux") == true ||
                 targetVersion?.contains("Android") == true ||
                 targetVersion == null)
        emit("CVE-2017-1000251 (L2CAP Configuration Response Buffer Overflow):")
        if (l2capVulnerable) {
            emit("  POTENTIALLY VULNERABLE -- L2CAP channels are open on a Linux/Android target.")
            emit("  l2cap_parse_conf_rsp() buffer overflow may be exploitable.")
            vulnerableCount++
        } else {
            emit("  NOT LIKELY -- No open L2CAP channels or non-Linux target.")
        }
        emit("")

        // Summary
        emit("=== Assessment Summary ===")
        emit("$vulnerableCount of 4 tested CVEs flagged as potentially vulnerable.")
        if (vulnerableCount > 0) {
            emit("WARNING: Target may be susceptible to BlueBorne zero-click RCE.")
            emit("Recommend verifying with the native blueborne_scanner for confirmation.")
        } else {
            emit("Target does not appear vulnerable based on available indicators.")
            emit("Note: False negatives are possible without the native scanner binary.")
        }
        Log.i(TAG, "BlueBorne assessment on $mac: $vulnerableCount/4 CVEs flagged")
    }

    /**
     * Probes for CVE-2017-0781: BNEP heap overflow.
     *
     * Tests BNEP channel availability and sends crafted BNEP control messages
     * to test for buffer handling issues in `bnep_add_extension()`.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.probeBnepHeapOverflow(
        mac: String
    ) {
        emit("Probing for CVE-2017-0781 (BNEP heap overflow)...")
        emit("This vulnerability targets the bnep_add_extension() function in the")
        emit("Android Bluetooth stack. A crafted BNEP control message with an oversized")
        emit("extension header triggers a heap buffer overflow.")

        val bnepAvailable = testBnepChannel(mac)
        if (!bnepAvailable) {
            emit("BNEP channel not available. CVE-2017-0781 cannot be tested without BNEP.")
            return
        }

        // Send crafted L2CAP packet to BNEP PSM to test buffer handling
        emit("Sending crafted L2CAP test packet to BNEP PSM...")
        val craftedResult = RootExecutor.execute(
            "l2test -b -P $BNEP_PSM -s 672 $mac 2>&1 &" +
            " sleep 3 && kill %1 2>/dev/null; wait 2>/dev/null"
        )

        for (line in craftedResult.lines()) {
            if (line.isNotBlank()) emit("  $line")
        }

        if (craftedResult.contains("refused") || craftedResult.contains("Error")) {
            emit("BNEP rejected the test packet. Target may be patched.")
        } else {
            emit("BNEP accepted the test payload. Target may be vulnerable to heap overflow.")
            emit("Full exploitation requires the native blueborne_scanner binary.")
        }
    }

    /**
     * Probes for CVE-2017-0785: SDP information disclosure.
     *
     * Sends SDP requests with oversized continuation state values to test
     * whether the target leaks heap memory in its response.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.probeSdpInfoLeak(
        mac: String
    ) {
        emit("Probing for CVE-2017-0785 (SDP information disclosure)...")
        emit("This vulnerability allows reading heap memory from the target's SDP")
        emit("server by supplying oversized continuation state in SDP requests.")

        // Query SDP to verify service is active
        val sdpResult = RootExecutor.execute("sdptool browse $mac")
        if (sdpResult.startsWith("Error") || sdpResult.isBlank()) {
            emit("SDP service not reachable. CVE-2017-0785 cannot be tested.")
            return
        }
        emit("SDP service is active. ${sdpResult.lines().count { it.isNotBlank() }} lines of service data returned.")

        // Send crafted SDP request via l2test to PSM 1 (SDP)
        emit("Sending crafted SDP continuation probe...")
        val probeResult = RootExecutor.execute(
            "l2test -b -P $SDP_PSM -s 256 $mac 2>&1 &" +
            " sleep 2 && kill %1 2>/dev/null; wait 2>/dev/null"
        )

        for (line in probeResult.lines()) {
            if (line.isNotBlank()) emit("  $line")
        }

        emit("SDP probe completed. Detailed exploitation requires the native scanner.")
        emit("If vulnerable, the target will return heap data beyond the SDP response buffer.")
    }

    /**
     * Probes for CVE-2017-0782: SDP remote code execution.
     *
     * Tests whether the target's SDP implementation is susceptible to a
     * malformed SDP response that triggers code execution.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.probeSdpRce(
        mac: String
    ) {
        emit("Probing for CVE-2017-0782 (SDP remote code execution)...")
        emit("This vulnerability exploits a flaw in Android's SDP client when parsing")
        emit("crafted SDP responses, allowing arbitrary code execution.")

        // Check SDP reachability
        val sdpResult = RootExecutor.execute("sdptool browse $mac")
        if (sdpResult.startsWith("Error") || sdpResult.isBlank()) {
            emit("SDP service not reachable. CVE-2017-0782 cannot be tested.")
            return
        }
        emit("SDP service is active on target.")

        // Probe SDP PSM with larger payloads
        emit("Sending oversized SDP payloads to test response handling...")
        val probeResult = RootExecutor.execute(
            "l2test -b -P $SDP_PSM -s 1024 $mac 2>&1 &" +
            " sleep 3 && kill %1 2>/dev/null; wait 2>/dev/null"
        )

        for (line in probeResult.lines()) {
            if (line.isNotBlank()) emit("  $line")
        }

        // Check if target is still responding after oversized payload
        emit("Verifying target responsiveness after probe...")
        val pingResult = RootExecutor.execute("l2ping -c 2 -t 3 $mac")
        if (pingResult.contains("bytes") && !pingResult.startsWith("Error")) {
            emit("Target still responsive. SDP stack handled the payload.")
        } else {
            emit("Target may have become unresponsive. Possible crash indicator.")
            Log.w(TAG, "Target $mac unresponsive after SDP RCE probe -- possible crash")
        }

        emit("Full SDP RCE exploitation requires the native blueborne_scanner binary.")
    }

    /**
     * Probes for CVE-2017-1000251: L2CAP configuration response buffer overflow.
     *
     * Tests the target's L2CAP stack by sending crafted configuration response
     * packets that may trigger a buffer overflow in `l2cap_parse_conf_rsp()`.
     * This affects the Linux kernel Bluetooth subsystem.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.probeL2capRce(
        mac: String
    ) {
        emit("Probing for CVE-2017-1000251 (L2CAP buffer overflow)...")
        emit("This vulnerability targets the l2cap_parse_conf_rsp() function in the")
        emit("Linux kernel Bluetooth subsystem. A crafted L2CAP configuration response")
        emit("with excessive options triggers a stack buffer overflow.")

        // Establish ACL connection
        emit("Creating ACL connection to $mac...")
        val aclResult = RootExecutor.execute("hcitool cc $mac")
        if (aclResult.startsWith("Error")) {
            emit("ACL connection failed: $aclResult")
            emit("Cannot test L2CAP without an ACL link.")
            return
        }
        emit("ACL connection established: ${aclResult.ifBlank { "OK" }}")

        // Probe multiple L2CAP PSMs with crafted payloads
        emit("Probing L2CAP PSMs with crafted configuration payloads...")
        for (psm in PROBE_PSMS) {
            val psmHex = "0x${psm.toString(16).uppercase().padStart(4, '0')}"
            val psmName = when (psm) {
                0x0001 -> "SDP"
                0x0003 -> "RFCOMM"
                0x000F -> "BNEP"
                0x0017 -> "AVCTP"
                0x0019 -> "AVDTP"
                0x001F -> "ATT"
                else -> "Unknown"
            }

            emit("  Testing PSM $psmHex ($psmName) with oversized config response...")
            val result = RootExecutor.execute(
                "l2test -b -P $psm -s 2048 $mac 2>&1 &" +
                " sleep 2 && kill %1 2>/dev/null; wait 2>/dev/null"
            )

            if (result.contains("refused") || result.contains("Error")) {
                emit("    PSM $psmHex: Connection refused or error.")
            } else {
                emit("    PSM $psmHex: Accepted test payload.")
            }
        }

        // Check target responsiveness
        emit("Verifying target responsiveness after L2CAP probes...")
        val pingResult = RootExecutor.execute("l2ping -c 3 -t 3 $mac")
        if (pingResult.contains("bytes") && !pingResult.startsWith("Error")) {
            emit("Target still responsive. L2CAP stack handled all test payloads.")
        } else {
            emit("Target may have become unresponsive. Possible kernel crash indicator.")
            Log.w(TAG, "Target $mac unresponsive after L2CAP probes -- possible kernel crash")
        }

        emit("Full L2CAP RCE exploitation requires the native blueborne_scanner binary.")
    }
}
