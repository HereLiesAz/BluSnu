package com.hereliesaz.blusnu.data

import com.google.android.gms.maps.model.LatLng

data class TargetDevice(
    val macAddress: String,
    val name: String?,
    val rssi: Int,
    val protocol: Protocol,
    val services: List<String> = emptyList(),
    val vulnerabilities: List<Vulnerability> = emptyList(),
    val isNew: Boolean = true,
    val estimatedLocation: LatLng? = null
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
