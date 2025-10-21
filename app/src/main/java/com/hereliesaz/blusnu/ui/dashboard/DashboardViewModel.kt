package com.hereliesaz.blusnu.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.Protocol
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardState(
    val bleDeviceCount: Int = 0,
    val classicDeviceCount: Int = 0,
    val isScanning: Boolean = false
)

class DashboardViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    val state: StateFlow<DashboardState> = deviceRepository.discoveredDevices
        .map { devices ->
            val bleCount = devices.count { it.protocol == Protocol.BLE }
            val classicCount = devices.count { it.protocol == Protocol.CLASSIC }
            DashboardState(bleDeviceCount = bleCount, classicDeviceCount = classicCount)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardState()
        )

    fun onStartScanClicked() {
        // TODO: Implement scan logic
    }
}
