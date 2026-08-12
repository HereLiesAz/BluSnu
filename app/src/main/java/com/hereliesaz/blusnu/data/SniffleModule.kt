package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Data class representing a single BLE packet captured by the Sniffle dongle.
 *
 * @property timestamp  Unix timestamp in milliseconds when the packet was received.
 * @property channel    BLE channel number (0-39) the packet was captured on.
 * @property accessAddress  The 32-bit access address as a hex string (e.g., "8E89BED6").
 * @property pduType    The PDU type string (e.g., "ADV_IND", "CONNECT_IND", "DATA").
 * @property rssi       Received signal strength in dBm.
 * @property rawHex     The raw packet bytes as a hex string.
 */
data class SniffedPacket(
    val timestamp: Long,
    val channel: Int,
    val accessAddress: String,
    val pduType: String,
    val rssi: Int,
    val rawHex: String
)

/**
 * Module for interacting with Sniffle BLE sniffer hardware.
 *
 * Sniffle uses a TI CC26x2 or CC1352 dongle to passively capture BLE packets
 * including advertising PDUs, connection setup, and encrypted traffic. The dongle
 * communicates over USB serial via [HardwareManager].
 *
 * Sniffle commands (sent over serial):
 * - `sniffle -a`       : Sniff advertising on all 3 channels
 * - `sniffle -t MAC`   : Follow a specific target's connections
 * - `sniffle -r`       : Follow first new connection
 * - `sniffle -e`       : Enable encrypted connection following (with key material)
 * - `sniffle -l`       : Sniff a specific channel
 * - `sniffle -c CHAN`  : Set channel
 *
 * @property hardwareManager Interface to the external Sniffle dongle via USB serial.
 */
class SniffleModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "SniffleModule"

        // Regex patterns for parsing Sniffle serial output lines.
        // Example: "TS=123456789 CH=37 AA=8E89BED6 PDU=ADV_IND RSSI=-45 RAW=0201061AFF..."
        private val TIMESTAMP_PATTERN = Regex("""TS=(\d+)""")
        private val CHANNEL_PATTERN = Regex("""CH=(\d+)""")
        private val ACCESS_ADDR_PATTERN = Regex("""AA=([0-9A-Fa-f]+)""")
        private val PDU_TYPE_PATTERN = Regex("""PDU=(\S+)""")
        private val RSSI_PATTERN = Regex("""RSSI=(-?\d+)""")
        private val RAW_HEX_PATTERN = Regex("""RAW=([0-9A-Fa-f]+)""")
    }

    private val _isSniffing = MutableStateFlow(false)
    val isSniffing = _isSniffing.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sniffJob: Job? = null

    /**
     * Sniff all BLE advertisements across all three advertising channels.
     *
     * Sends `sniffle -a` to the dongle and returns a [Flow] of parsed log lines
     * from the device output.
     *
     * @return A [Flow] emitting raw log strings from the Sniffle hardware.
     */
    fun startAdvertisingScan(): Flow<String> {
        stopSniffing()
        _isSniffing.value = true
        hardwareManager.sendCommand("sniffle -a")
        Log.d(TAG, "Started advertising scan on all channels.")

        return hardwareManager.deviceLogs
            .onEach { line ->
                if (line.contains("error", ignoreCase = true) ||
                    line.contains("timeout", ignoreCase = true)
                ) {
                    Log.w(TAG, "Sniffle warning: $line")
                }
            }
            .filter { it.contains("CH=") || it.contains("ADV") || it.contains("AA=") }
    }

    /**
     * Follow a specific device's connections by MAC address.
     *
     * Sends `sniffle -t MAC` to the dongle to track the target device.
     *
     * @param targetMac The target device's MAC address in colon-separated format
     *                  (e.g., "AA:BB:CC:DD:EE:FF").
     * @return A [Flow] emitting raw log strings from the Sniffle hardware.
     * @throws IllegalArgumentException if [targetMac] is not a valid MAC address.
     */
    fun startConnectionFollow(targetMac: String): Flow<String> {
        MacValidator.requireValid(targetMac)

        stopSniffing()
        _isSniffing.value = true
        hardwareManager.sendCommand("sniffle -t $targetMac")
        Log.d(TAG, "Started connection follow for target: $targetMac")

        return hardwareManager.deviceLogs
            .onEach { line ->
                if (line.contains("error", ignoreCase = true)) {
                    Log.w(TAG, "Sniffle warning: $line")
                }
            }
            .filter { it.contains("CH=") || it.contains("CONNECT") || it.contains("AA=") }
    }

    /**
     * Passively follow the first new BLE connection observed.
     *
     * Sends `sniffle -r` to the dongle. The hardware will lock onto the
     * first connection initiation it captures.
     *
     * @return A [Flow] emitting raw log strings from the Sniffle hardware.
     */
    fun startPassiveSniff(): Flow<String> {
        stopSniffing()
        _isSniffing.value = true
        hardwareManager.sendCommand("sniffle -r")
        Log.d(TAG, "Started passive sniff - following first new connection.")

        return hardwareManager.deviceLogs
            .onEach { line ->
                if (line.contains("error", ignoreCase = true)) {
                    Log.w(TAG, "Sniffle warning: $line")
                }
            }
            .filter { it.contains("CH=") || it.contains("CONNECT") || it.contains("AA=") || it.contains("DATA") }
    }

    /**
     * Stop any active sniffing operation.
     *
     * Sends a stop command to the Sniffle dongle and cancels the collection job.
     */
    fun stopSniffing() {
        if (_isSniffing.value) {
            sniffJob?.cancel()
            sniffJob = null
            hardwareManager.sendCommand("sniffle -s")
            _isSniffing.value = false
            Log.d(TAG, "Sniffing stopped.")
        }
    }

    /**
     * Check whether the Sniffle dongle hardware is currently connected.
     *
     * @return `true` if the [HardwareManager] reports a connected state.
     */
    fun isHardwareConnected(): Boolean {
        val state = hardwareManager.hardwareState.value
        return state == HardwareState.CONNECTED_BTLEJACK ||
                state == HardwareState.CONNECTED_DUAL
    }

    /**
     * Parse a raw Sniffle output line into a [SniffedPacket].
     *
     * Returns `null` if the line does not contain the expected fields.
     *
     * @param line A single line of serial output from the Sniffle dongle.
     * @return A parsed [SniffedPacket], or `null` if parsing fails.
     */
    fun parsePacket(line: String): SniffedPacket? {
        val timestamp = TIMESTAMP_PATTERN.find(line)?.groupValues?.get(1)?.toLongOrNull()
            ?: System.currentTimeMillis()
        val channel = CHANNEL_PATTERN.find(line)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val accessAddress = ACCESS_ADDR_PATTERN.find(line)?.groupValues?.get(1)
            ?: return null
        val pduType = PDU_TYPE_PATTERN.find(line)?.groupValues?.get(1)
            ?: "UNKNOWN"
        val rssi = RSSI_PATTERN.find(line)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0
        val rawHex = RAW_HEX_PATTERN.find(line)?.groupValues?.get(1)
            ?: ""

        return SniffedPacket(
            timestamp = timestamp,
            channel = channel,
            accessAddress = accessAddress,
            pduType = pduType,
            rssi = rssi,
            rawHex = rawHex
        )
    }

    /**
     * Returns a [Flow] that maps raw device log lines to parsed [SniffedPacket]s.
     *
     * Lines that cannot be parsed are silently dropped.
     */
    fun packetFlow(): Flow<SniffedPacket> {
        return hardwareManager.deviceLogs
            .filter { it.contains("AA=") }
            .map { parsePacket(it) }
            .filter { it != null }
            .map { it!! }
    }

    /**
     * Releases all resources: stops sniffing and cancels the coroutine scope.
     */
    fun close() {
        stopSniffing()
        scope.cancel()
    }
}
