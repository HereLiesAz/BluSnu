package com.hereliesaz.blusnu.data

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

enum class HardwareState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_BTLEJACK,
    CONNECTED_DUAL,
    CONNECTION_FAILED
}

class HardwareManager(private val context: Context) {

    companion object {
        private const val TAG = "HardwareManager"
        private const val BAUD_RATE = 115200
        private const val READ_TIMEOUT_MS = 1000
        private const val READ_BUFFER_SIZE = 4096
    }

    private val _hardwareState = MutableStateFlow(HardwareState.DISCONNECTED)
    val hardwareState = _hardwareState.asStateFlow()

    // 3.9: Increase buffer capacity so tryEmit drops only under sustained pressure
    private val _deviceLogs = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val deviceLogs = _deviceLogs.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 3.6: @Volatile ensures cross-thread visibility of port references
    @Volatile
    private var primaryPort: UsbSerialPort? = null
    @Volatile
    private var secondaryPort: UsbSerialPort? = null
    private var readJob: Job? = null

    // 3.3 / 3.6: Mutex guards connect, disconnect, and sendCommand to prevent
    // re-entrancy races on the port fields
    private val portMutex = Mutex()

    fun connect() {
        scope.launch {
            portMutex.withLock {
                // 3.3: If already connected, disconnect first to avoid leaking the old port
                if (primaryPort != null) {
                    disconnectLocked()
                }

                _hardwareState.value = HardwareState.CONNECTING
                log("Scanning for USB serial devices...")

                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

                if (availableDrivers.isEmpty()) {
                    _hardwareState.value = HardwareState.CONNECTION_FAILED
                    log("No USB serial devices found. Connect BtleJack via USB-OTG.")
                    return@withLock
                }

                val driver = availableDrivers[0]
                val connection = usbManager.openDevice(driver.device)
                if (connection == null) {
                    _hardwareState.value = HardwareState.CONNECTION_FAILED
                    log("USB permission denied. Grant USB access and retry.")
                    return@withLock
                }

                val port = driver.ports[0]
                try {
                    port.open(connection)
                    // 3.2: If setParameters throws, close the already-opened port
                    try {
                        port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    } catch (e: Exception) {
                        try { port.close() } catch (_: IOException) {}
                        throw e
                    }
                    primaryPort = port

                    _hardwareState.value = HardwareState.CONNECTED_BTLEJACK

                    // Query firmware version
                    sendCommandInternal(port, "version")
                    val versionResponse = readResponse(port)
                    log("Connected to BtleJack device.")
                    if (versionResponse.isNotBlank()) {
                        log("Firmware: $versionResponse")
                    }

                    startReadLoop(port)
                } catch (e: IOException) {
                    // 3.2: Close on any failure after open()
                    try { port.close() } catch (_: IOException) {}
                    primaryPort = null
                    _hardwareState.value = HardwareState.CONNECTION_FAILED
                    log("Connection failed: ${e.message}")
                }
            }
        }
    }

    fun connectDual() {
        scope.launch {
            portMutex.withLock {
                if (_hardwareState.value != HardwareState.CONNECTED_BTLEJACK) {
                    log("Primary device must be connected first.")
                    return@withLock
                }

                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

                if (availableDrivers.size < 2) {
                    log("Second USB device not found. Connect a secondary BLE dongle.")
                    return@withLock
                }

                val driver = availableDrivers[1]
                val connection = usbManager.openDevice(driver.device)
                if (connection == null) {
                    log("USB permission denied for secondary device.")
                    return@withLock
                }

                val port = driver.ports[0]
                try {
                    port.open(connection)
                    // 3.2: Close port on setParameters failure
                    try {
                        port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    } catch (e: Exception) {
                        try { port.close() } catch (_: IOException) {}
                        throw e
                    }
                    secondaryPort = port

                    _hardwareState.value = HardwareState.CONNECTED_DUAL
                    log("Secondary USB dongle connected.")
                } catch (e: IOException) {
                    try { port.close() } catch (_: IOException) {}
                    log("Secondary connection failed: ${e.message}")
                }
            }
        }
    }

    // 3.5: disconnect() delegates to scope.launch on Dispatchers.IO so USB close
    // never blocks the caller's (potentially main) thread.
    // 3.6: Acquires portMutex to prevent races with sendCommand().
    fun disconnect() {
        scope.launch {
            portMutex.withLock {
                disconnectLocked()
            }
        }
    }

    /**
     * Internal disconnect that must be called while holding [portMutex].
     * Closes USB ports (already on Dispatchers.IO via scope) and resets state.
     */
    private fun disconnectLocked() {
        readJob?.cancel()
        readJob = null
        try { primaryPort?.close() } catch (_: IOException) {}
        try { secondaryPort?.close() } catch (_: IOException) {}
        primaryPort = null
        secondaryPort = null
        _hardwareState.value = HardwareState.DISCONNECTED
    }

    fun close() {
        // Best-effort synchronous cleanup before cancelling the scope
        readJob?.cancel()
        readJob = null
        try { primaryPort?.close() } catch (_: IOException) {}
        try { secondaryPort?.close() } catch (_: IOException) {}
        primaryPort = null
        secondaryPort = null
        _hardwareState.value = HardwareState.DISCONNECTED
        scope.cancel()
    }

    // 3.6: sendCommand acquires portMutex to prevent racing with disconnect()
    fun sendCommand(command: String) {
        scope.launch {
            portMutex.withLock {
                val port = primaryPort
                if (port == null) {
                    log("No device connected. Cannot send: $command")
                    return@withLock
                }
                log("CMD > $command")
                try {
                    sendCommandInternal(port, command)
                } catch (e: IOException) {
                    log("Write error: ${e.message}")
                }
            }
        }
    }

    // 3.11: Made suspend; blocking serial I/O wrapped in withContext(Dispatchers.IO)
    suspend fun getSecondaryRssi(macAddress: String): Int? {
        val port = secondaryPort ?: return null
        return withContext(Dispatchers.IO) {
            try {
                sendCommandInternal(port, "rssi $macAddress")
                val response = readResponse(port)
                response.trim().toIntOrNull()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read secondary RSSI", e)
                null
            }
        }
    }

    private fun sendCommandInternal(port: UsbSerialPort, command: String) {
        val data = "$command\r\n".toByteArray()
        port.write(data, READ_TIMEOUT_MS)
    }

    private fun readResponse(port: UsbSerialPort): String {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val bytesRead = port.read(buffer, READ_TIMEOUT_MS)
        return if (bytesRead > 0) String(buffer, 0, bytesRead).trim() else ""
    }

    private fun startReadLoop(port: UsbSerialPort) {
        readJob = scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            while (isActive) {
                try {
                    val bytesRead = port.read(buffer, READ_TIMEOUT_MS)
                    if (bytesRead > 0) {
                        val line = String(buffer, 0, bytesRead).trim()
                        if (line.isNotEmpty()) {
                            log("< $line")
                        }
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        log("Read error: ${e.message}")
                        _hardwareState.value = HardwareState.CONNECTION_FAILED
                        break
                    }
                }
            }
        }
    }

    // 3.9: Use tryEmit (non-suspending, drops if buffer full) instead of
    // scope.launch { emit() } which spawned an unbounded number of coroutines
    private fun log(message: String) {
        _deviceLogs.tryEmit(message)
        Log.d(TAG, message)
    }
}
