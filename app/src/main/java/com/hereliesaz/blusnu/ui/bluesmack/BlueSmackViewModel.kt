package com.hereliesaz.blusnu.ui.bluesmack

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.BlueSmackModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class BlueSmackViewModel(
    application: Application,
    deviceRepository: DeviceRepository,
    private val blueSmackModule: BlueSmackModule
) : AndroidViewModel(application) {

    private val _selectedDevice = MutableStateFlow<TargetDevice?>(null)
    val selectedDevice: StateFlow<TargetDevice?> = _selectedDevice

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _devices = MutableStateFlow<List<TargetDevice>>(emptyList())
    val devices: StateFlow<List<TargetDevice>> = _devices.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _packetSize = MutableStateFlow("600")
    val packetSize: StateFlow<String> = _packetSize

    private val _packetCount = MutableStateFlow("1000")
    val packetCount: StateFlow<String> = _packetCount

    private val _interfaceName = MutableStateFlow("hci0")
    val interfaceName: StateFlow<String> = _interfaceName

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(BluetoothManager::class.java) as BluetoothManager).adapter

    private var attackJob: Job? = null
    private var currentTaskId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.allDevices.collect { allDevices ->
                _devices.value = allDevices.filter {
                    it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL
                }
            }
        }
    }

    fun onDeviceSelected(device: TargetDevice) {
        _selectedDevice.value = device
    }

    fun onPacketSizeChanged(value: String) {
        _packetSize.value = value
        _validationError.value = null
    }

    fun onPacketCountChanged(value: String) {
        _packetCount.value = value
        _validationError.value = null
    }

    fun onInterfaceNameChanged(value: String) {
        _interfaceName.value = value
        _validationError.value = null
    }

    fun startAttack() {
        if (_isRunning.value) return

        val selected = _selectedDevice.value ?: return

        if (bluetoothAdapter == null) {
            _status.value = "Bluetooth is not supported on this device"
            return
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(selected.macAddress)
        } catch (e: IllegalArgumentException) {
            _status.value = "Invalid MAC address"
            return
        }

        // Validate packet size
        val sizeRaw = _packetSize.value.toIntOrNull()
        if (sizeRaw == null) {
            _validationError.value = "Packet size must be a number"
            return
        }
        if (sizeRaw !in BlueSmackModule.PACKET_SIZE_RANGE) {
            _validationError.value =
                "Packet size must be between ${BlueSmackModule.PACKET_SIZE_RANGE.first} and ${BlueSmackModule.PACKET_SIZE_RANGE.last}"
            return
        }

        // Validate packet count
        val countRaw = _packetCount.value.toIntOrNull()
        if (countRaw == null) {
            _validationError.value = "Packet count must be a number"
            return
        }
        if (countRaw !in BlueSmackModule.PACKET_COUNT_RANGE) {
            _validationError.value =
                "Packet count must be between ${BlueSmackModule.PACKET_COUNT_RANGE.first} and ${BlueSmackModule.PACKET_COUNT_RANGE.last}"
            return
        }

        // Validate interface name
        val iface = _interfaceName.value
        try {
            BlueSmackModule.requireValidInterface(iface)
        } catch (e: IllegalArgumentException) {
            _validationError.value = e.message
            return
        }

        _validationError.value = null

        val taskId = "bluesmack_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("BlueSmack: Started attack on ${selected.macAddress} (size=$sizeRaw, count=$countRaw, iface=$iface)")
        ActiveTaskManager.add(taskId, "BlueSmack Attack", "Targeting ${selected.name ?: selected.macAddress}")

        _isRunning.value = true
        attackJob = viewModelScope.launch {
            try {
                _status.value = "Running l2ping flood against ${device.address}... (requires root)"
                blueSmackModule.startAttack(device.address, sizeRaw, countRaw, iface).collect { line ->
                    _status.value = line
                }
                _status.value = "Attack finished."
                ActionLogger.log("BlueSmack: Attack completed on ${selected.macAddress}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
                ActionLogger.log("BlueSmack: Error on ${selected.macAddress} - ${e.message}")
            } finally {
                _isRunning.value = false
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
            }
        }
    }

    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        _isRunning.value = false
        blueSmackModule.destroyProcess()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                RootExecutor.execute("killall l2ping")
            }
            _status.value = "Attack stopped."
            ActionLogger.log("BlueSmack: Attack stopped by user")
        }
    }

    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        blueSmackModule.destroyProcess()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        // Best-effort kill of any orphaned l2ping processes
        try {
            val killProcess = Runtime.getRuntime().exec("su")
            killProcess.outputStream.write("killall l2ping\nexit\n".toByteArray())
            killProcess.outputStream.flush()
            killProcess.outputStream.close()
            killProcess.waitFor()
            killProcess.destroyForcibly()
        } catch (_: Exception) {
            // Best-effort cleanup; ignore failures during teardown
        }
    }
}
