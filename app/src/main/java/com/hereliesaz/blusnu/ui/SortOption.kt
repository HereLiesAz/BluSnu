package com.hereliesaz.blusnu.ui

/**
 * Enum for sorting options in the device list.
 */
enum class SortOption {
    NONE,       // Unsorted (order of discovery).
    RSSI_ASC,   // Signal strength Ascending (Weakest first).
    RSSI_DESC   // Signal strength Descending (Strongest/Closest first).
}
