package com.hereliesaz.blusnu.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import com.hereliesaz.blusnu.data.BluetoothScanner
import com.hereliesaz.blusnu.data.DeviceRepository

class TargetManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val deviceRepository = DeviceRepository()
    private val bluetoothScanner =
        bluetoothAdapter?.let { BluetoothScanner(application, deviceRepository, it) }

    val discoveredDevices = deviceRepository.discoveredDevices

    var hasPermissions = false
    var isBluetoothEnabled = false

    fun startScan() {
        if (hasPermissions && isBluetoothEnabled) {
            bluetoothScanner?.startClassicDiscovery()
            bluetoothScanner?.startBleScan()
        }
    }

    fun stopScan() {
        bluetoothScanner?.stopClassicDiscovery()
        bluetoothScanner?.stopBleScan()
    }
}
