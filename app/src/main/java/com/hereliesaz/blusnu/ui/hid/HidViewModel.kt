package com.hereliesaz.blusnu.ui.hid

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BleHidController
import com.hereliesaz.blusnu.data.HidConnectionState
import com.hereliesaz.blusnu.data.HidController
import com.hereliesaz.blusnu.data.HidKeyMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HidMode { CLASSIC, BLE }
enum class HidTab { KEYBOARD, TOUCHPAD }

data class HidUiState(
    val mode: HidMode = HidMode.BLE,
    val tab: HidTab = HidTab.KEYBOARD,
    val connectionState: HidConnectionState = HidConnectionState.DISCONNECTED,
    val statusMessages: List<String> = emptyList(),
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val classicSupported: Boolean = false,
    val bleSupported: Boolean = false
)

// Fix 2.10: Accept shared BleHidController instance instead of creating a new one.
// Both HidViewModel and KeystrokeInjectionViewModel now share the same BleHidController,
// avoiding duplicate GATT servers.
class HidViewModel(
    application: Application,
    private val bleController: BleHidController
) : AndroidViewModel(application) {

    private val classicController = HidController(application)

    private val _mode = MutableStateFlow(HidMode.BLE)
    private val _tab = MutableStateFlow(HidTab.KEYBOARD)
    private val _statusMessages = MutableStateFlow<List<String>>(emptyList())
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)

    // Genuine connection-state flow, kept in sync with the active controller's connectionState
    // (see collectConnectionStates). Using a dedicated flow ensures real state changes propagate
    // to the UI instead of relying on a no-op self-assignment of _mode.
    private val _connectionState = MutableStateFlow(HidConnectionState.DISCONNECTED)

    val state: StateFlow<HidUiState> = combine(
        _mode,
        _tab,
        _statusMessages,
        _pairedDevices,
        _selectedDevice
    ) { mode, tab, messages, paired, selected ->
        HidUiState(
            mode = mode,
            tab = tab,
            connectionState = HidConnectionState.DISCONNECTED,
            statusMessages = messages,
            pairedDevices = paired,
            selectedDevice = selected,
            classicSupported = classicController.isSupported(),
            bleSupported = bleController.isSupported()
        )
    }.combine(_connectionState) { uiState, connectionState ->
        uiState.copy(connectionState = connectionState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HidUiState())

    init {
        collectStatusMessages()
        collectConnectionStates()
        loadPairedDevices()
    }

    private fun collectStatusMessages() {
        viewModelScope.launch {
            classicController.statusMessage.collect { msg ->
                appendStatus("[Classic] $msg")
            }
        }
        viewModelScope.launch {
            bleController.statusMessage.collect { msg ->
                appendStatus("[BLE] $msg")
            }
        }
    }

    private fun collectConnectionStates() {
        // Emit a genuinely new connection state whenever the active controller's state changes,
        // or when the user switches modes. This drives real UI updates (StateFlow dedups
        // identical values, so the previous `_mode.value = _mode.value` emitted nothing).
        viewModelScope.launch {
            combine(
                _mode,
                classicController.connectionState,
                bleController.connectionState
            ) { mode, classicState, bleState ->
                when (mode) {
                    HidMode.CLASSIC -> classicState
                    HidMode.BLE -> bleState
                }
            }.collect { connectionState ->
                _connectionState.value = connectionState
            }
        }
    }

    @Suppress("MissingPermission")
    private fun loadPairedDevices() {
        try {
            val manager = getApplication<Application>()
                .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bonded = manager?.adapter?.bondedDevices?.toList() ?: emptyList()
            _pairedDevices.value = bonded
        } catch (_: SecurityException) {
            appendStatus("Permission denied: cannot list paired devices.")
        }
    }

    private fun appendStatus(message: String) {
        val current = _statusMessages.value
        _statusMessages.value = (current + message).takeLast(50)
    }

    fun setMode(mode: HidMode) {
        _mode.value = mode
    }

    fun setTab(tab: HidTab) {
        _tab.value = tab
    }

    fun selectDevice(device: BluetoothDevice) {
        _selectedDevice.value = device
    }

    fun initialize() {
        viewModelScope.launch {
            when (_mode.value) {
                HidMode.CLASSIC -> classicController.initialize()
                HidMode.BLE -> bleController.initialize()
            }
        }
    }

    fun connect() {
        val device = _selectedDevice.value
        if (device == null) {
            appendStatus("No device selected.")
            return
        }

        when (_mode.value) {
            HidMode.CLASSIC -> classicController.connectToHost(device)
            HidMode.BLE -> bleController.startAdvertising()
        }
    }

    fun disconnect() {
        when (_mode.value) {
            HidMode.CLASSIC -> classicController.disconnect()
            HidMode.BLE -> {
                bleController.stopAdvertising()
                bleController.cleanup()
            }
        }
    }

    fun typeText(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (_mode.value) {
                HidMode.CLASSIC -> classicController.typeString(text)
                HidMode.BLE -> bleController.typeString(text)
            }
        }
    }

    fun sendSpecialKey(keyCode: Byte, modifiers: Byte = HidKeyMap.MOD_NONE) {
        viewModelScope.launch {
            when (_mode.value) {
                HidMode.CLASSIC -> {
                    classicController.sendKeyPress(keyCode, modifiers)
                    classicController.sendKeyRelease()
                }
                HidMode.BLE -> {
                    bleController.sendKeyPress(keyCode, modifiers)
                    bleController.sendKeyRelease()
                }
            }
        }
    }

    fun sendMouseMove(dx: Int, dy: Int) {
        viewModelScope.launch {
            when (_mode.value) {
                HidMode.CLASSIC -> classicController.sendMouseMove(dx, dy)
                HidMode.BLE -> bleController.sendMouseMove(dx, dy)
            }
        }
    }

    fun sendMouseClick(button: Byte = HidKeyMap.MOUSE_BUTTON_LEFT) {
        viewModelScope.launch {
            when (_mode.value) {
                HidMode.CLASSIC -> classicController.sendMouseClick(button)
                HidMode.BLE -> bleController.sendMouseClick(button)
            }
        }
    }

    fun sendMouseScroll(delta: Int) {
        viewModelScope.launch {
            when (_mode.value) {
                HidMode.CLASSIC -> classicController.sendMouseScroll(delta)
                HidMode.BLE -> bleController.sendMouseScroll(delta)
            }
        }
    }

    fun refreshPairedDevices() {
        loadPairedDevices()
    }

    override fun onCleared() {
        super.onCleared()
        classicController.cleanup()
        bleController.cleanup()
    }
}
