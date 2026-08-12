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

enum class FindHubTagMode(val description: String) {
    SCAN_FINDHUB_BEACONS("Passive scan for Google Find Hub FMDN beacons"),
    BROADCAST_FINDHUB("Advertise as a Google Find Hub tag")
}

/**
 * Module for Google Find Hub (FMDN) network research.
 *
 * Google's Find My Device Network (rebranded Find Hub) uses BLE Service Data
 * under UUID 0xFE6F rather than manufacturer data. The payload contains a 20-byte
 * Ephemeral Identifier (EID) normally derived from a provisioned shared secret.
 * This module uses deterministic rotating EIDs for detection-tool testing and
 * network-layer research purposes.
 *
 * Reference: Google FMDN Accessory Specification, DCTS spec (Google/Apple 2024).
 */
class FindHubTagModule(private val context: Context) {

    companion object {
        private const val TAG = "FindHubTagModule"
        val FMDN_SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FE6F-0000-1000-8000-00805F9B34FB")
        private const val EID_LENGTH = 20
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
        trySend("Initialising Google Find Hub beacon scanner...")

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

        trySend("Scanning for FMDN beacons (Service UUID: 0xFE6F)...")
        trySend("Listening for Google Find Hub / Android tracking network beacons...")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val serviceData = record.getServiceData(FMDN_SERVICE_UUID) ?: return

                val mac = result.device.address
                val rssi = result.rssi
                val eidHex = serviceData.take(EID_LENGTH).joinToString("") { "%02X".format(it) }
                trySend("[FMDN] Device: $mac | RSSI: $rssi dBm | EID: $eidHex")
                Log.d(TAG, "Find Hub beacon detected: $mac (RSSI: $rssi)")
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
            .setServiceUuid(FMDN_SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(SCAN_REPORT_INTERVAL_MS)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, callback)
            trySend("Scanner active. Waiting for FMDN beacons...")
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
        emit("Initialising Google Find Hub beacon broadcaster...")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth is not enabled.")
            return@flow
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            emit("ERROR: BLE Advertiser not available on this device.")
            return@flow
        }

        emit("Broadcasting as FMDN tag (Service UUID: 0xFE6F)...")
        emit("NOTE: EID is deterministic/rotating — no provisioned secret.")
        emit("Triggers FMDN-aware detection tools; real network forwarding requires registered key.")

        val payloads = generateEids()
        var broadcastCount = 0

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "FMDN broadcast started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "FMDN broadcast failed: $errorCode")
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
                        .addServiceData(FMDN_SERVICE_UUID, payload)
                        .build()

                    advertiser?.startAdvertising(settings, data, callback)
                    delay(ADVERTISE_BURST_MS)
                    advertiser?.stopAdvertising(callback)
                    delay(ADVERTISE_GAP_MS)

                    broadcastCount++
                    if (broadcastCount % 10 == 0) {
                        emit("[Broadcast] Sent $broadcastCount FMDN advertisements")
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

        Log.d(TAG, "FindHubTagModule stopped.")
    }

    fun close() {
        stop()
        scope.cancel()
    }

    /**
     * Generates 5 deterministic 20-byte EIDs for advertisement rotation.
     *
     * In a real FMDN accessory these are derived from a provisioned shared secret
     * via the Google EID scheme. Deterministic values here allow reproducible
     * detection-tool testing without a registered account.
     */
    private fun generateEids(): List<ByteArray> {
        return (0 until 5).map { index ->
            ByteArray(EID_LENGTH) { byteIndex ->
                (0xE0 + index + byteIndex).toByte()
            }
        }
    }
}
