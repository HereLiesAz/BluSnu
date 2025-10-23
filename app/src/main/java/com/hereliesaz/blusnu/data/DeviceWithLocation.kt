package com.hereliesaz.blusnu.data

data class DeviceWithLocation(
    val device: TargetDevice,
    val location: Location
)

data class Location(
    val latitude: Double,
    val longitude: Double
)
