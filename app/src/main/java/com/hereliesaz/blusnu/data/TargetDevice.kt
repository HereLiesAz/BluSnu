package com.hereliesaz.blusnu.data

data class TargetDevice(
    val macAddress: String,
    val name: String?,
    val rssi: Int,
    val protocol: Protocol,
    val services: List<String> = emptyList(),
    val vulnerabilities: List<Vulnerability> = emptyList(),
    val isFavorite: Boolean = false,
    val notes: String = ""
)

data class Vulnerability(
    val name: String,
    val uuid: String,
    val description: String,
    val cve: String
)

enum class Protocol {
    CLASSIC,
    BLE,
    DUAL
}
