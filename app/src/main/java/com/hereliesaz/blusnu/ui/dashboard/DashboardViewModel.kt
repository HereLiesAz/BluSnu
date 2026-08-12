package com.hereliesaz.blusnu.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActiveTask
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.AttackChainTemplate
import com.hereliesaz.blusnu.data.AttackChainTemplateRepository
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.DeviceWithLocation
import com.hereliesaz.blusnu.data.Location
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.data.SavedSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

import com.hereliesaz.blusnu.data.TargetDevice

/**
 * ViewModel for the Dashboard screen.
 *
 * Aggregates data from multiple repositories (Devices, Sessions, Templates) to
 * provide a unified state for the dashboard UI.
 */
class DashboardViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val savedSessionRepository: com.hereliesaz.blusnu.data.SavedSessionRepository,
    private val attackChainTemplateRepository: AttackChainTemplateRepository
) : AndroidViewModel(application) {

    /**
     * Registers a running background task so it appears on the dashboard.
     * Delegates to the global [ActiveTaskManager] singleton so any ViewModel
     * in the app can push tasks without holding a reference to the dashboard.
     */
    fun addActiveTask(task: ActiveTask) {
        ActiveTaskManager.add(task)
    }

    /**
     * Removes a completed/cancelled task from the dashboard by id.
     */
    fun removeActiveTask(taskId: String) {
        ActiveTaskManager.remove(taskId)
    }

    val state: StateFlow<DashboardState> = combine(
        deviceRepository.allDevices,
        savedSessionRepository.allSessions,
        attackChainTemplateRepository.allTemplates,
        ActiveTaskManager.tasks
    ) { devices, sessions, templates, activeTasks ->
        // Calculate derived statistics.
        val bleCount = devices.count { it.protocol == Protocol.BLE }
        val classicCount = devices.count { it.protocol == Protocol.CLASSIC }
        val devicesWithLocation = devices.filter { it.latitude != null && it.longitude != null }

        // Return new state.
        DashboardState(
            bleDeviceCount = bleCount,
            classicDeviceCount = classicCount,
            devicesWithLocation = devicesWithLocation,
            savedSessions = sessions,
            attackChainTemplates = templates,
            activeTasks = activeTasks
        )
    }.stateIn(
        // Keep the flow active for 5 seconds after the last subscriber disappears (e.g. rotation).
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardState()
    )

    /**
     * Handler for the "Start Scan" button click.
     */
    fun onStartScanClicked() {
        // This is handled by navigation in the UI layer (navigating to Targets screen),
        // so no logic is needed here for now.
    }
}
