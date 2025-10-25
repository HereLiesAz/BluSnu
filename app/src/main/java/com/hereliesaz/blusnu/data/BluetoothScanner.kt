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
import kotlinx.coroutines.launch

class BluetoothScanner(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val bluetoothAdapter: BluetoothAdapter,
    private val coroutineScope: CoroutineScope,
    private val bluetoothLog: BluetoothLog
) {

    private var isClassicReceiverRegistered = false
    private val bleScanner: BluetoothLeScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val classicDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
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
                            name = it.name,
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC,
                            lastSeen = System.currentTimeMillis()
                        )
                        coroutineScope.launch {
                            deviceRepository.insert(targetDevice)
                            bluetoothLog.log("Found classic device: ${targetDevice.name} (${targetDevice.macAddress})")
                        }
                    }
                }
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
                        @SuppressLint("MissingPermission")
                        val targetDevice = TargetDevice(
                            macAddress = it.address,
                            name = it.name,
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC,
                            services = uuidExtra?.map { it.toString() } ?: emptyList(),
                            lastSeen = System.currentTimeMillis()
                        )
                        coroutineScope.launch {
                            deviceRepository.insert(targetDevice)
                        }
                    }
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val targetDevice = TargetDevice(
                macAddress = device.address,
                name = device.name,
                rssi = result.rssi,
                protocol = Protocol.BLE,
                lastSeen = System.currentTimeMillis()
            )
            coroutineScope.launch {
                deviceRepository.insert(targetDevice)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services.map { it.uuid.toString() }
                @SuppressLint("MissingPermission")
                val targetDevice = TargetDevice(
                    macAddress = gatt.device.address,
                    name = gatt.device.name,
                    rssi = 0,
                    protocol = Protocol.BLE,
                    services = services,
                    lastSeen = System.currentTimeMillis()
                )
                coroutineScope.launch {
                    deviceRepository.insert(targetDevice)
                }
            }
            gatt.close()
        }
    }

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

    @SuppressLint("MissingPermission")
    fun stopClassicDiscovery() {
        if (isClassicReceiverRegistered) {
            context.unregisterReceiver(classicDiscoveryReceiver)
            isClassicReceiverRegistered = false
        }
        bluetoothAdapter.cancelDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun discoverServices(device: BluetoothDevice) {
        if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            device.fetchUuidsWithSdp()
        }
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        bleScanner.startScan(bleScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanner.stopScan(bleScanCallback)
    }
}
