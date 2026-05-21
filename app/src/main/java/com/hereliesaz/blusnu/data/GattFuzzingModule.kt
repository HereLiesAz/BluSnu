package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
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
    }

    private val _log = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val log: SharedFlow<String> = _log

    private var gatt: BluetoothGatt? = null
    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    private var discoveryDeferred: CompletableDeferred<Boolean>? = null
    private var writeDeferred: CompletableDeferred<Boolean>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _log.tryEmit("  Read ${characteristic.uuid}: ${value.toHexString()}")
            }
        }
    }

    suspend fun executeAttack(context: Context, device: BluetoothDevice): String {
        val results = StringBuilder()

        try {
            // Connect
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

            // Discover services
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

            // Fuzz each service
            for (service in services) {
                results.appendLine("\nService: ${service.uuid}")
                _log.tryEmit("Service: ${formatUuid(service.uuid)}")

                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val propsStr = formatProperties(props)
                    _log.tryEmit("  Characteristic: ${formatUuid(characteristic.uuid)} [$propsStr]")
                    results.appendLine("  Char: ${characteristic.uuid} [$propsStr]")

                    // Read if readable
                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        try {
                            gatt?.readCharacteristic(characteristic)
                            delay(WRITE_DELAY_MS)
                        } catch (e: Exception) {
                            _log.tryEmit("  Read failed: ${e.message}")
                        }
                    }

                    // Fuzz if writable
                    if (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        val fuzzPayloads = generateFuzzPayloads()
                        for ((name, payload) in fuzzPayloads) {
                            try {
                                writeDeferred = CompletableDeferred()
                                characteristic.value = payload
                                characteristic.writeType =
                                    if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                    else
                                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                                val wrote = gatt?.writeCharacteristic(characteristic) ?: false
                                if (wrote) {
                                    val success = try {
                                        withTimeout(2000) { writeDeferred!!.await() }
                                    } catch (e: Exception) {
                                        false
                                    }
                                    val status = if (success) "OK" else "REJECTED"
                                    _log.tryEmit("  Fuzz [$name]: $status")
                                    results.appendLine("    Fuzz [$name]: $status")
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

    // Kept for backward compatibility with existing ViewModel signature
    fun executeAttack(device: BluetoothDevice) {
        // No-op without context; use the suspend variant with context instead
    }

    private fun generateFuzzPayloads(): List<Pair<String, ByteArray>> = listOf(
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

    private fun formatUuid(uuid: UUID): String {
        val s = uuid.toString()
        // Show short form for standard BT UUIDs
        if (s.endsWith("-0000-1000-8000-00805f9b34fb")) {
            return "0x${s.substring(4, 8)}"
        }
        return s
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
