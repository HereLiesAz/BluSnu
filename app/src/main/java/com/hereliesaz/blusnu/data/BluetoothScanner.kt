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
 * Core class responsible for discovering Bluetooth devices.
 *
 * This class unifies the scanning process for both Bluetooth Classic (BR/EDR) and
 * Bluetooth Low Energy (BLE). It handles:
 * 1. Managing the Classic discovery via BroadcastReceiver.
 * 2. Managing the BLE scanning via BluetoothLeScanner.
 * 3. Processing scan results and converting them into [TargetDevice] entities.
 * 4. Initiating service discovery (SDP/GATT) to populate device capabilities.
 * 5. Persisting results to the database via [DeviceRepository].
 *
 * @property context The application context, required for registering receivers and connecting GATT.
 * @property deviceRepository The repository to store discovered devices.
 * @property bluetoothAdapter The system BluetoothAdapter.
 * @property bluetoothLog The logger for recording events.
 */
class BluetoothScanner(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val bluetoothAdapter: BluetoothAdapter,
    private val bluetoothLog: BluetoothLog
) {

    // Flag to track if the BroadcastReceiver for Classic discovery is currently registered.
    // This prevents crashing by trying to unregister a receiver that wasn't registered.
    private var isClassicReceiverRegistered = false

    // Lazy initialization of the BLE Scanner.
    // This might be null if Bluetooth is turned off or not supported on the device.
    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    /**
     * BroadcastReceiver to handle Bluetooth Classic discovery events.
     * Android sends intents (ACTION_FOUND, ACTION_UUID) when devices are discovered
     * or when their SDP records (UUIDs) are fetched.
     */
    private val classicDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // ACTION_FOUND: Fired when a device is discovered during a scan.
                BluetoothDevice.ACTION_FOUND -> {
                    // Extract the BluetoothDevice object from the intent extras.
                    // Handle API level differences for Parcelable extraction (Tiramisu/Android 13+ vs older).
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    // If a valid device was found:
                    device?.let {
                        // Suppress MissingPermission because checking permissions here is redundant/handled upstream.
                        @SuppressLint("MissingPermission")
                        val targetDevice = TargetDevice(
                            macAddress = it.address, // The hardware address.
                            name = it.name, // The friendly name (e.g., "Galaxy S21").
                            // RSSI is critical for distance estimation. Default to MIN_VALUE if missing.
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC, // Mark as Classic protocol.
                            lastSeen = System.currentTimeMillis() // Timestamp for "Last Seen".
                        )
                        // Save the device to the database asynchronously.
                        insertDevice(targetDevice)

                        // Log the discovery event.
                        CoroutineScope(Dispatchers.IO).launch {
                            bluetoothLog.log("Found classic device: ${targetDevice.name} (${targetDevice.macAddress})")
                        }
                    }
                }

                // ACTION_UUID: Fired when SDP (Service Discovery Protocol) records are retrieved.
                // This usually happens after a fetchUuidsWithSdp() call.
                BluetoothDevice.ACTION_UUID -> {
                    // Extract the device object again to associate the UUIDs with the correct entity.
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    // Extract the array of Parcelable UUIDs.
                    val uuidExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, Parcelable::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                    }

                    device?.let {
                        @SuppressLint("MissingPermission")
                        val targetDevice = TargetDevice(
                            macAddress = it.address,
                            name = it.name,
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC,
                            // Convert the ParcelableUUIDs to a List of Strings.
                            // If uuidExtra is null, use an empty list.
                            services = uuidExtra?.map { it.toString() } ?: emptyList(),
                            lastSeen = System.currentTimeMillis()
                        )
                        // Update the device in the DB with the newly found services.
                        insertDevice(targetDevice)
                    }
                }
            }
        }
    }

    /**
     * Callback for BLE scan results.
     * This is invoked by the system whenever a BLE advertisement packet is received.
     */
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // Create a TargetDevice from the scan result.
            val targetDevice = TargetDevice(
                macAddress = device.address,
                name = device.name, // Often null in BLE advertisements; Name is usually in the scan record.
                rssi = result.rssi,
                protocol = Protocol.BLE,
                lastSeen = System.currentTimeMillis()
            )
            // Save/Update the device.
            insertDevice(targetDevice)
        }
    }

    /**
     * Callback for GATT (Generic Attribute Profile) operations.
     * Used here specifically for service enumeration on BLE devices.
     */
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // When the connection state changes to CONNECTED, we can start discovering services.
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Initiate service discovery. This is an asynchronous operation.
                // Results will be delivered to onServicesDiscovered.
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // If service discovery succeeded:
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Extract the UUIDs of all discovered services.
                val services = gatt.services.map { it.uuid.toString() }

                @SuppressLint("MissingPermission")
                // Create/Update the TargetDevice with the new service list.
                val targetDevice = TargetDevice(
                    macAddress = gatt.device.address,
                    name = gatt.device.name,
                    rssi = 0, // We don't get RSSI from a GATT connection callback usually, so we leave it 0 (Repository handles merge).
                    protocol = Protocol.BLE,
                    services = services,
                    lastSeen = System.currentTimeMillis()
                )
                insertDevice(targetDevice)
            }
            // Close the GATT connection to save battery and resources.
            // We only connected to enumerate services.
            gatt.close()
        }
    }

    /**
     * Helper function to insert a device into the repository on a background thread.
     */
    fun insertDevice(device: TargetDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            deviceRepository.insert(device)
        }
    }

    /**
     * Starts the Classic Bluetooth discovery process.
     * Registers the BroadcastReceiver to listen for results.
     */
    @SuppressLint("MissingPermission")
    fun startClassicDiscovery() {
        // Only register if not already registered to avoid exceptions.
        if (!isClassicReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            // Also listen for UUID updates (SDP results).
            filter.addAction(BluetoothDevice.ACTION_UUID)
            context.registerReceiver(classicDiscoveryReceiver, filter)
            isClassicReceiverRegistered = true
        }
        // Start the actual hardware discovery.
        bluetoothAdapter.startDiscovery()
    }

    /**
     * Stops the Classic Bluetooth discovery process.
     * Unregisters the receiver.
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
     * Initiates detailed service discovery for a specific device.
     * The method differs based on the protocol (Classic vs BLE).
     *
     * @param device The BluetoothDevice to probe.
     */
    @SuppressLint("MissingPermission")
    fun discoverServices(device: BluetoothDevice) {
        // For Classic or Dual-mode devices, use SDP (Service Discovery Protocol).
        if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            // Asynchronously fetches UUIDs. Result is broadcast via ACTION_UUID.
            device.fetchUuidsWithSdp()
        }
        // For BLE or Dual-mode devices, connect via GATT.
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            // Connect to the GATT server.
            // autoConnect = false for faster initial connection.
            device.connectGatt(context, false, gattCallback)
        }
    }

    /**
     * Starts the BLE scanning process.
     */
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (bleScanner != null) {
            // Start scanning with the defined callback.
            bleScanner?.startScan(bleScanCallback)
        } else {
            // Log an error if the scanner is unavailable (e.g., BT disabled).
            CoroutineScope(Dispatchers.IO).launch {
                bluetoothLog.log("Error: Bluetooth LE Scanner not available (Bluetooth might be off)")
            }
        }
    }

    /**
     * Stops the BLE scanning process.
     */
    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanner?.stopScan(bleScanCallback)
    }
}
