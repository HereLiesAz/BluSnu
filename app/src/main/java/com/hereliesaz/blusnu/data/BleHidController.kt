package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
class BleHidController(private val context: Context) {

    companion object {
        private const val TAG = "BleHidController"

        /** Maximum retries when notifyCharacteristicChanged returns false (queue full). */
        private const val NOTIFY_MAX_RETRIES = 3
        /** Backoff delay in ms when notification queue is full. */
        private const val NOTIFY_BACKOFF_MS = 50L
        /** Delay between key press and release notifications. */
        private const val KEY_PRESS_RELEASE_DELAY_MS = 10L
        /** Delay between consecutive character injections. */
        private const val INTER_CHAR_DELAY_MS = 8L
        /** Timeout for waiting on onServiceAdded callback. */
        private const val SERVICE_ADD_TIMEOUT_MS = 2000L

        // HID Service UUIDs
        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        val HID_INFORMATION_UUID: UUID = UUID.fromString("00002a4a-0000-1000-8000-00805f9b34fb")
        val REPORT_MAP_UUID: UUID = UUID.fromString("00002a4b-0000-1000-8000-00805f9b34fb")
        val HID_CONTROL_POINT_UUID: UUID = UUID.fromString("00002a4c-0000-1000-8000-00805f9b34fb")
        val PROTOCOL_MODE_UUID: UUID = UUID.fromString("00002a4e-0000-1000-8000-00805f9b34fb")
        val REPORT_UUID: UUID = UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb")

        // Descriptors
        val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val REPORT_REFERENCE_UUID: UUID = UUID.fromString("00002908-0000-1000-8000-00805f9b34fb")

        // Battery Service
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        // Device Information Service
        val DEVICE_INFO_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val PNP_ID_UUID: UUID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb")

        // Report types
        private const val REPORT_TYPE_INPUT: Byte = 0x01
        private const val REPORT_TYPE_OUTPUT: Byte = 0x02

        // Report IDs
        private const val KEYBOARD_REPORT_ID: Byte = 0x01
        private const val MOUSE_REPORT_ID: Byte = 0x02
    }

