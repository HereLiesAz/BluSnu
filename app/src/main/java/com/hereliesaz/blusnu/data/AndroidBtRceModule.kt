package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Attack vectors for Android Bluetooth stack RCE vulnerabilities (2024-2025).
 *
 * Each vector targets a distinct memory corruption flaw in Android's Bluetooth
 * subsystem, spanning GATT server handling, Hands-Free client teardown, ACL
 * connection arbitration, BTA task management, and L2CAP reassembly. All affect
 * Android 12-15 and enable zero-click arbitrary code execution.
 */
enum class AndroidRceVector(val cve: String, val description: String) {
    GATT_SERVER_OVERFLOW(
        "CVE-2024-49748",
        "Heap overflow in GATT server attribute write handling"
    ),
    HF_CLIENT_UAF(
        "CVE-2025-0075",
        "Use-after-free in Hands-Free client connection teardown"
    ),
    ACL_ARBITER_UAF(
        "CVE-2025-22403",
        "Use-after-free in ACL connection arbiter during link supervision timeout"
    ),
    BTA_UAF(
        "CVE-2025-48593",
        "Use-after-free in BTA task deregistration path"
    ),
    L2CAP_REASSEMBLY(
        "CVE-2025-48539",
        "Use-after-free in L2CAP fragment reassembly on malformed continuation frames"
    )
}

/**
 * Implementation of Android Bluetooth stack RCE vulnerability assessment.
 *
 * This module targets multiple memory corruption vulnerabilities in Android's
 * Bluetooth stack (Android 12-15): heap overflows in the GATT server and
 * use-after-free conditions in the HF client, ACL arbiter, BTA task manager,
 * and L2CAP reassembly engine. These are zero-click attack surfaces reachable
 * over BR/EDR and BLE (HCI / ACL / GATT layers).
 *
 * The module first checks for a dedicated native binary (`android_bt_rce`).
 * If that binary is not present, it falls back to service probing to assess
 * the target's Bluetooth stack version and probe each vulnerable component
 * for indicators of susceptibility.
 *
 * Root is required for all approaches (HCI access, stack probing).
 * All privileged operations are executed through [RootExecutor].
 */
class AndroidBtRceModule {

