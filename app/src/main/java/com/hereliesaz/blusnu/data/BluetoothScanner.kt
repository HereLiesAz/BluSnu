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

class BluetoothScanner(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val bluetoothAdapter: BluetoothAdapter
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
                            protocol = Protocol.CLASSIC
                        )
                        deviceRepository.addDevice(targetDevice)
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
                protocol = Protocol.BLE
            )
            deviceRepository.addDevice(targetDevice)
        }
    }

    @SuppressLint("MissingPermission")
    fun startClassicDiscovery() {
        if (!isClassicReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
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
    fun startBleScan() {
        bleScanner.startScan(bleScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanner.stopScan(bleScanCallback)
    }
}
