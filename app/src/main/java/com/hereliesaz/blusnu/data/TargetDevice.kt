package com.hereliesaz.blusnu.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Data class representing a discovered Bluetooth device.
 *
 * This entity is stored in the Room database table "target_devices".
 * It serves as the core data model for the reconnaissance phase, holding
 * identification, signal strength, protocol, and service information.
 *
 * @property macAddress The unique hardware address (MAC) of the device. Used as the Primary Key.
 * @property name The human-readable name of the device (if available).
 * @property rssi The Received Signal Strength Indication (RSSI) in dBm.
 * @property protocol The Bluetooth protocol used by the device (CLASSIC, BLE, or DUAL).
 * @property services A list of UUID strings representing the services advertised or supported by the device.
 * @property lastSeen The timestamp (in milliseconds) when the device was last detected.
 * @property latitude The geographical latitude where the device was found (optional).
 * @property longitude The geographical longitude where the device was found (optional).
 * @property notes User-defined notes or annotations for this specific device.
 * @property isFavorite Boolean flag indicating if the user has marked this device as a favorite/target.
 */
@Entity(tableName = "target_devices")
// Applies the custom TypeConverters to this entity, allowing complex types (like List<String>)
// and enums (like Protocol) to be stored in the SQLite database.
@TypeConverters(Converters::class)
data class TargetDevice(
    // The MAC address is the unique identifier for a Bluetooth device.
    // We use it as the primary key for the database table.
    @PrimaryKey val macAddress: String,

    // The friendly name, e.g., "JBL Flip 5" or "iPhone". Can be null.
    val name: String?,

    // The signal strength value. Updated on every scan result.
    // Used for distance estimation and proximity sorting.
    val rssi: Int,

    // Enum indicating if the device is BR/EDR (Classic), Low Energy (BLE), or both.
    val protocol: Protocol,

    // List of Service UUIDs. Default is an empty list.
    // This is crucial for fingerprinting and identifying vulnerabilities.
    val services: List<String> = emptyList(),

    // Timestamp of the last scan packet received from this device.
    // Used to prune old devices or show "active now" status.
    val lastSeen: Long = 0L,

    // GPS coordinates for physical mapping of devices.
    // Nullable because location might not be available or permission denied.
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Field for the user to type manual notes about the target.
    // Default is an empty string.
    val notes: String = "",

    // Flag to pin the device to the top of the list or mark as a high-value target.
    // Default is false.
    val isFavorite: Boolean = false
) {
    // This field is NOT stored in the database (@Ignore).
    // It is populated at runtime by the VulnerabilityCorrelator.
    // It holds a list of potential vulnerabilities associated with this device's profile.
    @Ignore
    var vulnerabilities: List<Vulnerability> = emptyList()
}

/**
 * Data class representing a specific security vulnerability (CVE).
 *
 * @property name The short name or title of the vulnerability.
 * @property uuid The Service UUID associated with this vulnerability (used for matching).
 * @property description A detailed description of the exploit or flaw.
 * @property cve The Common Vulnerabilities and Exposures (CVE) ID (e.g., "CVE-2023-24023").
 */
data class Vulnerability(
    val name: String,
    val uuid: String,
    val description: String,
    val cve: String
)

/**
 * Enum representing the Bluetooth protocol types.
 */
enum class Protocol {
    // Basic Rate/Enhanced Data Rate - Traditional Bluetooth.
    CLASSIC,
    // Bluetooth Low Energy.
    BLE,
    // Device supports both Classic and BLE (Dual Mode).
    DUAL
}
