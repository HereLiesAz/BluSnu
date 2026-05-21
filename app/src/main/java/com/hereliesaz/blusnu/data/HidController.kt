package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

enum class HidConnectionState {
    DISCONNECTED,
    REGISTERING,
    REGISTERED,
    CONNECTING,
    CONNECTED,
    ERROR
}

@SuppressLint("MissingPermission")
class HidController(private val context: Context) {

    companion object {
        private const val TAG = "HidController"
        private const val KEYBOARD_REPORT_ID = 1
        private const val MOUSE_REPORT_ID = 2
    }

    private val _connectionState = MutableStateFlow(HidConnectionState.DISCONNECTED)
    val connectionState: StateFlow<HidConnectionState> = _connectionState

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val statusMessage: SharedFlow<String> = _statusMessage

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.address}")
            if (registered) {
                _connectionState.value = HidConnectionState.REGISTERED
                _statusMessage.tryEmit("HID app registered. Ready to connect.")
            } else {
                _connectionState.value = HidConnectionState.DISCONNECTED
                _statusMessage.tryEmit("HID app unregistered.")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: state=$state, device=${device?.address}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    _connectionState.value = HidConnectionState.CONNECTED
                    _statusMessage.tryEmit("Connected to ${device?.name ?: device?.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedHost = null
                    _connectionState.value = HidConnectionState.REGISTERED
                    _statusMessage.tryEmit("Disconnected from host.")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = HidConnectionState.CONNECTING
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport: type=$type, id=$id")
            hidDevice?.replyReport(device, type, id, ByteArray(bufferSize))
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "onSetReport: type=$type, id=$id")
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _connectionState.value = HidConnectionState.DISCONNECTED
                _statusMessage.tryEmit("HID service disconnected.")
            }
        }
    }

    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bluetoothAdapter != null
    }

    fun initialize() {
        if (!isSupported()) {
            _connectionState.value = HidConnectionState.ERROR
            _statusMessage.tryEmit("Classic HID requires Android 9+ (API 28)")
            return
        }

        _connectionState.value = HidConnectionState.REGISTERING
        _statusMessage.tryEmit("Initializing Classic HID...")

        val success = bluetoothAdapter?.getProfileProxy(
            context,
            profileListener,
            BluetoothProfile.HID_DEVICE
        ) ?: false

        if (!success) {
            _connectionState.value = HidConnectionState.ERROR
            _statusMessage.tryEmit("Failed to get HID device profile proxy.")
        }
    }

    private fun registerApp() {
        val device = hidDevice ?: return

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BluSnu HID",
            "Bluetooth Keyboard & Mouse",
            "BluSnu",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidKeyMap.REPORT_DESCRIPTOR
        )

        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )

        val registered = device.registerApp(sdp, null, qos, executor, hidCallback)
        if (!registered) {
            _connectionState.value = HidConnectionState.ERROR
            _statusMessage.tryEmit("Failed to register HID app. Device may not support it.")
        }
    }

    fun connectToHost(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            _statusMessage.tryEmit("HID not initialized.")
            return
        }
        if (_connectionState.value != HidConnectionState.REGISTERED) {
            _statusMessage.tryEmit("HID app not registered. Initialize first.")
            return
        }

        _connectionState.value = HidConnectionState.CONNECTING
        _statusMessage.tryEmit("Connecting to ${device.name ?: device.address}...")
        hid.connect(device)
    }

    fun disconnect() {
        val host = connectedHost ?: return
        hidDevice?.disconnect(host)
        connectedHost = null
        _connectionState.value = HidConnectionState.REGISTERED
    }

    fun sendKeyPress(keyCode: Byte, modifiers: Byte = HidKeyMap.MOD_NONE) {
        val host = connectedHost ?: return
        val report = byteArrayOf(modifiers, 0x00, keyCode, 0, 0, 0, 0, 0)
        hidDevice?.sendReport(host, KEYBOARD_REPORT_ID, report)
    }

    fun sendKeyRelease() {
        val host = connectedHost ?: return
        val report = ByteArray(8)
        hidDevice?.sendReport(host, KEYBOARD_REPORT_ID, report)
    }

    fun typeCharacter(char: Char) {
        val mapping = HidKeyMap.charToHid(char) ?: return
        sendKeyPress(mapping.keyCode, mapping.modifier)
        sendKeyRelease()
    }

    fun typeString(text: String) {
        for (char in text) {
            typeCharacter(char)
        }
    }

    fun sendMouseMove(dx: Int, dy: Int) {
        val host = connectedHost ?: return
        val clampedX = dx.coerceIn(-127, 127).toByte()
        val clampedY = dy.coerceIn(-127, 127).toByte()
        val report = byteArrayOf(0x00, clampedX, clampedY, 0x00)
        hidDevice?.sendReport(host, MOUSE_REPORT_ID, report)
    }

    fun sendMouseClick(button: Byte = HidKeyMap.MOUSE_BUTTON_LEFT) {
        val host = connectedHost ?: return
        // Press
        hidDevice?.sendReport(host, MOUSE_REPORT_ID, byteArrayOf(button, 0, 0, 0))
        // Release
        hidDevice?.sendReport(host, MOUSE_REPORT_ID, byteArrayOf(0, 0, 0, 0))
    }

    fun sendMouseScroll(delta: Int) {
        val host = connectedHost ?: return
        val clampedDelta = delta.coerceIn(-127, 127).toByte()
        val report = byteArrayOf(0x00, 0x00, 0x00, clampedDelta)
        hidDevice?.sendReport(host, MOUSE_REPORT_ID, report)
    }

    fun cleanup() {
        disconnect()
        hidDevice?.unregisterApp()
        hidDevice?.let {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hidDevice = null
        _connectionState.value = HidConnectionState.DISCONNECTED
    }
}