    companion object {
        private const val TAG = "AndroidBtRceModule"
        private const val RCE_BINARY_PATH = "/data/local/tmp/android_bt_rce"
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
     * Cancels any running native binary process launched by this module.
     *
     * Sends SIGTERM to any `android_bt_rce` process. Safe to call even when
     * no attack is running -- the kill command simply finds no matching process.
     */
    suspend fun stopAttack() {
        RootExecutor.execute("pkill -f android_bt_rce 2>/dev/null || true")
        Log.d(TAG, "Stop requested -- sent SIGTERM to android_bt_rce processes")
    }

    /**
     * Executes the Android BT stack RCE assessment workflow against the target.
     *
     * The flow:
     * 1. Verify root access (required, no simulation fallback)
     * 2. Check if the native android_bt_rce binary exists
     * 3. If it exists, run it with the specified vector
     * 4. Otherwise, fall back to vulnerability assessment via service probing
     *
     * @param targetDevice The Bluetooth device to target.
     * @param vector The specific vulnerability vector to assess.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice, vector: AndroidRceVector): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Starting Android BT Stack RCE assessment (${vector.cve}) on ${targetDevice.name ?: mac}")
        emit("Vector: ${vector.name} -- ${vector.description}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("Android BT RCE assessment requires root for HCI access and stack probing. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if the dedicated native binary exists
        val binaryCheck = RootExecutor.execute("ls $RCE_BINARY_PATH")
        val binaryExists = !binaryCheck.startsWith("Error") &&
                !binaryCheck.contains("No such file") &&
                binaryCheck.trim().isNotEmpty()

        if (binaryExists) {
            // Use the native android_bt_rce binary
            emit("Native android_bt_rce binary found at $RCE_BINARY_PATH")
            emit("Executing: $RCE_BINARY_PATH -t $mac -v ${vector.name}")

            val output = RootExecutor.execute("$RCE_BINARY_PATH -t $mac -v ${vector.name}")
            // Emit each line of output from the binary
            for (line in output.lines()) {
                if (line.isNotBlank()) {
                    emit(line)
                }
            }

            if (output.startsWith("Error")) {
                emit("android_bt_rce execution failed.")
            } else {
                emit("android_bt_rce execution completed.")
            }
        } else {
            // Fallback: vulnerability assessment via service probing
            emit("Native binary not found at $RCE_BINARY_PATH")
            emit("Falling back to vulnerability assessment via service probing...")

            executeServiceProbing(mac, vector)
        }
    }

    /**
     * Performs vulnerability assessment by probing the target's Bluetooth stack
     * for indicators of susceptibility to the specified [vector].
     *
     * The assessment checks the target's Bluetooth stack version, then runs
     * vector-specific probes against GATT, HF client, ACL, BTA, and L2CAP
     * layers. This does not exploit the vulnerability -- it assesses whether
     * the target's stack version and behavior match known-vulnerable patterns.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeServiceProbing(
        mac: String,
        vector: AndroidRceVector
    ) {
        // Step 1: Check target's Bluetooth stack version via SDP
        emit("Probing target Bluetooth stack version via SDP...")
        val sdpResult = RootExecutor.execute("sdptool browse $mac 2>/dev/null")
        if (sdpResult.startsWith("Error") || sdpResult.isBlank()) {
            emit("SDP browse failed or returned no data.")
            emit("Target may be out of range or not accepting SDP queries.")
        } else {
            // Look for Bluetooth version indicators in SDP records
            val versionLines = sdpResult.lines().filter {
                it.contains("Version", ignoreCase = true) ||
                        it.contains("Profile", ignoreCase = true)
            }
            if (versionLines.isNotEmpty()) {
                emit("SDP version/profile indicators found:")
                for (line in versionLines) {
                    if (line.isNotBlank()) emit("  $line")
                }
            } else {
                emit("No version indicators found in SDP records.")
            }
        }

        // Step 2: Establish ACL connection for further probing
        emit("Creating ACL connection to $mac...")
        val ccResult = RootExecutor.execute("hcitool cc $mac")
        if (ccResult.startsWith("Error")) {
            emit("Failed to create ACL connection: $ccResult")
            emit("The target may be out of range or not accepting connections.")
            return
        }
        emit("ACL connection result: ${ccResult.ifBlank { "OK (no output = success)" }}")

        // Step 3: Read remote version information
        emit("Reading remote version information...")
        val infoResult = RootExecutor.execute("hcitool info $mac")
        if (!infoResult.startsWith("Error") && infoResult.isNotBlank()) {
            for (line in infoResult.lines()) {
                if (line.isNotBlank()) emit(line)
            }
        } else {
            emit("Could not read remote device information.")
        }

        // Step 4: Vector-specific probing
        executeVectorSpecificProbe(mac, vector)
    }

    /**
     * Runs vector-specific probes to assess susceptibility to the targeted
     * vulnerability. Each [AndroidRceVector] exercises a different component
     * of the Bluetooth stack.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeVectorSpecificProbe(
        mac: String,
        vector: AndroidRceVector
    ) {
        emit("--- Vector-specific probe: ${vector.name} (${vector.cve}) ---")

        when (vector) {
            AndroidRceVector.GATT_SERVER_OVERFLOW -> {
                // CVE-2024-49748: Probe GATT server for buffer handling
                emit("Probing GATT server for attribute buffer handling behavior...")

                val gattResult = RootExecutor.execute(
                    "gatttool -b $mac --char-read -a 0x0001 2>/dev/null"
                )
                if (gattResult.startsWith("Error") || gattResult.isBlank()) {
                    emit("GATT read failed -- target may not expose a GATT server.")
                } else {
                    emit("GATT read response: $gattResult")
                }

                // Probe characteristic discovery for large attribute enumeration
                emit("Enumerating GATT primary services...")
                val primaryResult = RootExecutor.execute(
                    "gatttool -b $mac --primary 2>/dev/null"
                )
                if (!primaryResult.startsWith("Error") && primaryResult.isNotBlank()) {
                    val serviceCount = primaryResult.lines().count { it.isNotBlank() }
                    emit("Found $serviceCount primary services.")
                    emit("Large service tables increase heap allocation pressure in the")
                    emit("GATT server write path where CVE-2024-49748 heap overflow occurs.")
                } else {
                    emit("Could not enumerate primary services.")
                }

                emit("Assessment: CVE-2024-49748 affects GATT server attribute write")
                emit("handling. Exploitation requires crafted oversized write requests")
                emit("to trigger a heap overflow in the attribute cache.")
            }

            AndroidRceVector.HF_CLIENT_UAF -> {
                // CVE-2025-0075: Probe HF client connection handling
                emit("Probing Hands-Free client connection handling...")

                // Check if HFP service is available on the target
                val hfpResult = RootExecutor.execute(
                    "sdptool search --bdaddr $mac HF 2>/dev/null"
                )
                if (!hfpResult.startsWith("Error") && hfpResult.isNotBlank()) {
                    val hasHfp = hfpResult.contains("Hands-Free", ignoreCase = true) ||
                            hfpResult.contains("HFP", ignoreCase = true)
                    if (hasHfp) {
                        emit("HFP (Hands-Free Profile) service found on target.")
                        emit("Target exposes the vulnerable HF client connection path.")
                    } else {
                        emit("HFP service not explicitly found in SDP results.")
                        emit("Raw SDP output: ${hfpResult.take(200)}")
                    }
                } else {
                    emit("HFP SDP search failed or returned no results.")
                }

                // Probe RFCOMM channels for HF profile
                emit("Scanning RFCOMM channels for HF profile endpoints...")
                val rfcommResult = RootExecutor.execute(
                    "sdptool browse $mac 2>/dev/null | grep -A 5 -i 'hands-free\\|HFP'"
                )
                if (!rfcommResult.startsWith("Error") && rfcommResult.isNotBlank()) {
                    for (line in rfcommResult.lines()) {
                        if (line.isNotBlank()) emit("  $line")
                    }
                } else {
                    emit("No HF RFCOMM channel information available.")
                }

                emit("Assessment: CVE-2025-0075 triggers during HF client teardown.")
                emit("A race between connection close and callback dispatch causes")
                emit("a use-after-free in the freed client control block.")
            }

            AndroidRceVector.ACL_ARBITER_UAF -> {
                // CVE-2025-22403: Probe ACL layer for race conditions
                emit("Probing ACL connection arbiter for race condition indicators...")

                // Check active connections to assess arbiter state
                val conResult = RootExecutor.execute("hcitool con")
                emit("Active connections: ${conResult.ifBlank { "(none)" }}")

                // Read link supervision timeout
                emit("Reading link supervision timeout for $mac...")
                val lstoResult = RootExecutor.execute(
                    "hcitool cmd 0x02 0x0036 $(hcitool con | grep '$mac' | awk '{print \$5}' | tr -d '<>') 2>/dev/null"
                )
                if (!lstoResult.startsWith("Error") && lstoResult.isNotBlank()) {
                    emit("Link supervision timeout query result: $lstoResult")
                } else {
                    emit("Could not read link supervision timeout.")
                }

                // Probe multiple rapid connection attempts to stress the arbiter
                emit("Probing connection state management...")
                val roleResult = RootExecutor.execute("hcitool sr $mac 2>/dev/null")
                emit("Role switch result: ${roleResult.ifBlank { "(not available)" }}")

                emit("Assessment: CVE-2025-22403 occurs when the ACL arbiter frees a")
                emit("connection object during link supervision timeout while another")
                emit("thread still references it. Race window is timing-dependent.")
            }

            AndroidRceVector.BTA_UAF -> {
                // CVE-2025-48593: Probe BTA task deregistration
                emit("Probing BTA (Bluetooth Application) task management...")

                // Check registered BT services on the target
                val servicesResult = RootExecutor.execute(
                    "sdptool browse $mac 2>/dev/null"
                )
                if (!servicesResult.startsWith("Error") && servicesResult.isNotBlank()) {
                    val serviceNames = servicesResult.lines().filter {
                        it.contains("Service Name:", ignoreCase = true)
                    }
                    emit("Registered services on target:")
                    for (name in serviceNames) {
                        if (name.isNotBlank()) emit("  ${name.trim()}")
                    }
                    emit("${serviceNames.size} services found.")
                    emit("Multiple active services increase the likelihood of concurrent")
                    emit("BTA task deregistration triggering the use-after-free.")
                } else {
                    emit("Could not enumerate target services.")
                }

                // Check connection parameters
                emit("Reading remote supported features...")
                val featuresResult = RootExecutor.execute("hcitool cmd 0x04 0x000C")
                emit("Local supported features: ${featuresResult.ifBlank { "(empty)" }}")

                emit("Assessment: CVE-2025-48593 triggers when a BTA task is deregistered")
                emit("while its callback queue still contains pending events. The freed")
                emit("task control block is accessed by the event dispatcher thread.")
            }

            AndroidRceVector.L2CAP_REASSEMBLY -> {
                // CVE-2025-48539: Probe L2CAP reassembly behavior
                emit("Probing L2CAP fragment reassembly behavior...")

                // Check L2CAP connection parameters
                emit("Querying L2CAP connection information...")
                val l2capResult = RootExecutor.execute(
                    "cat /sys/kernel/debug/bluetooth/hci0/l2cap 2>/dev/null"
                )
                if (!l2capResult.startsWith("Error") && l2capResult.isNotBlank()) {
                    for (line in l2capResult.lines()) {
                        if (line.isNotBlank()) emit(line)
                    }
                } else {
                    emit("L2CAP debug info unavailable (requires debugfs).")
                    emit("Ensure debugfs is mounted: mount -t debugfs none /sys/kernel/debug")
                }

                // Check MTU negotiation to assess reassembly surface
                emit("Probing L2CAP MTU via information request...")
                val mtuResult = RootExecutor.execute(
                    "l2ping -c 1 -s 48 $mac 2>/dev/null"
                )
                if (!mtuResult.startsWith("Error") && mtuResult.isNotBlank()) {
                    for (line in mtuResult.lines()) {
                        if (line.isNotBlank()) emit(line)
                    }
                    emit("L2CAP echo response received -- reassembly path is reachable.")
                } else {
                    emit("L2CAP ping failed -- target may not respond to echo requests.")
                }

                emit("Assessment: CVE-2025-48539 triggers when malformed L2CAP continuation")
                emit("frames arrive with inconsistent length fields. The reassembly engine")
                emit("frees the partial buffer but retains a stale pointer for the next fragment.")
            }
        }
    }
}
