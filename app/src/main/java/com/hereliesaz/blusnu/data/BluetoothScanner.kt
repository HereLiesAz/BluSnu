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

@SuppressLint("MissingPermission")
class BluetoothScanner(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val bluetoothAdapter: BluetoothAdapter
) {

    private val bleScanner: BluetoothLeScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private var isReceiverRegistered = false
    private val classicDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val targetDevice = TargetDevice(
                            macAddress = it.address,
                            name = it.name,
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC
                        )
                        deviceRepository.addDevice(targetDevice)
                    }
                }
                BluetoothDevice.ACTION_UUID -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val uuids = device?.uuids
                    uuids?.let {
                        deviceRepository.updateDeviceServices(device.address, it.map { it.toString() })
                    }
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val targetDevice = TargetDevice(
                macAddress = device.address,
                name = device.name,
                rssi = result.rssi,
                protocol = Protocol.BLE
            )
            deviceRepository.addDevice(targetDevice)
        }
    }

    fun startClassicDiscovery() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothDevice.ACTION_UUID)
            context.registerReceiver(classicDiscoveryReceiver, filter)
            isReceiverRegistered = true
        }
        bluetoothAdapter.startDiscovery()
    }

    fun stopClassicDiscovery() {
        if (isReceiverRegistered) {
            context.unregisterReceiver(classicDiscoveryReceiver)
            isReceiverRegistered = false
        }
        bluetoothAdapter.cancelDiscovery()
    }

    fun discoverServices(device: BluetoothDevice) {
        if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            device.fetchUuidsWithSdp()
        }
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun startBleScan() {
        bleScanner.startScan(bleScanCallback)
    }

    fun stopBleScan() {
        bleScanner.stopScan(bleScanCallback)
    }

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: android.bluetooth.BluetoothGatt, status: Int, newState: Int) {
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: android.bluetooth.BluetoothGatt, status: Int) {
            if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services.map { it.uuid.toString() }
                deviceRepository.updateDeviceServices(gatt.device.address, services)
            }
            gatt.close()
        }
    }
}
