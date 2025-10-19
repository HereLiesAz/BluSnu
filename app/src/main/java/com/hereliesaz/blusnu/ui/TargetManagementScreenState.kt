package com.hereliesaz.blusnu.ui

import com.hereliesaz.blusnu.data.TargetDevice

data class TargetManagementScreenState(
    val devices: List<TargetDevice> = emptyList(),
    val isScanning: Boolean = false,
    val hasPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val selectedFilter: FilterProtocol = FilterProtocol.ALL,
    val selectedSort: SortOption = SortOption.NONE
)
