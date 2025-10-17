package com.hereliesaz.blusnu.data

data class TargetDevice(
    val macAddress: String,
    val name: String?,
    val rssi: Int,
    val protocol: Protocol,
    val services: List<String> = emptyList()
)

enum class Protocol {
    CLASSIC,
    BLE,
    DUAL
}