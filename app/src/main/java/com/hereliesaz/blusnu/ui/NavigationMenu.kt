package com.hereliesaz.blusnu.ui

/**
 * Data class representing a category in the Navigation Menu (Rail).
 *
 * @property id Unique ID for the category (e.g., "recon").
 * @property text Display text for the category header (e.g., "Recon").
 * @property items List of sub-items (screens) within this category.
 */
data class NavigationCategory(
    val id: String,
    val text: String,
    val items: List<NavigationItem>
)

/**
 * Data class representing a single navigation item/screen.
 *
 * @property id Unique ID for the item.
 * @property text Display label (short).
 * @property route The Jetpack Navigation route string.
 */
data class NavigationItem(
    val id: String,
    val text: String,
    val route: String
)

/**
 * The static definition of the application's navigation structure.
 * This list drives the AzNavRail configuration in MainActivity.
 */
val MENU_CATEGORIES = listOf(
    // Reconnaissance Category
    NavigationCategory(
        id = "recon",
        text = "Recon",
        items = listOf(
            NavigationItem("dashboard", "Dashboard", "dashboard"),
            NavigationItem("targets", "Targets", "targets"), // Device Management
            NavigationItem("find", "Find", "find"), // Geolocation
            NavigationItem("report", "Report", "report"),
            NavigationItem("bluetooth_log", "Log", "bluetooth_log")
        )
    ),
    // Impersonation Attacks
    NavigationCategory(
        id = "impersonation",
        text = "Fake",
        items = listOf(
            NavigationItem("spoofing", "Spoofing", "spoofing"),
            NavigationItem("btlejacking", "Jacking", "btlejacking"),
            NavigationItem("btlejuice", "Juice", "btlejuice"),
            NavigationItem("gattrelay", "Relay", "gattrelay"),
            NavigationItem("bluffs", "BLUFFS", "bluffs")
        )
    ),
    // Exploitation Attacks
    NavigationCategory(
        id = "exploitation",
        text = "Exploit",
        items = listOf(
            NavigationItem("bluebugging", "Bugging", "bluebugging"),
            NavigationItem("bluesnarfing", "Snarfing", "bluesnarfing"),
            NavigationItem("keystroke_injection", "Injection", "keystroke_injection"),
            NavigationItem("perfektblue", "PerfektBlue", "perfektblue"),
            NavigationItem("smpbypass", "SMP Bypass", "smpbypass")
        )
    ),
    // Disruption (DoS) Attacks
    NavigationCategory(
        id = "disruption",
        text = "Disrupt",
        items = listOf(
            NavigationItem("bluesmack", "Smack", "bluesmack"),
            NavigationItem("blespam", "BLE Spam", "blespam"),
            NavigationItem("braktooth", "BrakTooth", "braktooth"),
            NavigationItem("gattfuzzing", "Fuzzing", "gattfuzzing")
        )
    ),
    // Utilities & Settings
    NavigationCategory(
        id = "utilities",
        text = "Utility",
        items = listOf(
            NavigationItem("attack_chaining", "Chaining", "attack_chaining"),
            NavigationItem("raw_commands", "Raw Cmds", "raw_commands"),
            NavigationItem("magisk", "Magisk", "magisk"),
            NavigationItem("settings", "Settings", "settings")
        )
    )
)
