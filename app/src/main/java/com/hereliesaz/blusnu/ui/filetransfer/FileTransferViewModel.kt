package com.hereliesaz.blusnu.ui.filetransfer

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.FileTransferController
import com.hereliesaz.blusnu.data.TransferInfo
import com.hereliesaz.blusnu.data.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FileTransferUiState(
    val serverRunning: Boolean = false,
    val transferStatus: TransferStatus = TransferStatus.IDLE,
    val transferProgress: Float = 0f,
    val currentTransfer: TransferInfo? = null,
    val transferHistory: List<TransferInfo> = emptyList(),
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val statusMessages: List<String> = emptyList()
)

class FileTransferViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = FileTransferController(application)

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    private val _selectedFileName = MutableStateFlow<String?>(null)
    private val _statusMessages = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<FileTransferUiState> = combine(
        controller.serverRunning,
        controller.transferStatus,
        controller.transferProgress,
        controller.currentTransfer,
        controller.transferHistory
    ) { serverRunning, status, progress, current, history ->
        FileTransferUiState(
            serverRunning = serverRunning,
            transferStatus = status,
            transferProgress = progress,
            currentTransfer = current,
            transferHistory = history,
            pairedDevices = _pairedDevices.value,
            selectedDevice = _selectedDevice.value,
            selectedFileUri = _selectedFileUri.value,
            selectedFileName = _selectedFileName.value,
            statusMessages = _statusMessages.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FileTransferUiState())

    init {
        loadPairedDevices()
        collectStatusMessages()
    }

    private fun collectStatusMessages() {
        viewModelScope.launch {
            controller.statusMessage.collect { msg ->
                _statusMessages.value = (_statusMessages.value + msg).takeLast(50)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun loadPairedDevices() {
        try {
            val manager = getApplication<Application>()
                .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            _pairedDevices.value = manager?.adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            _statusMessages.value = _statusMessages.value + "Permission denied: cannot list paired devices."
        }
    }

    fun selectDevice(device: BluetoothDevice) {
        _selectedDevice.value = device
    }

    fun selectFile(uri: Uri, fileName: String?) {
        _selectedFileUri.value = uri
        _selectedFileName.value = fileName
    }

    fun sendFile() {
        val device = _selectedDevice.value ?: return
        val uri = _selectedFileUri.value ?: return
        controller.sendFile(device, uri)
    }

    fun toggleServer() {
        if (controller.serverRunning.value) {
            controller.stopServer()
        } else {
            controller.startServer()
        }
    }

    fun cancelTransfer() {
        controller.cancelTransfer()
    }

    fun refreshPairedDevices() {
        loadPairedDevices()
    }

    override fun onCleared() {
        super.onCleared()
        controller.cleanup()
    }
}
