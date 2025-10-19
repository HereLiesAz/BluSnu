package com.hereliesaz.blusnu.data

data class Vulnerability(
    val serviceUuid: String,
    val vulnerabilityName: String,
    val cve: String
)

data class TargetDevice(
    val macAddress: String,
    val name: String?,
    val rssi: Int,
    val protocol: Protocol,
    val services: List<String> = emptyList(),
    val vulnerabilities: List<Vulnerability> = emptyList()
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
