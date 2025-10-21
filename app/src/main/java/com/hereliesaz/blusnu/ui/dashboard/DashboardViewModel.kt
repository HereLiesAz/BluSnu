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
    val isScanning: Boolean = false,
    val activeTasks: List<ActiveTask> = emptyList(),
    val savedSessions: List<SavedSession> = emptyList(),
    val attackChainTemplates: List<AttackChainTemplate> = emptyList()
)

class DashboardViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    val state: StateFlow<DashboardState> = deviceRepository.discoveredDevices
        .map { devices ->
            val bleCount = devices.count { it.protocol == Protocol.BLE }
            val classicCount = devices.count { it.protocol == Protocol.CLASSIC }
            DashboardState(
                bleDeviceCount = bleCount,
                classicDeviceCount = classicCount,
                activeTasks = getSampleActiveTasks(),
                savedSessions = getSampleSavedSessions(),
                attackChainTemplates = getSampleAttackChainTemplates()
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardState()
        )

    private fun getSampleActiveTasks(): List<ActiveTask> {
        return listOf(
            ActiveTask("1", "Sniffing BLE traffic", "Monitoring BLE advertisements on channel 37"),
            ActiveTask("2", "Cracking classic PIN", "Brute-forcing PIN on device 00:11:22:33:44:55")
        )
    }

    private fun getSampleSavedSessions(): List<SavedSession> {
        return listOf(
            SavedSession("1", "Coffee Shop Audit", "2025-10-20"),
            SavedSession("2", "Office Pentest", "2025-10-19")
        )
    }

    private fun getSampleAttackChainTemplates(): List<AttackChainTemplate> {
        return listOf(
            AttackChainTemplate("1", "BLE Smart Lock Audit", "A template for auditing BLE smart locks"),
            AttackChainTemplate("2", "Opportunistic Eavesdropping", "A template for passively monitoring BLE traffic")
        )
    }

    fun onStartScanClicked() {
        // TODO: Implement scan logic
    }
}
