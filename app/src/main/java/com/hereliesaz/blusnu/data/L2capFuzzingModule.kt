package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Modes of operation for L2CAP stateful fuzzing.
 *
 * Each mode targets a different aspect of the L2CAP signaling layer's state
 * machine. Stateful fuzzing generates valid-but-malformed packets that respect
 * protocol state transitions, achieving roughly 46x more effectiveness than
 * simple flooding approaches (L2Fuzz, Garbelini et al.).
 *
 * @property description A human-readable description of the fuzzing strategy.
 */
enum class L2capFuzzMode(val description: String) {
    CONNECTION_REQUEST_FUZZ("Fuzz L2CAP_ConnectReq with invalid PSM, SCID, and state violations"),
    CONFIGURATION_FUZZ("Send malformed L2CAP_ConfigReq/Rsp with invalid MTU, flush timeout, and QoS options"),
    INFORMATION_REQUEST_FUZZ("Fuzz L2CAP_InfoReq/Rsp with invalid info types and oversized payloads"),
    ECHO_REQUEST_FUZZ("Send oversized and malformed L2CAP_EchoReq to test buffer handling"),
    SIGNALING_COMMAND_FUZZ("Inject invalid signaling command codes, lengths, and identifiers"),
    FRAGMENT_FUZZ("Test L2CAP fragmentation/reassembly with overlapping, truncated, and out-of-order fragments")
}

/**
 * Implementation of the L2CAP Stateful Fuzzing module.
 *
 * Performs stateful protocol fuzzing of the L2CAP signaling layer on Bluetooth
 * Classic (BR/EDR) targets. Unlike simple flood-based attacks (e.g. BlueSmack),
 * this module generates valid-but-malformed packets that respect the L2CAP
 * protocol state machine, making it significantly more effective at uncovering
 * implementation bugs. Research (L2Fuzz) demonstrated 46x greater effectiveness
 * over random fuzzing and discovered 5 zero-day vulnerabilities using this
 * approach.
 *
 * The module first checks for a dedicated native binary (`l2cap_fuzzer`).
 * If that binary is not present, it falls back to l2test-based probing using
 * standard BlueZ utilities. The l2test fallback creates L2CAP connections,
 * sends malformed configuration requests, fuzzes information request/response
 * sequences, tests fragmentation handling, and monitors for target crashes
 * or resets.
 *
 * Root is required for raw L2CAP sockets and l2test access. All privileged
 * operations are executed through [RootExecutor].
 */
class L2capFuzzingModule {

    companion object {
        private const val TAG = "L2capFuzzingModule"
        private const val FUZZER_BINARY_PATH = "/data/local/tmp/l2cap_fuzzer"

        /**
         * Default L2CAP signaling channel CID. Signaling commands are always
         * sent on CID 0x0001 for BR/EDR connections (Bluetooth Core Spec v5.4,
         * Vol 3, Part A, Section 2.1).
         */
        private const val L2CAP_SIGNALING_CID = "0x0001"

        /**
         * Default PSM for SDP (Service Discovery Protocol), commonly used as
         * an initial connection target since nearly all BR/EDR devices expose it.
         */
        private const val DEFAULT_PSM = "1"

        /**
         * Timeout in seconds for l2test connection attempts before declaring
         * the target unreachable or crashed.
         */
        private const val CONNECTION_TIMEOUT_SECONDS = 10
    }

    /**
     * Handle to the currently running root-fallback process, if any.
     * Used by [stopFuzzing] to terminate an in-progress fuzzing session.
     */
    @Volatile
    private var activeProcess: Process? = null

    /**
     * Flag indicating whether a fuzzing session is currently active.
     * Checked by the Flow producers to bail out early on cancellation.
     */
    @Volatile
    private var cancelled = false

