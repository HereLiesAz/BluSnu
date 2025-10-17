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

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: android.bluetooth.BluetoothGatt?, status: Int, newState: Int) {
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    gatt?.discoverServices()
                }
            }
        }

        override fun onServicesDiscovered(gatt: android.bluetooth.BluetoothGatt?, status: Int) {
            if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                val services = gatt?.services?.map { it.uuid.toString() } ?: emptyList()
                val device = deviceRepository.discoveredDevices.value.find { it.macAddress == gatt?.device?.address }
                device?.let {
                    deviceRepository.updateDevice(it.copy(services = services))
                }
            }
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                gatt?.close()
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val newDevice = TargetDevice(
                        macAddress = it.address,
                        name = it.name,
                        rssi = result.rssi,
                        protocol = Protocol.BLE
                    )
                    deviceRepository.addDevice(newDevice)
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
                        val newDevice = TargetDevice(
                            macAddress = it.address,
                            name = it.name,
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                            protocol = Protocol.CLASSIC
                        )
                        deviceRepository.addDevice(newDevice)
                        it.fetchUuidsWithSdp()
                    }
                }
            } else if (BluetoothDevice.ACTION_UUID == intent.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                device?.let { foundDevice ->
                    val services = uuidExtra?.map { it.toString() } ?: emptyList()
                    val existingDevice = deviceRepository.discoveredDevices.value.find { it.macAddress == foundDevice.address }
                    existingDevice?.let {
                        deviceRepository.updateDevice(it.copy(services = services))
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

    fun connectToDevice(device: TargetDevice) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.macAddress)
        bluetoothDevice?.connectGatt(context, false, gattCallback)
    }
}