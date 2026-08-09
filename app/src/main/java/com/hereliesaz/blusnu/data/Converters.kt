package com.hereliesaz.blusnu.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type Converters for Room Database.
 *
 * Room natively supports primitive types. To store complex objects like Lists or Enums,
 * we must provide methods to convert them to/from supported types (e.g., String).
 */
class Converters {

    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        // Handle both JSON array format and legacy comma-separated format
        if (value.startsWith("[")) {
            return gson.fromJson<List<String>>(value, listType) ?: emptyList()
        }
        return value.split(",").map { it.trim() }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        if (list.isEmpty()) return ""
        return gson.toJson(list)
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
