package com.hereliesaz.blusnu.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.android.gms.maps.model.LatLng

@Entity(tableName = "target_devices")
@TypeConverters(Converters::class)
data class TargetDevice(
    @PrimaryKey val macAddress: String,
    val name: String?,
    val rssi: Int,
    val protocol: Protocol,
    val services: List<String> = emptyList(),
    val lastSeen: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = "",
    val isFavorite: Boolean = false
) {
    @Ignore
    var vulnerabilities: List<Vulnerability> = emptyList()

    @get:Ignore
    val estimatedLocation: LatLng?
        get() = if (latitude != null && longitude != null) {
            LatLng(latitude, longitude)
        } else {
            null
        }
}

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
