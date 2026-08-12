package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@SuppressLint("MissingPermission")
class FileTransferController(private val context: Context) {

    companion object {
        private const val TAG = "FileTransferController"
        private const val SERVER_NAME = "BluSnu File Transfer"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress

    private val _transferStatus = MutableStateFlow(TransferStatus.IDLE)
    val transferStatus: StateFlow<TransferStatus> = _transferStatus

    private val _currentTransfer = MutableStateFlow<TransferInfo?>(null)
    val currentTransfer: StateFlow<TransferInfo?> = _currentTransfer

    private val _transferHistory = MutableStateFlow<List<TransferInfo>>(emptyList())
    val transferHistory: StateFlow<List<TransferInfo>> = _transferHistory

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val statusMessage: SharedFlow<String> = _statusMessage

    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var serverJob: Job? = null
    private var transferJob: Job? = null

    // --- Send ---

    fun sendFile(device: BluetoothDevice, fileUri: Uri) {
        if (transferJob?.isActive == true) {
            _statusMessage.tryEmit("Transfer already in progress.")
            return
        }

        transferJob = scope.launch {
            try {
                val contentResolver = context.contentResolver
                val fileName = getFileName(contentResolver, fileUri) ?: "unknown_file"
                val fileSize = getFileSize(contentResolver, fileUri)

                _currentTransfer.value = TransferInfo(fileName, fileSize, TransferDirection.SEND, TransferStatus.TRANSFERRING)
                _transferStatus.value = TransferStatus.TRANSFERRING
                _transferProgress.value = 0f
                _statusMessage.emit("Connecting to ${device.name ?: device.address}...")

                val socket = device.createRfcommSocketToServiceRecord(FileTransferProtocol.SERVICE_UUID)
                activeSocket = socket
                socket.connect()

                _statusMessage.emit("Connected. Sending $fileName (${formatSize(fileSize)})...")

                val outStream = socket.outputStream
                val inStream = socket.inputStream

                // Send file start header
                val header = FileTransferProtocol.buildFileStartHeader(fileName, fileSize)
                outStream.write(header)
                outStream.flush()

                // Send file data in chunks
                val inputStream = contentResolver.openInputStream(fileUri)
                    ?: throw IOException("Cannot open file")

                inputStream.use { fileIn ->
                    val buffer = ByteArray(FileTransferProtocol.CHUNK_SIZE)
                    var totalSent = 0L

                    while (isActive) {
                        val bytesRead = fileIn.read(buffer)
                        if (bytesRead == -1) break

                        outStream.write(byteArrayOf(FileTransferProtocol.MSG_FILE_DATA))
                        // 4-byte big-endian length prefix
                        outStream.write(byteArrayOf(
                            ((bytesRead shr 24) and 0xFF).toByte(),
                            ((bytesRead shr 16) and 0xFF).toByte(),
                            ((bytesRead shr 8) and 0xFF).toByte(),
                            (bytesRead and 0xFF).toByte()
                        ))
                        outStream.write(buffer, 0, bytesRead)
                        outStream.flush()
                        totalSent += bytesRead
                        _transferProgress.value = if (fileSize > 0) totalSent.toFloat() / fileSize else 0f
                    }
                }

                // Send end marker
                outStream.write(byteArrayOf(FileTransferProtocol.MSG_FILE_END))
                outStream.flush()

                // Wait for ACK
                val ack = inStream.read()
                if (ack == FileTransferProtocol.MSG_ACK.toInt()) {
                    _statusMessage.emit("File sent successfully: $fileName")
                    _transferStatus.value = TransferStatus.COMPLETED
                } else {
                    _statusMessage.emit("Transfer failed: no ACK received.")
                    _transferStatus.value = TransferStatus.FAILED
                }

                addToHistory(TransferInfo(fileName, fileSize, TransferDirection.SEND, _transferStatus.value, 1f))
                socket.close()

            } catch (e: CancellationException) {
                _transferStatus.value = TransferStatus.CANCELLED
                _statusMessage.tryEmit("Transfer cancelled.")
            } catch (e: Exception) {
                Log.e(TAG, "Send failed", e)
                _transferStatus.value = TransferStatus.FAILED
                _statusMessage.tryEmit("Send failed: ${e.message}")
            } finally {
                activeSocket = null
                _currentTransfer.value = null
            }
        }
    }

    // --- Receive Server ---

    fun startServer() {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            try {
                val adapter = bluetoothAdapter ?: run {
                    _statusMessage.emit("Bluetooth not available.")
                    return@launch
                }

                serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                    SERVER_NAME,
                    FileTransferProtocol.SERVICE_UUID
                )
                _serverRunning.value = true
                _statusMessage.emit("File transfer server started. Waiting for connections...")

                while (isActive) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        if (isActive) Log.e(TAG, "Server accept failed", e)
                        null
                    }

                    if (socket != null) {
                        _statusMessage.emit("Incoming connection from ${socket.remoteDevice?.name ?: socket.remoteDevice?.address}")
                        handleIncomingTransfer(socket)
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                _statusMessage.tryEmit("Server error: ${e.message}")
            } finally {
                _serverRunning.value = false
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {}
        serverSocket = null
        _serverRunning.value = false
        _statusMessage.tryEmit("File transfer server stopped.")
    }

    private suspend fun handleIncomingTransfer(socket: BluetoothSocket) {
        try {
            val inStream = socket.inputStream
            val outStream = socket.outputStream

            // Read header
            val headerBuffer = ByteArray(1024)
            val headerLen = inStream.read(headerBuffer)
            if (headerLen <= 0) {
                socket.close()
                return
            }

            val headerData = headerBuffer.copyOf(headerLen)
            val parsed = FileTransferProtocol.parseFileStartHeader(headerData) ?: run {
                _statusMessage.emit("Invalid file transfer header.")
                socket.close()
                return
            }

            val (fileName, fileSize) = parsed
            _currentTransfer.value = TransferInfo(fileName, fileSize, TransferDirection.RECEIVE, TransferStatus.TRANSFERRING)
            _transferStatus.value = TransferStatus.TRANSFERRING
            _transferProgress.value = 0f
            _statusMessage.emit("Receiving $fileName (${formatSize(fileSize)})...")

            // Save to Downloads -- sanitize filename to prevent path traversal
            val downloadsDir = context.getExternalFilesDir(null) ?: context.filesDir
            val sanitizedName = File(fileName).name
            val outFile = File(downloadsDir, sanitizedName)
            if (!outFile.canonicalPath.startsWith(downloadsDir.canonicalPath)) {
                _statusMessage.emit("Rejected file: path traversal detected in filename.")
                _transferStatus.value = TransferStatus.FAILED
                socket.close()
                return
            }
            var totalReceived = 0L

            outFile.outputStream().use { fileOut ->
                while (true) {
                    val typeByte = inStream.read()
                    if (typeByte == -1) break

                    when (typeByte.toByte()) {
                        FileTransferProtocol.MSG_FILE_DATA -> {
                            // Read 4-byte big-endian length prefix
                            val lengthBytes = readExactly(inStream, 4)
                            val chunkLen = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                                    ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                                    ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                                    (lengthBytes[3].toInt() and 0xFF)
                            val chunkData = readExactly(inStream, chunkLen)
                            fileOut.write(chunkData)
                            totalReceived += chunkLen
                            _transferProgress.value = if (fileSize > 0) totalReceived.toFloat() / fileSize else 0f
                        }
                        FileTransferProtocol.MSG_FILE_END -> break
                        FileTransferProtocol.MSG_CANCEL -> {
                            _statusMessage.emit("Transfer cancelled by sender.")
                            _transferStatus.value = TransferStatus.CANCELLED
                            outFile.delete()
                            socket.close()
                            return
                        }
                    }
                }
            }

            // Send ACK
            outStream.write(byteArrayOf(FileTransferProtocol.MSG_ACK))
            outStream.flush()

            _transferStatus.value = TransferStatus.COMPLETED
            _transferProgress.value = 1f
            _statusMessage.emit("File received: ${outFile.absolutePath}")
            addToHistory(TransferInfo(fileName, fileSize, TransferDirection.RECEIVE, TransferStatus.COMPLETED, 1f))

            socket.close()

        } catch (e: Exception) {
            Log.e(TAG, "Receive failed", e)
            _transferStatus.value = TransferStatus.FAILED
            _statusMessage.tryEmit("Receive failed: ${e.message}")
        } finally {
            _currentTransfer.value = null
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        try {
            activeSocket?.outputStream?.write(byteArrayOf(FileTransferProtocol.MSG_CANCEL))
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        _transferStatus.value = TransferStatus.CANCELLED
        _transferProgress.value = 0f
        _currentTransfer.value = null
        _statusMessage.tryEmit("Transfer cancelled.")
    }

    fun cleanup() {
        stopServer()
        cancelTransfer()
        scope.cancel()
    }

    private fun addToHistory(info: TransferInfo) {
        _transferHistory.value = (_transferHistory.value + info).takeLast(50)
    }

    private fun getFileName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(resolver: ContentResolver, uri: Uri): Long {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) return cursor.getLong(sizeIndex)
            }
        }
        return 0L
    }

    /**
     * Reads exactly [count] bytes from the stream, looping until all bytes
     * are received or the stream ends prematurely.
     */
    private fun readExactly(stream: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val bytesRead = stream.read(buffer, offset, count - offset)
            if (bytesRead == -1) throw IOException("Unexpected end of stream (expected $count bytes, got $offset)")
            offset += bytesRead
        }
        return buffer
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
