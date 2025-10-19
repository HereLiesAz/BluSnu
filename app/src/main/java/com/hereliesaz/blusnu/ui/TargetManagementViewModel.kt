package com.hereliesaz.blusnu.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluetoothScanner
import com.hereliesaz.blusnu.data.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class TargetManagementViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TargetManagementScreenState())
    val state: StateFlow<TargetManagementScreenState> = _state

    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val bluetoothScanner =
        bluetoothAdapter?.let { BluetoothScanner(application, deviceRepository, it) }

    init {
        deviceRepository.discoveredDevices.onEach { devices ->
            _state.update { it.copy(devices = devices) }
        }.launchIn(viewModelScope)

        updatePermissionsState()
        updateBluetoothState()
    }

    fun startScan() {
        if (_state.value.hasPermissions && _state.value.isBluetoothEnabled) {
            _state.update { it.copy(isScanning = true) }
            bluetoothScanner?.startClassicDiscovery()
            bluetoothScanner?.startBleScan()
        }
    }

    fun stopScan() {
        _state.update { it.copy(isScanning = false) }
        bluetoothScanner?.stopClassicDiscovery()
        bluetoothScanner?.stopBleScan()
    }

    fun updatePermissionsState() {
        _state.update { it.copy(hasPermissions = hasBluetoothPermissions()) }
    }

    fun updateBluetoothState() {
        _state.update { it.copy(isBluetoothEnabled = isBluetoothEnabled()) }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getApplication<Application>().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    getApplication<Application>().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            getApplication<Application>().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }
}
