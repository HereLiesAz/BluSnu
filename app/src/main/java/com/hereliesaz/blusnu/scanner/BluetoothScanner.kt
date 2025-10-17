package com.hereliesaz.blusnu.scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice

class BluetoothScanner(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val bluetoothAdapter: BluetoothAdapter?
) {

    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    private var isScanning = false

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    deviceRepository.addDevice(
                        TargetDevice(
                            macAddress = it.address,
                            name = it.name,
                            rssi = result.rssi,
                            protocol = Protocol.BLE
                        )
                    )
                }
            }
        }
    }

    private val classicDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        deviceRepository.addDevice(
                            TargetDevice(
                                macAddress = it.address,
                                name = it.name,
                                rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                                protocol = Protocol.CLASSIC
                            )
                        )
                    }
                }
            }
        }
    }

    fun startScan() {
        if (isScanning) return
        isScanning = true

        // Start BLE Scan
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bleScanner?.startScan(bleScanCallback)
        }


        // Start Classic Discovery
        context.registerReceiver(classicDiscoveryReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        bluetoothAdapter?.startDiscovery()
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false

        // Stop BLE Scan
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bleScanner?.stopScan(bleScanCallback)
        }

        // Stop Classic Discovery
        bluetoothAdapter?.cancelDiscovery()
        context.unregisterReceiver(classicDiscoveryReceiver)
    }
}