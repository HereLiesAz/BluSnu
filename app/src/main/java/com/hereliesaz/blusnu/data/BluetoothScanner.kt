package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.os.Parcelable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A comprehensive wrapper for Android's Bluetooth scanning capabilities.
 *
 * <p>
 * This class abstracts the complexity of handling two distinct scanning APIs:
 * 1. <b>Bluetooth Classic (BR/EDR):</b> Uses [BluetoothAdapter.startDiscovery] and a [BroadcastReceiver] to listen for intents.
 * 2. <b>Bluetooth Low Energy (BLE):</b> Uses [BluetoothLeScanner] with a [ScanCallback].
 * </p>
 *
 * It is also responsible for Service Discovery (SDP/GATT) to enumerate features of discovered devices.
 * All results are normalized into [TargetDevice] objects and persisted via the [DeviceRepository].
 *
 * @property context Application context for registering receivers and connecting GATT.
 * @property deviceRepository Repository to persist discovered devices.
 * @property bluetoothAdapter The system Bluetooth Adapter.
 * @property bluetoothLog Logger for tracking scanning events.
 */
class BluetoothScanner(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val bluetoothAdapter: BluetoothAdapter,
    private val bluetoothLog: BluetoothLog
) {

    /**
     * Flag to track if the Classic discovery receiver is currently registered to avoid Leaks or Crashes.
     */
    private var isClassicReceiverRegistered = false

    /**
     * Lazy initialization of the BLE Scanner. This can be null if Bluetooth is disabled.
     */
    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    // ---------------------------------------------------------------------------------------------
    // Bluetooth Classic Discovery Implementation
    // ---------------------------------------------------------------------------------------------

    /**
     * BroadcastReceiver that listens for Bluetooth Classic discovery events.
     * Android transmits found devices via global broadcasts rather than callbacks for Classic.
     */
    private val classicDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Fired when a device is found during discovery.
                BluetoothDevice.ACTION_FOUND -> {
                    // Handle API level differences for getting Parcelables
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    device?.let {
                        @SuppressLint("MissingPermission")
                        val targetDevice = TargetDevice(
                            macAddress = it.address,
                            name = it.name, // Name might be null if not cached
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC,
                            lastSeen = System.currentTimeMillis()
                        )
                        insertDevice(targetDevice)

                        // Log finding for debug purposes
                        CoroutineScope(Dispatchers.IO).launch {
                            bluetoothLog.log("Found classic device: ${targetDevice.name} (${targetDevice.macAddress})")
                        }
                    }
                }

                // Fired when SDP (Service Discovery Protocol) UUIDs are fetched.
                BluetoothDevice.ACTION_UUID -> {
                     val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    val uuidExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, Parcelable::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                    }

                    device?.let {
                        // Create a device entry updated with the list of supported Services (UUIDs)
                        @SuppressLint("MissingPermission")
                        val targetDevice = TargetDevice(
                            macAddress = it.address,
                            name = it.name,
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC,
                            services = uuidExtra?.map { it.toString() } ?: emptyList(),
                            lastSeen = System.currentTimeMillis()
                        )
                        insertDevice(targetDevice)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bluetooth Low Energy (BLE) Scanning Implementation
    // ---------------------------------------------------------------------------------------------

    /**
     * Callback for handling BLE scan results.
     */
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // BLE devices advertise data packets that can be parsed immediately.
            // Currently we just extract the basic info.
            val targetDevice = TargetDevice(
                macAddress = device.address,
                name = device.name,
                rssi = result.rssi,
                protocol = Protocol.BLE,
                lastSeen = System.currentTimeMillis()
            )
            insertDevice(targetDevice)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Service Enumeration (GATT/SDP)
    // ---------------------------------------------------------------------------------------------

    /**
     * GATT Callback for service discovery.
     * When we connect to a BLE device to enumerate its services, these callbacks are fired.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Connected successfully, now request the list of services (Services, Characteristics)
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Extract UUIDs
                val services = gatt.services.map { it.uuid.toString() }

                // Update the device record in the database with the discovered services
                @SuppressLint("MissingPermission")
                val targetDevice = TargetDevice(
                    macAddress = gatt.device.address,
                    name = gatt.device.name,
                    rssi = 0, // RSSI is not available during GATT connection events usually
                    protocol = Protocol.BLE,
                    services = services,
                    lastSeen = System.currentTimeMillis()
                )
                insertDevice(targetDevice)
            }
            // Always close the connection after discovery to save battery and free the connection slot
            gatt.close()
        }
    }

    /**
     * Helper to insert or update a device in the repository on a background thread.
     */
    fun insertDevice(device: TargetDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            deviceRepository.insert(device)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    /**
     * Starts the heavy Bluetooth Classic discovery process (approx 12 seconds).
     * Registers the BroadcastReceiver to listen for results.
     */
    @SuppressLint("MissingPermission")
    fun startClassicDiscovery() {
        if (!isClassicReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothDevice.ACTION_UUID)
            context.registerReceiver(classicDiscoveryReceiver, filter)
            isClassicReceiverRegistered = true
        }
        bluetoothAdapter.startDiscovery()
    }

    /**
     * Stops Classic discovery and unregisters the receiver.
     */
    @SuppressLint("MissingPermission")
    fun stopClassicDiscovery() {
        if (isClassicReceiverRegistered) {
            context.unregisterReceiver(classicDiscoveryReceiver)
            isClassicReceiverRegistered = false
        }
        bluetoothAdapter.cancelDiscovery()
    }

    /**
     * Initiates Service Discovery for a specific device.
     * - For Classic: Triggers [BluetoothDevice.fetchUuidsWithSdp].
     * - For BLE: Connects via GATT and calls [BluetoothGatt.discoverServices].
     */
    @SuppressLint("MissingPermission")
    fun discoverServices(device: BluetoothDevice) {
        if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            device.fetchUuidsWithSdp()
        }
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            // autoConnect=false provides a faster initial connection attempt
            device.connectGatt(context, false, gattCallback)
        }
    }

    /**
     * Starts the BLE Scanner.
     */
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (bleScanner != null) {
            bleScanner?.startScan(bleScanCallback)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                bluetoothLog.log("Error: Bluetooth LE Scanner not available (Bluetooth might be off)")
            }
        }
    }

    /**
     * Stops the BLE Scanner.
     */
    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanner?.stopScan(bleScanCallback)
    }
}
