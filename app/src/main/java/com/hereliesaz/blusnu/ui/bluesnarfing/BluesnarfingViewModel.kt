package com.hereliesaz.blusnu.ui.bluesnarfing

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.BluesnarfingModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Bluesnarfing feature.
 *
 * Manages the state of the attack screen and orchestrates the interaction
 * between the UI and the underlying [BluesnarfingModule] data logic.
 *
 * @property application Application context for system services.
 * @property deviceRepository Repository to fetch candidate targets.
 */
class BluesnarfingViewModel(application: Application, deviceRepository: DeviceRepository) : AndroidViewModel(application) {

    // Currently selected target.
    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    // Status message (e.g., "Connecting...", "Failed").
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    // The payload/result of the attack (e.g. Phonebook text).
    private val _result = MutableStateFlow("")
    val result: StateFlow<String> = _result

    // Filtered list of candidate devices (Classic/Dual only).
    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    // The attack logic module.
    private val bluesnarfingModule = BluesnarfingModule()

    // System adapter for creating remote device objects.
    private val bluetoothAdapter: BluetoothAdapter? = (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    private var hasPermissions = false

    init {
        // Collect devices from repository and filter for valid targets (Classic).
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    /**
     * Called by UI to update permission state.
     */
    fun onPermissionsResult(hasPermissions: Boolean) {
        this.hasPermissions = hasPermissions
    }

    /**
     * Called when user picks a device from dropdown.
     */
    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    /**
     * Initiates the attack sequence.
     */
    fun startAttack() {
        if (!hasPermissions) {
            _status.value = "Bluetooth connect permission is required"
            return
        }

        val selected = _selectedDevice.value ?: return

        // Log start for audit report.
        ActionLogger.log("Bluesnarfing attack started against ${selected.macAddress}.")

        if (bluetoothAdapter == null) {
            _status.value = "Bluetooth is not supported on this device"
            return
        }

        // Get the Android BluetoothDevice object.
        val device = try {
            bluetoothAdapter.getRemoteDevice(selected.macAddress)
        } catch (e: IllegalArgumentException) {
            _status.value = "Invalid MAC address"
            return
        }

        // Run the blocking IO operation in a coroutine.
        viewModelScope.launch {
            _status.value = "Connecting..."

            // Switch to IO dispatcher for network operations.
            val result = withContext(Dispatchers.IO) {
                bluesnarfingModule.getPhonebook(device)
            }

            _status.value = "Finished"

            // Update UI with success data or error message.
            result.onSuccess {
                _result.value = it
            }.onFailure {
                _result.value = it.message ?: "Unknown error"
            }
        }
    }
}
