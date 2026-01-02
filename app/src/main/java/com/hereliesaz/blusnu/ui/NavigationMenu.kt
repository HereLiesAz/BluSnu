package com.hereliesaz.blusnu.ui

data class NavigationCategory(
    val id: String,
    val text: String,
    val items: List<NavigationItem>
)

data class NavigationItem(
    val id: String,
    val text: String,
    val route: String
)

val MENU_CATEGORIES = listOf(
    NavigationCategory(
        id = "recon",
        text = "Recon",
        items = listOf(
            NavigationItem("dashboard", "Dashboard", "dashboard"),
            NavigationItem("targets", "Targets", "targets"),
            NavigationItem("geolocation", "Location", "geolocation"),
            NavigationItem("reporting", "Reporting", "reporting"),
            NavigationItem("bluetooth_log", "Log", "bluetooth_log")
        )
    ),
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
