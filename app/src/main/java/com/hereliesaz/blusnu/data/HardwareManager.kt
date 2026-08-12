package com.hereliesaz.blusnu.data

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val _deviceLogs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val deviceLogs = _deviceLogs.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var primaryPort: UsbSerialPort? = null
    private var secondaryPort: UsbSerialPort? = null
    private var readJob: Job? = null

    fun connect() {
        scope.launch {
            _hardwareState.value = HardwareState.CONNECTING
            log("Scanning for USB serial devices...")

            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                _hardwareState.value = HardwareState.CONNECTION_FAILED
                log("No USB serial devices found. Connect BtleJack via USB-OTG.")
                return@launch
            }

            val driver = availableDrivers[0]
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                _hardwareState.value = HardwareState.CONNECTION_FAILED
                log("USB permission denied. Grant USB access and retry.")
                return@launch
            }

            try {
                val port = driver.ports[0]
                port.open(connection)
                port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
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
                _hardwareState.value = HardwareState.CONNECTION_FAILED
                log("Connection failed: ${e.message}")
            }
        }
    }

    fun connectDual() {
        scope.launch {
            if (_hardwareState.value != HardwareState.CONNECTED_BTLEJACK) {
                log("Primary device must be connected first.")
                return@launch
            }

            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.size < 2) {
                log("Second USB device not found. Connect a secondary BLE dongle.")
                return@launch
            }

            val driver = availableDrivers[1]
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                log("USB permission denied for secondary device.")
                return@launch
            }

            try {
                val port = driver.ports[0]
                port.open(connection)
                port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                secondaryPort = port

                _hardwareState.value = HardwareState.CONNECTED_DUAL
                log("Secondary USB dongle connected.")
            } catch (e: IOException) {
                log("Secondary connection failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try { primaryPort?.close() } catch (_: IOException) {}
        try { secondaryPort?.close() } catch (_: IOException) {}
        primaryPort = null
        secondaryPort = null
        _hardwareState.value = HardwareState.DISCONNECTED
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    fun sendCommand(command: String) {
        val port = primaryPort
        if (port == null) {
            log("No device connected. Cannot send: $command")
            return
        }
        scope.launch {
            log("CMD > $command")
            try {
                sendCommandInternal(port, command)
            } catch (e: IOException) {
                log("Write error: ${e.message}")
            }
        }
    }

    fun getSecondaryRssi(macAddress: String): Int? {
        val port = secondaryPort ?: return null
        return try {
            sendCommandInternal(port, "rssi $macAddress")
            val response = readResponse(port)
            response.trim().toIntOrNull()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read secondary RSSI", e)
            null
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

    private fun log(message: String) {
        scope.launch {
            _deviceLogs.emit(message)
            Log.d(TAG, message)
        }
    }
}
