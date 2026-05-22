package com.hereliesaz.blusnu.data

import androidx.room.TypeConverter

/**
 * Type Converters for Room Database.
 *
 * Room natively supports primitive types. To store complex objects like Lists or Enums,
 * we must provide methods to convert them to/from supported types (e.g., String).
 */
class Converters {

    /**
     * Converts a comma-separated String from the DB back into a List of Strings.
     */
    @TypeConverter
    fun fromString(value: String): List<String> {
        // Handle empty string case to avoid list with one empty string.
        if (value.isEmpty()) return emptyList()
        return value.split(",").map { it.trim() }
    }

    /**
     * Converts a List of Strings into a comma-separated String for storage.
     */
    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(",")
    }

    /**
     * Converts a Protocol Enum to its String name.
     */
    @TypeConverter
    fun fromProtocol(protocol: Protocol): String {
        return protocol.name
    }

    /**
     * Converts a String name back to a Protocol Enum.
     */
    @TypeConverter
    fun toProtocol(name: String): Protocol {
        return try {
            Protocol.valueOf(name)
        } catch (e: IllegalArgumentException) {
            // Default to Classic if unknown (backward compatibility safety).
            Protocol.CLASSIC
        }
    }
}
