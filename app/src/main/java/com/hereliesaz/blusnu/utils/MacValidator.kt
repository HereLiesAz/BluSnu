package com.hereliesaz.blusnu.utils

object MacValidator {
    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    fun isValid(mac: String): Boolean = MAC_REGEX.matches(mac)

    fun requireValid(mac: String): String {
        require(MAC_REGEX.matches(mac)) { "Invalid MAC address: $mac" }
        return mac
    }
}
