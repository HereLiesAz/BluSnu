package com.hereliesaz.blusnu.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        return value.split(",").map { it.trim() }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun fromProtocol(protocol: Protocol): String {
        return protocol.name
    }

    @TypeConverter
    fun toProtocol(name: String): Protocol {
        return Protocol.valueOf(name)
    }
}
