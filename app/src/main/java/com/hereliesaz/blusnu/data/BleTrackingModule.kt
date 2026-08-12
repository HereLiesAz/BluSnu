package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.pow

/**
 * Data class representing a persistently tracked BLE device.
 *
 * Devices are correlated across MAC address changes using a fingerprint derived
 * from advertising data patterns, service UUIDs, manufacturer data, and TX power
 * levels. RSSI history enables distance estimation and temporal signature matching.
 *
 * @property fingerprintId A stable identifier assigned by the tracking engine when
 *   a device is first fingerprinted. Persists across MAC rotations.
 * @property currentMac The most recently observed MAC address for this device.
 * @property previousMacs All MAC addresses historically associated with this fingerprint.
 * @property firstSeen Epoch millis when this fingerprint was first observed.
 * @property lastSeen Epoch millis of the most recent advertisement from this device.
 * @property rssiHistory Recent RSSI samples (bounded to [MAX_RSSI_HISTORY] entries).
 * @property advertisingDataHash SHA-256 hex digest of the raw advertising data payload.
 * @property serviceUuids Service UUIDs advertised by this device.
 * @property manufacturerData Raw manufacturer-specific data keyed by company ID.
 * @property estimatedDistance Distance estimate in meters derived from smoothed RSSI.
 * @property confidenceScore Correlation confidence (0.0 -- 1.0) that this fingerprint
 *   correctly identifies a single physical device across address changes.
 */
data class TrackedDevice(
    val fingerprintId: String,
    val currentMac: String,
    val previousMacs: List<String> = emptyList(),
    val firstSeen: Long,
    val lastSeen: Long,
    val rssiHistory: List<Int> = emptyList(),
    val advertisingDataHash: String,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val estimatedDistance: Double = 0.0,
    val confidenceScore: Double = 0.0
)

/**
 * BLE Tracking module that defeats MAC address randomization for persistent
 * device tracking.
 *
 * Uses Android's standard BLE scanner APIs (no root required) to continuously
 * scan BLE advertisements and build device fingerprints from:
 * - Advertising data patterns (payload hash)
 * - Service UUIDs
 * - Manufacturer-specific data (company ID + payload)
 * - TX power levels
 * - RSSI patterns over time
 *
 * When a device rotates its MAC address, the module correlates the new address
 * to an existing fingerprint by comparing the composite signature. This approach
 * is inspired by UC San Diego research on physical-layer fingerprinting using
 * per-chip radio imperfections, adapted here to the userspace advertising data
 * available through Android's BLE scanner APIs.
 *
 * This module does NOT require root -- all data is obtained from
 * [android.bluetooth.le.BluetoothLeScanner] callbacks which provide RSSI,
 * advertising data, service UUIDs, and TX power in userspace.
 *
 * @property context The application context, required to access BLE scanner services.
 */
class BleTrackingModule(private val context: Context) {

