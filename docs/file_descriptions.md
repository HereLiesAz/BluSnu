# Blu Snu: File & Directory Guide

This document provides a detailed breakdown of the project structure, explaining the responsibility of each key file and directory.

## Root Directory

*   `README.md`: The primary entry point. Contains installation instructions, feature lists, and the ethical disclaimer.
*   `build.gradle.kts`: The root-level build script. Configures plugins and repositories for all modules.
*   `settings.gradle.kts`: Defines the project name and included modules (currently just `:app`).
*   `version.properties`: Stores the versioning metadata (Major, Minor, Patch, Build) used for CI/CD auto-incrementing.
*   `docs/`: Contains all documentation files.
*   `app/`: The main Android application module.

## `app/src/main/java/com/hereliesaz/blusnu/`

### **Core**
*   `MainActivity.kt`: The application entry point. Handles:
    *   Dependency Injection (ViewModelFactory).
    *   Navigation Graph (NavHost).
    *   Permission Requests.
    *   Root/System Checks.
    *   Global UI structure (Scaffold, AzNavRail).

### **`data/` (The Logic Layer)**
This package contains the "Brain" of the application: Attack Modules, Repositories, and System Managers.

*   **Attack Modules:**
    *   `BluffsModule.kt`: Implementation of the BLUFFS (CVE-2023-24023) attack logic.
    *   `BrakToothModule.kt`: Implementation of BrakTooth crash vectors (LMP flooding, etc.).
    *   `BluesnarfingModule.kt`: Logic for extracting phonebooks/data via OBEX.
    *   `BluebuggingModule.kt`: Logic for unauthorized call control.
    *   `BlueSmackModule.kt`: Logic for L2CAP packet flooding (DoS).
    *   `BtlejuiceModule.kt`: Logic for BLE Man-in-the-Middle (Proxying GATT).
    *   `BtlejackingModule.kt`: Logic for BLE connection hijacking.
    *   `GattFuzzingModule.kt`: Logic for fuzzing GATT characteristics.
    *   `GattRelayModule.kt`: Logic for relaying packets between two devices.
    *   `KeystrokeInjectionModule.kt`: Logic for HID emulation and DuckyScript parsing.
    *   `SpoofingModule.kt`: Logic for changing MAC addresses (requires Root).
    *   `BleSpamModule.kt`: Logic for flooding advertising packets.
    *   `SmpBypassModule.kt`: Logic for bypassing Security Manager Protocol.
    *   `PerfektBlueModule.kt`: Logic for specific stack exploits.

*   **Infrastructure & Data:**
    *   `AppDatabase.kt`: The Room Database definition.
    *   `BluetoothScanner.kt`: Wrapper for `BluetoothLeScanner`. Handles parsing scan results.
    *   `DeviceRepository.kt`: Single source of truth for `TargetDevice` data.
    *   `TargetDevice.kt`: Entity class representing a discovered Bluetooth device.
    *   `BluetoothLog.kt`: A custom logging facility for HCI/Debug events.
    *   `HardwareManager.kt`: Manages external USB/Serial hardware (if connected).
    *   `LocationManager.kt`: Handles GPS/Location updates for device triangulation.
    *   `VulnerabilityCorrelator.kt`: Matches discovered devices against CVE databases.

### **`ui/` (The Presentation Layer)**
Contains the Jetpack Compose Screens and ViewModels.

*   **Navigation:**
    *   `NavigationMenu.kt`: Defines the menu structure (Categories: Recon, Impersonation, etc.) used by AzNavRail.

*   **Feature Packages:** (Each contains a `Screen` and a `ViewModel`)
    *   `geolocation/`: The "Find" feature (Triangulation UI).
    *   `dashboard/`: The main landing page stats.
    *   `devicemanagement/`: The detailed list of devices.
    *   `spoofing/`: The MAC Spoofing UI.
    *   `bluffs/`, `braktooth/`, etc.: UIs for specific attacks.

## `app/src/main/assets/`

*   `leaflet/`: Contains the Leaflet.js library for offline maps.
*   `leaflet_map.html`: The HTML entry point for the "Find" feature's map view.
*   `vulnerabilities.json`: A local database of known Bluetooth OUI vulnerabilities.

