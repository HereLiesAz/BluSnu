package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.random.Random

@SuppressLint("MissingPermission")
class GattFuzzingModule {

    companion object {
        private const val TAG = "GattFuzzing"
        private const val CONNECTION_TIMEOUT_MS = 10_000L
        private const val DISCOVERY_TIMEOUT_MS = 15_000L
        private const val WRITE_DELAY_MS = 200L
        private const val READ_TIMEOUT_MS = 3_000L
        private const val WRITE_RETRY_DELAY_MS = 500L
        private const val MAX_WRITE_RETRIES = 3
        private const val BT_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"
    }

    private val _log = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val log: SharedFlow<String> = _log

    private var gatt: BluetoothGatt? = null

    // Finding 11.4: Mark CompletableDeferred fields as @Volatile for cross-thread visibility
    @Volatile
    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var discoveryDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var writeDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var readDeferred: CompletableDeferred<ByteArray?>? = null

    private val gattCallback = object : BluetoothGattCallback() {

        // Finding 11.7: Check status parameter in onConnectionStateChange
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _log.tryEmit("Connection state change failed with status $status")
                connectionDeferred?.complete(false)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _log.tryEmit("Connected to GATT server")
                    connectionDeferred?.complete(true)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _log.tryEmit("Disconnected from GATT server")
                    connectionDeferred?.complete(false)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _log.tryEmit("Services discovered: ${gatt.services.size} services found")
                discoveryDeferred?.complete(true)
            } else {
                _log.tryEmit("Service discovery failed with status $status")
                discoveryDeferred?.complete(false)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Finding 11.1: API 33+ onCharacteristicRead signature
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _log.tryEmit("  Read ${characteristic.uuid}: ${value.toHexString()}")
                readDeferred?.complete(value)
            } else {
                _log.tryEmit("  Read failed with status $status for ${characteristic.uuid}")
                readDeferred?.complete(null)
            }
        }

        // Finding 11.1: Deprecated onCharacteristicRead for API 26-32
        @Deprecated("Required for API 26-32 compatibility")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                _log.tryEmit("  Read ${characteristic.uuid}: ${value?.toHexString()}")
                readDeferred?.complete(value)
            } else {
                _log.tryEmit("  Read failed with status $status for ${characteristic.uuid}")
                readDeferred?.complete(null)
            }
        }
    }

    suspend fun executeAttack(context: Context, device: BluetoothDevice): String {
        val results = StringBuilder()

        try {
            _log.tryEmit("Connecting to ${device.address}...")
            connectionDeferred = CompletableDeferred()
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

            val connected = try {
                withTimeout(CONNECTION_TIMEOUT_MS) { connectionDeferred!!.await() }
            } catch (e: Exception) {
                false
            }

            if (!connected) {
                val msg = "Failed to connect to GATT server"
                _log.tryEmit(msg)
                return msg
            }

            _log.tryEmit("Discovering services...")
            discoveryDeferred = CompletableDeferred()
            gatt?.discoverServices()

            val discovered = try {
                withTimeout(DISCOVERY_TIMEOUT_MS) { discoveryDeferred!!.await() }
            } catch (e: Exception) {
                false
            }

            if (!discovered) {
                val msg = "Service discovery failed"
                _log.tryEmit(msg)
                return msg
            }

            val services = gatt?.services ?: emptyList()
            results.appendLine("Found ${services.size} services")
            _log.tryEmit("Starting fuzzing on ${services.size} services...")

            for (service in services) {
                results.appendLine("\nService: ${service.uuid}")
                _log.tryEmit("Service: ${formatUuid(service.uuid)}")

                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val propsStr = formatProperties(props)
                    _log.tryEmit("  Characteristic: ${formatUuid(characteristic.uuid)} [$propsStr]")
                    results.appendLine("  Char: ${characteristic.uuid} [$propsStr]")

                    // Finding 11.6: Await read via CompletableDeferred + withTimeout
                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        try {
                            readDeferred = CompletableDeferred()
                            gatt?.readCharacteristic(characteristic)
                            val readValue = withTimeout(READ_TIMEOUT_MS) { readDeferred!!.await() }
                            if (readValue != null) {
                                results.appendLine("    Read: ${readValue.toHexString()}")
                            } else {
                                _log.tryEmit("  Read returned null for ${characteristic.uuid}")
                            }
                        } catch (e: Exception) {
                            _log.tryEmit("  Read failed: ${e.message}")
                        }
                    }

                    if (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        val fuzzPayloads = generateFuzzPayloads()
                        for ((name, payload) in fuzzPayloads) {
                            try {
                                writeDeferred = CompletableDeferred()

                                // Finding 11.2: Prefer WRITE_TYPE_DEFAULT when both are supported
                                val writeType = selectWriteType(props)

                                // Finding 11.3: Check writeCharacteristic return and retry on false
                                var wrote = false
                                for (attempt in 1..MAX_WRITE_RETRIES) {
                                    wrote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        gatt?.writeCharacteristic(characteristic, payload, writeType) ==
                                            BluetoothStatusCodes.SUCCESS
                                    } else {
                                        @Suppress("DEPRECATION")
                                        characteristic.value = payload
                                        characteristic.writeType = writeType
                                        @Suppress("DEPRECATION")
                                        gatt?.writeCharacteristic(characteristic) ?: false
                                    }
                                    if (wrote) break
                                    _log.tryEmit("  Write rejected by stack (queue full), retry $attempt/$MAX_WRITE_RETRIES")
                                    delay(WRITE_RETRY_DELAY_MS)
                                    writeDeferred = CompletableDeferred()
                                }

                                if (wrote) {
                                    val success = try {
                                        withTimeout(2000) { writeDeferred!!.await() }
                                    } catch (e: Exception) {
                                        false
                                    }
                                    val status = if (success) "OK" else "REJECTED"
                                    _log.tryEmit("  Fuzz [$name]: $status")
                                    results.appendLine("    Fuzz [$name]: $status")
                                } else {
                                    _log.tryEmit("  Fuzz [$name]: STACK_REJECTED")
                                    results.appendLine("    Fuzz [$name]: STACK_REJECTED")
                                }
                                delay(WRITE_DELAY_MS)
                            } catch (e: Exception) {
                                _log.tryEmit("  Fuzz [$name]: ERROR ${e.message}")
                                results.appendLine("    Fuzz [$name]: ERROR ${e.message}")
                            }
                        }
                    }
                }
            }

            _log.tryEmit("Fuzzing complete")
            results.appendLine("\nFuzzing complete")

        } catch (e: Exception) {
            Log.e(TAG, "GATT fuzzing error", e)
            val msg = "Error: ${e.message}"
            _log.tryEmit(msg)
            results.appendLine(msg)
        } finally {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }

        return results.toString()
    }

    /**
     * Select the write type for a characteristic based on its supported properties.
     * Prefers WRITE_TYPE_DEFAULT (write with response) for reliable fuzz verdicts.
     * Falls back to WRITE_TYPE_NO_RESPONSE only when it is the sole write property.
     */
    internal fun selectWriteType(props: Int): Int {
        val supportsWrite = props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val supportsWriteNoResponse = props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        return when {
            supportsWrite -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            supportsWriteNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
    }

    internal fun generateFuzzPayloads(): List<Pair<String, ByteArray>> = listOf(
        "empty" to byteArrayOf(),
        "zero-1" to byteArrayOf(0x00),
        "zero-20" to ByteArray(20) { 0x00 },
        "ff-1" to byteArrayOf(0xFF.toByte()),
        "ff-20" to ByteArray(20) { 0xFF.toByte() },
        "max-len" to ByteArray(512) { 0x41 },
        "overflow" to ByteArray(600) { 0x42 },
        "random-8" to Random.nextBytes(8),
        "random-20" to Random.nextBytes(20),
        "int-zero" to byteArrayOf(0x00, 0x00, 0x00, 0x00),
        "int-max" to byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F),
        "int-neg" to byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        "utf8-invalid" to byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0xC0.toByte(), 0x80.toByte()),
    )

    /**
     * Format a UUID for display. Standard Bluetooth Base UUIDs are shortened:
     * - 16-bit UUIDs (0000xxxx-0000-1000-8000-00805f9b34fb) display as "0xXXXX"
     * - 32-bit UUIDs (xxxxxxxx-0000-1000-8000-00805f9b34fb) display as "0xXXXXXXXX"
     * Non-standard UUIDs are shown in full.
     */
    internal fun formatUuid(uuid: UUID): String {
        val s = uuid.toString()
        if (!s.endsWith(BT_UUID_SUFFIX)) {
            return s
        }
        // Finding 11.5: Handle both 16-bit and 32-bit UUIDs correctly
        val prefix = s.substring(0, 8)
        return if (prefix.startsWith("0000")) {
            // 16-bit UUID: upper 16 bits are zero, extract chars 4..8
            "0x${s.substring(4, 8).uppercase()}"
        } else {
            // 32-bit UUID: extract all 8 hex chars before the first dash
            "0x${prefix.uppercase()}"
        }
    }

    private fun formatProperties(props: Int): String {
        val parts = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) parts.add("R")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) parts.add("W")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) parts.add("WNR")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) parts.add("N")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) parts.add("I")
        return parts.joinToString(",")
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it) }
}
