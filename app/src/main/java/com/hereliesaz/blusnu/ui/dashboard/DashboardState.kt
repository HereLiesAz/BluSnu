package com.hereliesaz.blusnu.ui.dashboard

import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.data.SavedSession
import com.hereliesaz.blusnu.data.AttackChainTemplate

/**
 * Immutable state class for the Dashboard screen.
 *
 * @property bleDeviceCount Number of BLE devices currently in the database.
 * @property classicDeviceCount Number of Classic devices currently in the database.
 * @property isScanning Whether a background scan is active.
 * @property devicesWithLocation List of devices that have GPS coordinates (for Heatmap).
 * @property savedSessions List of previously saved sessions.
 * @property attackChainTemplates List of available attack workflows.
 */
data class DashboardState(
    val bleDeviceCount: Int = 0,
    val classicDeviceCount: Int = 0,
    val isScanning: Boolean = false,
    val devicesWithLocation: List<TargetDevice> = emptyList(),
    val savedSessions: List<SavedSession> = emptyList(),
    val attackChainTemplates: List<AttackChainTemplate> = emptyList()
)