    companion object {
        private const val TAG = "BleTrackingModule"

        /** Maximum RSSI samples retained per tracked device. */
        private const val MAX_RSSI_HISTORY = 50

        /**
         * Minimum fingerprint similarity score (0.0 -- 1.0) required to correlate
         * a new MAC address with an existing tracked device.
         */
        private const val CORRELATION_THRESHOLD = 0.65

        /**
         * Default TX power assumed when the advertisement does not include one.
         * -59 dBm is a common average for BLE devices at 1 meter.
         */
        private const val DEFAULT_TX_POWER_DBM = -59

        /** Path loss exponent for indoor BLE distance estimation. */
        private const val PATH_LOSS_EXPONENT = 2.0
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    /** Mutable registry of tracked devices keyed by fingerprint ID. */
    private val trackedDevices = mutableMapOf<String, TrackedDevice>()

    /** Maps a currently-known MAC to its fingerprint ID for fast lookups. */
    private val macToFingerprint = mutableMapOf<String, String>()

    /** Monotonic counter for generating fingerprint IDs. */
    private var fingerprintCounter = 0L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    @Volatile
    private var isScanning = false

    /** Exposed read-only count of currently tracked devices. */
    private val _trackedDeviceCount = MutableStateFlow(0)
    val trackedDeviceCount: StateFlow<Int> = _trackedDeviceCount

    /**
     * Starts continuous BLE advertisement scanning and tracking.
     *
     * Returns a [Flow] that emits human-readable log lines describing scan
     * events, fingerprint creation, MAC correlation, and distance estimates.
     * The flow completes when [stopTracking] is called or the scanner is
     * unavailable.
     *
     * @return A cold [Flow] of log strings.
     */
    @SuppressLint("MissingPermission")
    fun startTracking(): Flow<String> = callbackFlow {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            trySend("ERROR: Bluetooth is not enabled.")
            close()
            return@callbackFlow
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            trySend("ERROR: BLE Scanner is not available.")
            close()
            return@callbackFlow
        }

        isScanning = true
        trySend("BLE Tracking started. Scanning for advertisements...")
        trySend("No root required -- using Android BLE scanner APIs.")
        trySend("Building device fingerprints from advertising data, UUIDs, manufacturer data, TX power, and RSSI patterns.")

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!isScanning) return
                scope.launch {
                    val log = processScanResult(result)
                    if (log != null) {
                        trySend(log)
                    }
                }
            }

            override fun onBatchedScanResults(results: MutableList<ScanResult>) {
                if (!isScanning) return
                scope.launch {
                    for (result in results) {
                        val log = processScanResult(result)
                        if (log != null) {
                            trySend(log)
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val message = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan failed: already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan failed: app registration failed"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan failed: feature unsupported"
                    SCAN_FAILED_INTERNAL_ERROR -> "Scan failed: internal error"
                    else -> "Scan failed: error code $errorCode"
                }
                Log.e(TAG, message)
                trySend("ERROR: $message")
            }
        }

        try {
            scanner?.startScan(null, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            trySend("ERROR: Missing BLE scan permissions: ${e.message}")
            close()
            return@callbackFlow
        }

        awaitClose {
            isScanning = false
            try {
                scanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
        }
    }

    /**
     * Processes a single BLE scan result.
     *
     * Extracts the fingerprint components, attempts to correlate the device
     * with an existing tracked entry, and updates or creates the tracking record.
     *
     * @return A log string if a noteworthy event occurred (new device, MAC
     *   rotation detected, etc.), or null for routine updates.
     */
    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult): String? {
        val mac = result.device.address ?: return null
        val rssi = result.rssi
        val scanRecord = result.scanRecord ?: return null
        val now = System.currentTimeMillis()

        // Extract fingerprint components from the scan record.
        val serviceUuids = scanRecord.serviceUuids
            ?.map { it.uuid.toString() }
            ?: emptyList()

        val manufacturerData = mutableMapOf<Int, ByteArray>()
        val sparseArray = scanRecord.manufacturerSpecificData
        if (sparseArray != null) {
            for (i in 0 until sparseArray.size()) {
                manufacturerData[sparseArray.keyAt(i)] = sparseArray.valueAt(i)
            }
        }

        val txPower = if (scanRecord.txPowerLevel != Int.MIN_VALUE) {
            scanRecord.txPowerLevel
        } else {
            DEFAULT_TX_POWER_DBM
        }

        val advertisingDataHash = hashAdvertisingData(scanRecord.bytes ?: byteArrayOf())
        val estimatedDistance = estimateDistance(rssi, txPower)

        // Build a composite fingerprint for correlation.
        val fingerprint = buildFingerprint(serviceUuids, manufacturerData, advertisingDataHash)

        // Fast path: MAC already tracked under a known fingerprint.
        val existingFpId = macToFingerprint[mac]
        if (existingFpId != null) {
            updateTrackedDevice(existingFpId, mac, rssi, estimatedDistance, now)
            return null // Routine update, no log needed.
        }

        // Attempt to correlate with an existing tracked device (MAC rotation).
        val correlatedEntry = findCorrelation(fingerprint, serviceUuids, manufacturerData, rssi)
        if (correlatedEntry != null) {
            val updated = correlateDevice(correlatedEntry, mac, rssi, advertisingDataHash, serviceUuids, manufacturerData, estimatedDistance, now)
            macToFingerprint[mac] = updated.fingerprintId
            _trackedDeviceCount.value = trackedDevices.size
            val oldMac = correlatedEntry.currentMac
            return "MAC ROTATION DETECTED: $oldMac -> $mac | Fingerprint: ${updated.fingerprintId} | Confidence: ${"%.0f".format(updated.confidenceScore * 100)}% | Distance: ${"%.1f".format(estimatedDistance)}m"
        }

        // New device -- create a fresh tracking entry.
        val fpId = generateFingerprintId()
        val newDevice = TrackedDevice(
            fingerprintId = fpId,
            currentMac = mac,
            previousMacs = emptyList(),
            firstSeen = now,
            lastSeen = now,
            rssiHistory = listOf(rssi),
            advertisingDataHash = advertisingDataHash,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            estimatedDistance = estimatedDistance,
            confidenceScore = 1.0
        )
        trackedDevices[fpId] = newDevice
        macToFingerprint[mac] = fpId
        _trackedDeviceCount.value = trackedDevices.size

        val name = try { result.device.name } catch (_: SecurityException) { null }
        val displayName = name ?: mac
        return "NEW DEVICE: $displayName | Fingerprint: $fpId | UUIDs: ${serviceUuids.size} | Distance: ${"%.1f".format(estimatedDistance)}m"
    }

    /**
     * Updates an existing tracked device with a new RSSI sample and timestamp.
     */
    private fun updateTrackedDevice(
        fpId: String,
        mac: String,
        rssi: Int,
        estimatedDistance: Double,
        now: Long
    ) {
        val existing = trackedDevices[fpId] ?: return
        val updatedHistory = (existing.rssiHistory + rssi).takeLast(MAX_RSSI_HISTORY)
        trackedDevices[fpId] = existing.copy(
            currentMac = mac,
            lastSeen = now,
            rssiHistory = updatedHistory,
            estimatedDistance = estimatedDistance
        )
    }

    /**
     * Correlates a new MAC address with an existing tracked device entry,
     * recording the MAC rotation.
     */
    private fun correlateDevice(
        existing: TrackedDevice,
        newMac: String,
        rssi: Int,
        advertisingDataHash: String,
        serviceUuids: List<String>,
        manufacturerData: Map<Int, ByteArray>,
        estimatedDistance: Double,
        now: Long
    ): TrackedDevice {
        // Remove old MAC mapping.
        macToFingerprint.remove(existing.currentMac)

        val updatedHistory = (existing.rssiHistory + rssi).takeLast(MAX_RSSI_HISTORY)
        val updatedPrevious = if (existing.currentMac !in existing.previousMacs) {
            existing.previousMacs + existing.currentMac
        } else {
            existing.previousMacs
        }

        val confidence = calculateCorrelationConfidence(
            existing, serviceUuids, manufacturerData, advertisingDataHash, rssi
        )

        val updated = existing.copy(
            currentMac = newMac,
            previousMacs = updatedPrevious,
            lastSeen = now,
            rssiHistory = updatedHistory,
            advertisingDataHash = advertisingDataHash,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            estimatedDistance = estimatedDistance,
            confidenceScore = confidence
        )
        trackedDevices[existing.fingerprintId] = updated
        return updated
    }

    /**
     * Searches existing tracked devices for one whose fingerprint matches the
     * new observation closely enough to be considered the same physical device.
     *
     * @return The matching [TrackedDevice] or null if no correlation exceeds
     *   [CORRELATION_THRESHOLD].
     */
    private fun findCorrelation(
        fingerprint: String,
        serviceUuids: List<String>,
        manufacturerData: Map<Int, ByteArray>,
        rssi: Int
    ): TrackedDevice? {
        var bestMatch: TrackedDevice? = null
        var bestScore = 0.0

        for (device in trackedDevices.values) {
            val score = calculateCorrelationConfidence(
                device, serviceUuids, manufacturerData, "", rssi
            )
            if (score > bestScore && score >= CORRELATION_THRESHOLD) {
                bestScore = score
                bestMatch = device
            }
        }
        return bestMatch
    }

    /**
     * Calculates a composite correlation confidence score (0.0 -- 1.0) between
     * an existing tracked device and a new observation.
     *
     * Scoring factors:
     * - Service UUID overlap (weighted 0.35)
     * - Manufacturer data company ID match (weighted 0.30)
     * - Manufacturer data payload similarity (weighted 0.15)
     * - RSSI proximity to the device's recent average (weighted 0.10)
     * - Advertising data hash match (weighted 0.10)
     */
    private fun calculateCorrelationConfidence(
        existing: TrackedDevice,
        newServiceUuids: List<String>,
        newManufacturerData: Map<Int, ByteArray>,
        newAdvHash: String,
        newRssi: Int
    ): Double {
        var score = 0.0

        // Service UUID overlap (0.35).
        if (existing.serviceUuids.isNotEmpty() && newServiceUuids.isNotEmpty()) {
            val intersection = existing.serviceUuids.intersect(newServiceUuids.toSet())
            val union = existing.serviceUuids.union(newServiceUuids.toSet())
            if (union.isNotEmpty()) {
                score += 0.35 * (intersection.size.toDouble() / union.size)
            }
        } else if (existing.serviceUuids.isEmpty() && newServiceUuids.isEmpty()) {
            // Both have no UUIDs -- weak positive signal.
            score += 0.10
        }

        // Manufacturer data company ID match (0.30).
        if (existing.manufacturerData.isNotEmpty() && newManufacturerData.isNotEmpty()) {
            val existingKeys = existing.manufacturerData.keys
            val newKeys = newManufacturerData.keys
            val keyIntersection = existingKeys.intersect(newKeys)
            if (keyIntersection.isNotEmpty()) {
                score += 0.30

                // Manufacturer data payload similarity (0.15).
                var payloadSimilarity = 0.0
                var compared = 0
                for (key in keyIntersection) {
                    val existingPayload = existing.manufacturerData[key] ?: continue
                    val newPayload = newManufacturerData[key] ?: continue
                    payloadSimilarity += byteArraySimilarity(existingPayload, newPayload)
                    compared++
                }
                if (compared > 0) {
                    score += 0.15 * (payloadSimilarity / compared)
                }
            }
        }

        // RSSI proximity (0.10).
        if (existing.rssiHistory.isNotEmpty()) {
            val avgRssi = existing.rssiHistory.average()
            val rssiDiff = abs(avgRssi - newRssi)
            // Within 10 dBm is a strong match; beyond 30 dBm is no match.
            val rssiScore = (1.0 - (rssiDiff / 30.0)).coerceIn(0.0, 1.0)
            score += 0.10 * rssiScore
        }

        // Advertising data hash match (0.10).
        if (newAdvHash.isNotEmpty() && existing.advertisingDataHash == newAdvHash) {
            score += 0.10
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Computes the byte-level similarity between two byte arrays as a ratio
     * of matching bytes to total length.
     */
    private fun byteArraySimilarity(a: ByteArray, b: ByteArray): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val maxLen = maxOf(a.size, b.size)
        if (maxLen == 0) return 1.0
        val minLen = minOf(a.size, b.size)
        var matches = 0
        for (i in 0 until minLen) {
            if (a[i] == b[i]) matches++
        }
        return matches.toDouble() / maxLen
    }

    /**
     * Builds a composite fingerprint string from the main identifying components.
     */
    private fun buildFingerprint(
        serviceUuids: List<String>,
        manufacturerData: Map<Int, ByteArray>,
        advertisingDataHash: String
    ): String {
        val uuidPart = serviceUuids.sorted().joinToString(",")
        val mfgPart = manufacturerData.keys.sorted().joinToString(",")
        return "$uuidPart|$mfgPart|$advertisingDataHash"
    }

    /**
     * Generates a SHA-256 hex digest of the raw advertising data bytes.
     */
    private fun hashAdvertisingData(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(bytes)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to hash advertising data: ${e.message}")
            ""
        }
    }

    /**
     * Estimates the distance to a BLE device using the log-distance path loss model.
     *
     * Formula: distance = 10 ^ ((txPower - rssi) / (10 * N))
     *
     * @param rssi The received signal strength in dBm.
     * @param txPower The calibrated RSSI at 1 meter.
     * @return Estimated distance in meters.
     */
    private fun estimateDistance(rssi: Int, txPower: Int): Double {
        return 10.0.pow((txPower - rssi).toDouble() / (10 * PATH_LOSS_EXPONENT))
    }

    /**
     * Generates a unique fingerprint ID for a newly discovered device.
     */
    private fun generateFingerprintId(): String {
        fingerprintCounter++
        return "FP-${"%04d".format(fingerprintCounter)}"
    }

    /**
     * Returns a snapshot of all currently tracked devices.
     */
    fun getTrackedDevices(): List<TrackedDevice> {
        return trackedDevices.values.toList()
    }

    /**
     * Stops the active BLE tracking scan.
     */
    fun stopTracking() {
        isScanning = false
        scanJob?.cancel()
        scanJob = null
    }

    /**
     * Stops tracking and releases all resources.
     */
    fun close() {
        stopTracking()
        trackedDevices.clear()
        macToFingerprint.clear()
        _trackedDeviceCount.value = 0
        scope.cancel()
    }
}
