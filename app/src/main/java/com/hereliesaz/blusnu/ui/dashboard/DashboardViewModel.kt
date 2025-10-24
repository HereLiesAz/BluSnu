package com.hereliesaz.blusnu.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.AttackChainTemplate
import com.hereliesaz.blusnu.data.AttackChainTemplateRepository
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.DeviceWithLocation
import com.hereliesaz.blusnu.data.Location
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.SavedSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardState(
    val bleDeviceCount: Int = 0,
    val classicDeviceCount: Int = 0,
    val isScanning: Boolean = false,
    val devicesWithLocation: List<com.hereliesaz.blusnu.data.DeviceWithLocation> = emptyList(),
    val savedSessions: List<SavedSession> = emptyList(),
    val attackChainTemplates: List<AttackChainTemplate> = emptyList()
)

class DashboardViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val savedSessionRepository: com.hereliesaz.blusnu.data.SavedSessionRepository,
    private val attackChainTemplateRepository: AttackChainTemplateRepository
) : AndroidViewModel(application) {

    val state: StateFlow<DashboardState> = combine(
        deviceRepository.allDevices,
        savedSessionRepository.allSessions,
        attackChainTemplateRepository.allTemplates
    ) { devices, sessions, templates ->
        val bleCount = devices.count { it.protocol == Protocol.BLE }
        val classicCount = devices.count { it.protocol == Protocol.CLASSIC }
        val devicesWithLocation = devices.mapNotNull { device ->
            device.estimatedLocation?.let { location ->
                DeviceWithLocation(device, Location(location.latitude, location.longitude))
            }
        }
        DashboardState(
            bleDeviceCount = bleCount,
            classicDeviceCount = classicCount,
            devicesWithLocation = devicesWithLocation,
            savedSessions = sessions,
            attackChainTemplates = templates
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardState()
    )

    fun onStartScanClicked() {
        // TODO: Implement scan logic
    }
}
