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
import android.os.ParcelUuid
import android.util.Log
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
import kotlin.coroutines.cancellation.CancellationException

enum class TileTagMode(val description: String) {
    SCAN_TILE_DEVICES("Passive scan for Tile / Life360 trackers"),
    BROADCAST_TILE("Advertise as a Tile tracker")
}

/**
 * Module for Tile (Life360) tracking network research.
 *
 * Tile trackers use BLE Service Data under UUID 0xFEED (Tile Inc., registered
 * with Bluetooth SIG). The community-find payload has a frame type of 0x02, an
 * 8-byte device identifier, and an 8-byte nonce. Tile identifiers rotate slowly
 * (hours to days), a known privacy weakness documented in the AirGuard paper
 * (TU Darmstadt, 2022).
 *
 * Reference: AirGuard (seemoo-lab), Tile BLE reverse engineering research.
 */
class TileTagModule(private val context: Context) {

    companion object {
        private const val TAG = "TileTagModule"
        val TILE_SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
        private const val TILE_FRAME_TYPE: Byte = 0x02  // community-find mode
        private const val TILE_DEVICE_ID_LENGTH = 8
        private const val TILE_NONCE_LENGTH = 8
        private const val TILE_PAYLOAD_LENGTH = 1 + TILE_DEVICE_ID_LENGTH + TILE_NONCE_LENGTH  // 17 bytes
        private const val ADVERTISE_BURST_MS = 200L
        private const val ADVERTISE_GAP_MS = 50L
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

    @SuppressLint("MissingPermission")
    fun startScan(): Flow<String> = callbackFlow {
        trySend("Initialising Tile tracker scanner...")

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

        trySend("Scanning for Tile trackers (Service UUID: 0xFEED)...")
        trySend("Tile identifiers rotate infrequently — trackers are themselves trackable.")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val serviceData = record.getServiceData(TILE_SERVICE_UUID) ?: return

                if (serviceData.isEmpty() || serviceData[0] != TILE_FRAME_TYPE) return

                val mac = result.device.address
                val rssi = result.rssi
                val deviceIdHex = if (serviceData.size >= 9) {
                    serviceData.drop(1).take(TILE_DEVICE_ID_LENGTH).joinToString("") { "%02X".format(it) }
                } else "N/A"
                val nonceHex = if (serviceData.size >= 17) {
                    serviceData.drop(9).take(TILE_NONCE_LENGTH).joinToString("") { "%02X".format(it) }
                } else "N/A"

                trySend("[Tile] Device: $mac | RSSI: $rssi dBm | ID: $deviceIdHex | Nonce: $nonceHex")
                Log.d(TAG, "Tile tracker detected: $mac (RSSI: $rssi)")
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

        val filter = ScanFilter.Builder()
            .setServiceUuid(TILE_SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, callback)
            trySend("Scanner active. Waiting for Tile trackers...")
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

    @SuppressLint("MissingPermission")
    fun startBroadcast(): Flow<String> = flow {
        emit("Initialising Tile tracker broadcaster...")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth is not enabled.")
            return@flow
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            emit("ERROR: BLE Advertiser not available on this device.")
            return@flow
        }

        emit("Broadcasting as Tile tracker (Service UUID: 0xFEED, frame type: 0x02)...")

        val payloads = generateTilePayloads()
        var broadcastCount = 0

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Tile broadcast started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Tile broadcast failed: $errorCode")
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
                        .addServiceData(TILE_SERVICE_UUID, payload)
                        .build()

                    advertiser?.startAdvertising(settings, data, callback)
                    delay(ADVERTISE_BURST_MS)
                    advertiser?.stopAdvertising(callback)
                    delay(ADVERTISE_GAP_MS)

                    broadcastCount++
                    if (broadcastCount % 10 == 0) {
                        emit("[Broadcast] Sent $broadcastCount Tile advertisements")
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

    @SuppressLint("MissingPermission")
    fun stop() {
        activeJob?.cancel()
        activeJob = null

        scanCallback?.let { cb ->
            try {
                scanner?.stopScan(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
            scanCallback = null
        }

        advertiseCallback?.let { cb ->
            try {
                advertiser?.stopAdvertising(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping advertiser: ${e.message}")
            }
            advertiseCallback = null
        }

        Log.d(TAG, "TileTagModule stopped.")
    }

    fun close() {
        stop()
        scope.cancel()
    }

    /**
     * Generates 5 Tile community-find payloads with rotating device identifiers.
     *
     * Structure: [frame_type=0x02][device_id: 8 bytes][nonce: 8 bytes] = 17 bytes.
     * Real Tile identifiers are static for hours to days; these rotate per payload
     * for detection-tool testing.
     */
    private fun generateTilePayloads(): List<ByteArray> {
        return (0 until 5).map { index ->
            byteArrayOf(
                TILE_FRAME_TYPE,
                // 8-byte device identifier
                (0xFE - index).toByte(), 0xED.toByte(), (0x01 + index).toByte(), 0x23,
                0x45, 0x67, (0x89 + index).toByte(), 0xAB.toByte(),
                // 8-byte nonce
                (0xC0 + index).toByte(), 0xDE.toByte(), 0xF0.toByte(), 0x12,
                0x34, 0x56, (0x78 + index).toByte(), 0x9A.toByte()
            )
        }
    }
}