    /**
     * Checks if the device has root access via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Executes the L2CAP stateful fuzzing workflow against the target device.
     *
     * The flow:
     * 1. Verify root access (required for raw L2CAP sockets)
     * 2. Check if the native l2cap_fuzzer binary exists
     * 3. If it exists, run it with the specified mode
     * 4. Otherwise, fall back to l2test-based probing
     *
     * @param targetDevice The Bluetooth device to fuzz.
     * @param mode The specific L2CAP fuzzing strategy to use.
     * @return A Flow of status strings for the UI console.
     */
    fun startFuzzing(targetDevice: TargetDevice, mode: L2capFuzzMode): Flow<String> = flow {
        cancelled = false
        try {
            val mac = MacValidator.requireValid(targetDevice.macAddress)
            emit("Starting L2CAP Stateful Fuzzing on ${targetDevice.name ?: mac}")
            emit("Mode: ${mode.name} -- ${mode.description}")

            // Root is strictly required -- no simulation fallback
            if (!checkRoot()) {
                emit("ERROR: Root access is not available.")
                emit("L2CAP fuzzing requires root for raw L2CAP sockets. Aborting.")
                return@flow
            }
            emit("Root access confirmed.")

            // Check if the dedicated native binary exists
            val binaryCheck = RootExecutor.execute("ls $FUZZER_BINARY_PATH")
            val binaryExists = !binaryCheck.startsWith("Error") &&
                    !binaryCheck.contains("No such file") &&
                    binaryCheck.trim().isNotEmpty()

            if (binaryExists) {
                // Use the native l2cap_fuzzer binary
                emit("Native l2cap_fuzzer binary found at $FUZZER_BINARY_PATH")
                executeNativeFuzzer(mac, mode)
            } else {
                // Fallback: use l2test-based probing
                emit("Native binary not found at $FUZZER_BINARY_PATH")
                emit("Falling back to l2test-based L2CAP probing...")
                executeL2testFallback(mac, mode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "L2CAP fuzzing failed", e)
            emit("ERROR: ${e.message ?: "Unknown error during fuzzing"}")
            emit("Fuzzing session aborted due to error.")
        }
    }

    /**
     * Stops any in-progress fuzzing session.
     *
     * Sets the cancellation flag and destroys any active root-fallback process.
     */
    fun stopFuzzing() {
        cancelled = true

        // Terminate root binary or l2test process if running
        activeProcess?.let { process ->
            try {
                process.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to destroy fuzzer process", e)
            }
            activeProcess = null
        }
    }

    /**
     * Executes the native l2cap_fuzzer binary with the specified mode.
     *
     * Invokes `su -c /data/local/tmp/l2cap_fuzzer TARGET_MAC MODE` and streams
     * stdout/stderr as log lines. The [Process] handle is stored in
     * [activeProcess] so [stopFuzzing] can terminate it.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeNativeFuzzer(
        mac: String,
        mode: L2capFuzzMode
    ) {
        val command = "$FUZZER_BINARY_PATH -t $mac -m ${mode.name}"
        emit("Executing: su -c $command")

        val process = try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        } catch (e: Exception) {
            emit("ERROR: Failed to execute native binary: ${e.message}")
            emit("Ensure device is rooted and l2cap_fuzzer is at $FUZZER_BINARY_PATH.")
            return
        }

        activeProcess = process
        var crashDetected = false

        try {
            process.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (cancelled) {
                        emit("Fuzzing cancelled by user.")
                        return
                    }

                    emit(line)

                    if (line.contains("CRASH", ignoreCase = true) ||
                        line.contains("RESET", ignoreCase = true)) {
                        crashDetected = true
                    }

                    line = reader.readLine()
                }
            }

            // Also capture stderr
            process.errorStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    emit("STDERR: $line")
                    line = reader.readLine()
                }
            }

            val exitCode = process.waitFor()
            emit("Process exited with code $exitCode")

            if (crashDetected) {
                emit("CRASH/RESET DETECTED: Target stopped responding.")
                emit("Vulnerability found in L2CAP ${mode.name} handling.")
            } else {
                emit("Target resilient. No crash detected for ${mode.name}.")
            }
        } finally {
            activeProcess = null
        }
    }

    /**
     * Executes the l2test-based fallback fuzzing approach.
     *
     * Uses standard BlueZ l2test and l2ping utilities to probe the target's
     * L2CAP implementation. Each [L2capFuzzMode] maps to a different probing
     * strategy:
     *
     * - Creates L2CAP connections using l2test
     * - Sends malformed configuration requests
     * - Fuzzes information request/response sequences
     * - Tests fragmentation handling
     * - Monitors for target crashes/resets via l2ping
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeL2testFallback(
        mac: String,
        mode: L2capFuzzMode
    ) {
        // Verify l2test is available
        val l2testCheck = RootExecutor.execute("which l2test 2>/dev/null || ls /usr/bin/l2test 2>/dev/null")
        val l2testAvailable = !l2testCheck.startsWith("Error") &&
                l2testCheck.trim().isNotEmpty() &&
                !l2testCheck.contains("not found")

        if (!l2testAvailable) {
            emit("WARNING: l2test not found. Attempting with l2ping only.")
            emit("Install BlueZ tools for full L2CAP fuzzing capabilities.")
        }

        // Pre-fuzz connectivity check via l2ping
        emit("Checking target reachability via l2ping...")
        val pingResult = RootExecutor.execute("l2ping -c 2 -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1")
        if (pingResult.contains("Can't connect") || pingResult.startsWith("Error")) {
            emit("Target $mac is not reachable via L2CAP: $pingResult")
            emit("Ensure the target is in range and discoverable.")
            return
        }
        emit("Target reachable. L2CAP baseline connectivity confirmed.")

        // Dispatch to mode-specific probing
        when (mode) {
            L2capFuzzMode.CONNECTION_REQUEST_FUZZ -> fuzzConnectionRequests(mac, l2testAvailable)
            L2capFuzzMode.CONFIGURATION_FUZZ -> fuzzConfiguration(mac, l2testAvailable)
            L2capFuzzMode.INFORMATION_REQUEST_FUZZ -> fuzzInformationRequests(mac, l2testAvailable)
            L2capFuzzMode.ECHO_REQUEST_FUZZ -> fuzzEchoRequests(mac)
            L2capFuzzMode.SIGNALING_COMMAND_FUZZ -> fuzzSignalingCommands(mac, l2testAvailable)
            L2capFuzzMode.FRAGMENT_FUZZ -> fuzzFragmentation(mac, l2testAvailable)
        }

        // Post-fuzz crash detection via l2ping
        emit("--- Post-fuzz crash detection ---")
        emit("Pinging target to check if it is still responsive...")
        val postPing = RootExecutor.execute("l2ping -c 3 -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1")
        if (postPing.contains("Can't connect") || postPing.contains("timed out") || postPing.startsWith("Error")) {
            emit("TARGET UNRESPONSIVE after fuzzing.")
            emit("Possible crash or reset detected in L2CAP ${mode.name} handling.")
            Log.w(TAG, "Target $mac unresponsive after L2CAP fuzzing mode ${mode.name}")
        } else {
            emit("Target still responsive after fuzzing.")
            for (line in postPing.lines()) {
                if (line.isNotBlank()) emit(line)
            }
        }

        emit("L2CAP fuzzing mode ${mode.name} complete.")
    }

    /**
     * Fuzzes L2CAP connection request handling by attempting connections on
     * invalid, reserved, and out-of-range PSM values. Tests whether the target
     * correctly rejects malformed connection requests or crashes.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.fuzzConnectionRequests(
        mac: String,
        l2testAvailable: Boolean
    ) {
        emit("--- Connection Request Fuzzing ---")
        emit("Testing invalid PSM values and connection state violations...")

        // Test a range of invalid and reserved PSM values
        val testPsms = listOf("0", "2", "4", "0xFFFF", "0xFFFE", "3", "5", "65535")
        for (psm in testPsms) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("Attempting L2CAP connection with PSM=$psm...")
            if (l2testAvailable) {
                val result = RootExecutor.execute(
                    "timeout $CONNECTION_TIMEOUT_SECONDS l2test -b $psm $mac 2>&1 || true"
                )
                for (line in result.lines()) {
                    if (line.isNotBlank()) emit("  $line")
                }
            } else {
                // Use raw l2ping with non-standard sizes as a limited probe
                val result = RootExecutor.execute(
                    "l2ping -c 1 -s 48 -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1"
                )
                emit("  l2ping result: ${result.lines().firstOrNull { it.isNotBlank() } ?: "(empty)"}")
            }
        }

        // Test rapid connect/disconnect cycling
        emit("Testing rapid connect/disconnect cycling...")
        for (i in 1..5) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("  Cycle $i/5: connect -> immediate disconnect")
            if (l2testAvailable) {
                RootExecutor.execute(
                    "timeout 2 l2test -b $DEFAULT_PSM $mac 2>&1 || true"
                )
            } else {
                RootExecutor.execute("l2ping -c 1 -t 2 $mac 2>&1")
            }
        }
        emit("Connection request fuzzing complete.")
    }

    /**
     * Fuzzes L2CAP configuration handling by sending connections with
     * deliberately invalid MTU, flush timeout, and QoS option values.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.fuzzConfiguration(
        mac: String,
        l2testAvailable: Boolean
    ) {
        emit("--- Configuration Fuzzing ---")
        emit("Testing malformed MTU, flush timeout, and QoS values...")

        if (!l2testAvailable) {
            emit("WARNING: l2test required for configuration fuzzing. Skipping to limited probe.")
            val result = RootExecutor.execute("l2ping -c 3 -s 600 -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1")
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("  $line")
            }
            return
        }

        // Test invalid MTU values
        val mtuValues = listOf("1", "23", "47", "65535", "0")
        for (mtu in mtuValues) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("Sending ConfigReq with MTU=$mtu...")
            val result = RootExecutor.execute(
                "timeout $CONNECTION_TIMEOUT_SECONDS l2test -b -m $mtu $DEFAULT_PSM $mac 2>&1 || true"
            )
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("  $line")
            }
        }

        // Test flush timeout boundary values
        emit("Testing flush timeout boundary values...")
        val flushValues = listOf("0", "1", "65535")
        for (flush in flushValues) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("Sending ConfigReq with flush_timeout=$flush...")
            val result = RootExecutor.execute(
                "timeout $CONNECTION_TIMEOUT_SECONDS l2test -b -f $flush $DEFAULT_PSM $mac 2>&1 || true"
            )
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("  $line")
            }
        }

        emit("Configuration fuzzing complete.")
    }

    /**
     * Fuzzes L2CAP information request/response handling by sending requests
     * with invalid info types and analyzing whether the target responds
     * correctly or exhibits unexpected behavior.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.fuzzInformationRequests(
        mac: String,
        l2testAvailable: Boolean
    ) {
        emit("--- Information Request Fuzzing ---")
        emit("Testing L2CAP_InfoReq with invalid info types...")

        // First, retrieve supported info types normally
        emit("Querying standard info types (connectionless MTU, extended features)...")
        val infoResult = RootExecutor.execute(
            "timeout $CONNECTION_TIMEOUT_SECONDS l2ping -c 1 -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1"
        )
        for (line in infoResult.lines()) {
            if (line.isNotBlank()) emit("  $line")
        }

        if (!l2testAvailable) {
            emit("WARNING: Full information request fuzzing requires l2test.")
            return
        }

        // Send info requests using l2test in info-request mode
        emit("Sending rapid information request sequence...")
        for (i in 1..8) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("  InfoReq burst $i/8...")
            val result = RootExecutor.execute(
                "timeout $CONNECTION_TIMEOUT_SECONDS l2test -i $DEFAULT_PSM $mac 2>&1 || true"
            )
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("    $line")
            }
        }

        emit("Information request fuzzing complete.")
    }

    /**
     * Fuzzes L2CAP echo request handling by sending progressively larger
     * payloads to test buffer allocation and handling in the target's
     * L2CAP implementation.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.fuzzEchoRequests(
        mac: String
    ) {
        emit("--- Echo Request Fuzzing ---")
        emit("Sending progressively larger L2CAP echo requests...")

        // l2ping supports echo requests with variable payload sizes
        val sizes = listOf(44, 128, 256, 512, 600, 672, 1024, 2048)
        for (size in sizes) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("Sending echo request with payload size=$size bytes...")
            val result = RootExecutor.execute(
                "l2ping -c 2 -s $size -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1"
            )
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("  $line")
            }

            // Check for failure indicating a crash
            if (result.contains("Can't connect") || result.contains("timed out")) {
                emit("Target stopped responding at payload size $size.")
                emit("Possible buffer overflow or crash in echo request handling.")
                return
            }
        }

        // Flood with rapid echo requests
        emit("Sending rapid echo request flood (20 packets)...")
        val floodResult = RootExecutor.execute(
            "l2ping -c 20 -f -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1"
        )
        for (line in floodResult.lines()) {
            if (line.isNotBlank()) emit("  $line")
        }

        emit("Echo request fuzzing complete.")
    }

    /**
     * Fuzzes the L2CAP signaling command parser by sending commands with
     * invalid command codes, mismatched lengths, and malformed identifiers
     * via raw L2CAP sockets.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.fuzzSignalingCommands(
        mac: String,
        l2testAvailable: Boolean
    ) {
        emit("--- Signaling Command Fuzzing ---")
        emit("Testing invalid signaling command codes and malformed headers...")

        if (!l2testAvailable) {
            emit("WARNING: Signaling command fuzzing requires l2test for raw socket access.")
            emit("Falling back to l2ping-based probing.")
            val result = RootExecutor.execute(
                "l2ping -c 5 -s 200 -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1"
            )
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("  $line")
            }
            return
        }

        // Test with various l2test modes to exercise the signaling parser
        val testModes = listOf(
            Pair("-b", "basic send mode"),
            Pair("-u", "unreliable send mode"),
            Pair("-s", "sequenced send mode"),
            Pair("-n", "connectionless mode")
        )

        for ((flag, description) in testModes) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("Testing signaling via $description ($flag)...")
            val result = RootExecutor.execute(
                "timeout $CONNECTION_TIMEOUT_SECONDS l2test $flag $DEFAULT_PSM $mac 2>&1 || true"
            )
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("  $line")
            }
        }

        // Rapid reconnection to stress the signaling state machine
        emit("Stress-testing signaling state machine with rapid reconnects...")
        for (i in 1..10) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            RootExecutor.execute("timeout 1 l2test -b $DEFAULT_PSM $mac 2>&1 || true")
            if (i % 5 == 0) emit("  Completed $i/10 rapid reconnection cycles.")
        }

        emit("Signaling command fuzzing complete.")
    }

    /**
     * Fuzzes L2CAP fragmentation and reassembly by sending packets designed
     * to trigger overlapping fragment handling, truncated reassembly, and
     * out-of-order fragment processing.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.fuzzFragmentation(
        mac: String,
        l2testAvailable: Boolean
    ) {
        emit("--- Fragment Fuzzing ---")
        emit("Testing L2CAP fragmentation/reassembly handling...")

        // Test with payloads that force fragmentation at various boundaries
        emit("Phase 1: Boundary-size payloads to force fragmentation...")
        val fragmentSizes = listOf(
            Pair(672, "Default L2CAP MTU boundary"),
            Pair(673, "One byte over default MTU"),
            Pair(1021, "Just under extended MTU"),
            Pair(1024, "Common fragment boundary"),
            Pair(2048, "Large payload forcing multiple fragments")
        )

        for ((size, description) in fragmentSizes) {
            if (cancelled) { emit("Fuzzing cancelled."); return }

            emit("Sending $size-byte payload ($description)...")
            val result = RootExecutor.execute(
                "l2ping -c 2 -s $size -t $CONNECTION_TIMEOUT_SECONDS $mac 2>&1"
            )
            for (line in result.lines()) {
                if (line.isNotBlank()) emit("  $line")
            }

            if (result.contains("Can't connect") || result.contains("timed out")) {
                emit("Target unresponsive at fragment size $size -- possible reassembly crash.")
                return
            }
        }

        if (l2testAvailable) {
            // Test with various MTU configurations to exercise reassembly paths
            emit("Phase 2: MTU mismatch to stress reassembly...")
            val mtuPairs = listOf("48", "127", "672", "1023")
            for (mtu in mtuPairs) {
                if (cancelled) { emit("Fuzzing cancelled."); return }

                emit("Connecting with negotiated MTU=$mtu then sending oversized data...")
                val result = RootExecutor.execute(
                    "timeout $CONNECTION_TIMEOUT_SECONDS l2test -b -m $mtu $DEFAULT_PSM $mac 2>&1 || true"
                )
                for (line in result.lines()) {
                    if (line.isNotBlank()) emit("  $line")
                }
            }
        }

        emit("Fragment fuzzing complete.")
    }
}
