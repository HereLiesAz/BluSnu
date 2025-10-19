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
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(classicDiscoveryReceiver, filter)
        bluetoothAdapter.startDiscovery()
    }

    fun stopClassicDiscovery() {
        context.unregisterReceiver(classicDiscoveryReceiver)
        bluetoothAdapter.cancelDiscovery()
    }

    fun startBleScan() {
        bleScanner.startScan(bleScanCallback)
    }

    fun stopBleScan() {
        bleScanner.stopScan(bleScanCallback)
    }
}