    private val _connectionState = MutableStateFlow(HidConnectionState.DISCONNECTED)
    val connectionState: StateFlow<HidConnectionState> = _connectionState

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val statusMessage: SharedFlow<String> = _statusMessage

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager?.adapter }

    private var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var keyboardInputCharacteristic: BluetoothGattCharacteristic? = null
    private var mouseInputCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile
    private var isAdvertising = false
    @Volatile
    private var isInitialized = false

    // Continuation for sequential addService flow (fix 2.2)
    private var serviceAddContinuation: CancellableContinuation<Boolean>? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _connectionState.value = HidConnectionState.CONNECTED
                    _statusMessage.tryEmit("BLE HID connected to ${device?.name ?: device?.address}")
                    stopAdvertising()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    _connectionState.value = HidConnectionState.REGISTERED
                    _statusMessage.tryEmit("BLE HID disconnected.")
                }
            }
        }

        // Fix 2.1: Handle offset for long reads (e.g., 101-byte HID Report Map at default MTU 23)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            device ?: return
            val value = characteristic?.value ?: ByteArray(0)
            if (offset >= value.size) {
                // Offset beyond data: send empty response to signal end of long read
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
            } else {
                val chunk = value.copyOfRange(offset, value.size)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            device ?: return
            val value = descriptor?.value ?: ByteArray(0)
            if (offset >= value.size) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
            } else {
                val chunk = value.copyOfRange(offset, value.size)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?, requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray?
        ) {
            device ?: return
            if (descriptor?.uuid == CLIENT_CONFIG_UUID) {
                descriptor.value = value
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?, requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray?
        ) {
            device ?: return
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        // Fix 2.2: Callback for sequential addService
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (!success) {
                Log.e(TAG, "Failed to add service ${service?.uuid}: status=$status")
            }
            serviceAddContinuation?.resume(success)
            serviceAddContinuation = null
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            _statusMessage.tryEmit("BLE HID advertising started. Waiting for connection...")
        }

        // Fix 2.5: Update connectionState on advertising failure
        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            _connectionState.value = HidConnectionState.ERROR
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
                else -> "unknown error"
            }
            Log.e(TAG, "BLE advertising failed: $reason (code $errorCode)")
            _statusMessage.tryEmit("BLE advertising failed: $reason (code $errorCode)")
        }
    }

    fun isSupported(): Boolean = bluetoothAdapter?.bluetoothLeAdvertiser != null

    // Fix 2.2: initialize is now suspend to allow sequential service registration
    suspend fun initialize() {
        // Guard against re-initialization
        if (isInitialized) {
            _statusMessage.tryEmit("BLE HID already initialized.")
            return
        }

        if (!isSupported()) {
            _connectionState.value = HidConnectionState.ERROR
            _statusMessage.tryEmit("BLE HID not supported on this device.")
            return
        }

        _connectionState.value = HidConnectionState.REGISTERING
        _statusMessage.tryEmit("Initializing BLE HID GATT server...")

        try {
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            if (gattServer == null) {
                _connectionState.value = HidConnectionState.ERROR
                _statusMessage.tryEmit("Failed to open GATT server.")
                return
            }

            setupServices()
            isInitialized = true
            _connectionState.value = HidConnectionState.REGISTERED
            _statusMessage.tryEmit("BLE HID GATT server ready.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BLE HID", e)
            _connectionState.value = HidConnectionState.ERROR
            _statusMessage.tryEmit("BLE HID init error: ${e.message}")
        }
    }

    // Fix 2.2: Add services sequentially, awaiting onServiceAdded between each
    private suspend fun addServiceAndWait(server: BluetoothGattServer, service: BluetoothGattService) {
        val success = withTimeout(SERVICE_ADD_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                serviceAddContinuation = cont
                if (!server.addService(service)) {
                    serviceAddContinuation = null
                    cont.resume(false)
                }
            }
        }
        if (!success) {
            throw IllegalStateException("Failed to add service ${service.uuid}")
        }
    }

    private suspend fun setupServices() {
        val server = gattServer ?: return

        // Device Information Service
        val deviceInfoService = BluetoothGattService(
            DEVICE_INFO_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val manufacturerName = BluetoothGattCharacteristic(
            MANUFACTURER_NAME_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ).apply { value = "BluSnu".toByteArray() }
        val pnpId = BluetoothGattCharacteristic(
            PNP_ID_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ).apply {
            value = byteArrayOf(
                0x01,                           // Bluetooth SIG vendor ID source
                0x00, 0x00,                     // Vendor ID
                0x01, 0x00,                     // Product ID
                0x01, 0x00                      // Product Version
            )
        }
        deviceInfoService.addCharacteristic(manufacturerName)
        deviceInfoService.addCharacteristic(pnpId)
        // Fix 2.2: Wait for onServiceAdded before adding next service
        addServiceAndWait(server, deviceInfoService)

        // Battery Service
        val batteryService = BluetoothGattService(
            BATTERY_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val batteryLevel = BluetoothGattCharacteristic(
            BATTERY_LEVEL_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ).apply { value = byteArrayOf(100) }
        batteryLevel.addDescriptor(BluetoothGattDescriptor(
            CLIENT_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        ).apply { value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE })
        batteryService.addCharacteristic(batteryLevel)
        addServiceAndWait(server, batteryService)

        // HID Service
        val hidService = BluetoothGattService(
            HID_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // HID Information
        val hidInfo = BluetoothGattCharacteristic(
            HID_INFORMATION_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ).apply {
            value = byteArrayOf(
                0x11, 0x01,  // HID version 1.11
                0x00,        // Country code: not localized
                0x02         // Flags: normally connectable
            )
        }
        hidService.addCharacteristic(hidInfo)

        // Report Map
        val reportMap = BluetoothGattCharacteristic(
            REPORT_MAP_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ).apply { value = HidKeyMap.REPORT_DESCRIPTOR }
        hidService.addCharacteristic(reportMap)

        // Protocol Mode
        val protocolMode = BluetoothGattCharacteristic(
            PROTOCOL_MODE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
        ).apply { value = byteArrayOf(0x01) } // Report Protocol Mode
        hidService.addCharacteristic(protocolMode)

        // HID Control Point
        val controlPoint = BluetoothGattCharacteristic(
            HID_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
        )
        hidService.addCharacteristic(controlPoint)

        // Keyboard Input Report
        keyboardInputCharacteristic = BluetoothGattCharacteristic(
            REPORT_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ).apply { value = ByteArray(8) }
        keyboardInputCharacteristic!!.addDescriptor(BluetoothGattDescriptor(
            CLIENT_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
        ).apply { value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE })
        keyboardInputCharacteristic!!.addDescriptor(BluetoothGattDescriptor(
            REPORT_REFERENCE_UUID,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
        ).apply { value = byteArrayOf(KEYBOARD_REPORT_ID, REPORT_TYPE_INPUT) })
        hidService.addCharacteristic(keyboardInputCharacteristic!!)

        // Mouse Input Report
        mouseInputCharacteristic = BluetoothGattCharacteristic(
            REPORT_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        ).apply { value = ByteArray(4) }
        mouseInputCharacteristic!!.addDescriptor(BluetoothGattDescriptor(
            CLIENT_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
        ).apply { value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE })
        mouseInputCharacteristic!!.addDescriptor(BluetoothGattDescriptor(
            REPORT_REFERENCE_UUID,
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
        ).apply { value = byteArrayOf(MOUSE_REPORT_ID, REPORT_TYPE_INPUT) })
        hidService.addCharacteristic(mouseInputCharacteristic!!)

        addServiceAndWait(server, hidService)
    }

    fun startAdvertising() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(HID_SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeTxPowerLevel(true)
            .build()

        _connectionState.value = HidConnectionState.CONNECTING
        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
    }

    /**
     * Sends a notification with retry logic when the BLE queue is full.
     *
     * @return true if notification was sent successfully, false after exhausting retries.
     */
    private suspend fun notifyWithRetry(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val server = gattServer ?: return false
        for (attempt in 0 until NOTIFY_MAX_RETRIES) {
            @Suppress("DEPRECATION")
            val sent = server.notifyCharacteristicChanged(device, characteristic, false)
            if (sent) return true
            delay(NOTIFY_BACKOFF_MS)
        }
        Log.w(TAG, "Notification failed after $NOTIFY_MAX_RETRIES retries")
        return false
    }

    // Fix 2.3: suspend function with delay between press and release
    suspend fun sendKeyPress(keyCode: Byte, modifiers: Byte = HidKeyMap.MOD_NONE): Boolean {
        val device = connectedDevice ?: return false
        val char = keyboardInputCharacteristic ?: return false
        val report = byteArrayOf(modifiers, 0x00, keyCode, 0, 0, 0, 0, 0)
        char.value = report
        return notifyWithRetry(device, char)
    }

    suspend fun sendKeyRelease(): Boolean {
        val device = connectedDevice ?: return false
        val char = keyboardInputCharacteristic ?: return false
        char.value = ByteArray(8)
        return notifyWithRetry(device, char)
    }

    // Fix 2.3: Add delay between press and release
    suspend fun typeCharacter(ch: Char): Boolean {
        val mapping = HidKeyMap.charToHid(ch) ?: return false
        val pressOk = sendKeyPress(mapping.keyCode, mapping.modifier)
        delay(KEY_PRESS_RELEASE_DELAY_MS)
        val releaseOk = sendKeyRelease()
        return pressOk && releaseOk
    }

    // Fix 2.4: Add inter-character delay to avoid flooding BLE notification queue
    suspend fun typeString(text: String): Int {
        var failures = 0
        for (ch in text) {
            if (!typeCharacter(ch)) {
                failures++
            }
            delay(INTER_CHAR_DELAY_MS)
        }
        return failures
    }

    suspend fun sendMouseMove(dx: Int, dy: Int): Boolean {
        val device = connectedDevice ?: return false
        val char = mouseInputCharacteristic ?: return false
        val clampedX = dx.coerceIn(-127, 127).toByte()
        val clampedY = dy.coerceIn(-127, 127).toByte()
        char.value = byteArrayOf(0x00, clampedX, clampedY, 0x00)
        return notifyWithRetry(device, char)
    }

    suspend fun sendMouseClick(button: Byte = HidKeyMap.MOUSE_BUTTON_LEFT): Boolean {
        val device = connectedDevice ?: return false
        val char = mouseInputCharacteristic ?: return false
        // Press
        char.value = byteArrayOf(button, 0, 0, 0)
        val pressOk = notifyWithRetry(device, char)
        delay(KEY_PRESS_RELEASE_DELAY_MS)
        // Release
        char.value = byteArrayOf(0, 0, 0, 0)
        val releaseOk = notifyWithRetry(device, char)
        return pressOk && releaseOk
    }

    suspend fun sendMouseScroll(delta: Int): Boolean {
        val device = connectedDevice ?: return false
        val char = mouseInputCharacteristic ?: return false
        val clampedDelta = delta.coerceIn(-127, 127).toByte()
        char.value = byteArrayOf(0x00, 0x00, 0x00, clampedDelta)
        return notifyWithRetry(device, char)
    }

    fun cleanup() {
        stopAdvertising()
        connectedDevice?.let { gattServer?.cancelConnection(it) }
        connectedDevice = null
        gattServer?.close()
        gattServer = null
        keyboardInputCharacteristic = null
        mouseInputCharacteristic = null
        isInitialized = false
        _connectionState.value = HidConnectionState.DISCONNECTED
    }
}
