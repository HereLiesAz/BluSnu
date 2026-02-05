# Blu Snu: File Descriptions

This document provides an exhaustive description of every source file in the Blu Snu project.

## Root Directory (`app/src/main/java/com/hereliesaz/blusnu/`)

*   `MainActivity.kt`: The single Activity entry point for the application, hosting the Navigation component and global state initialization.

## Data Layer (`app/src/main/java/com/hereliesaz/blusnu/data/`)

### Core Models & Database
*   `ActiveTask.kt`: Represents a currently running background task (scan, attack) for UI status display.
*   `ActionLogger.kt`: Handles logging of user actions and attack results for the final report.
*   `AppDatabase.kt`: Room Database definition, defining entities and DAOs.
*   `BluetoothLog.kt`: Data class representing a raw Bluetooth log entry (HCI/Logcat).
*   `Converters.kt`: Type converters for Room database storage.
*   `DatabaseUpdater.kt`: Utility for handling database migrations and updates.
*   `DeviceRepository.kt`: Repository pattern implementation for accessing `TargetDevice` data.
*   `TargetDevice.kt`: The central data entity representing a discovered Bluetooth device (Classic or BLE).
*   `TargetDeviceDao.kt`: Data Access Object for `TargetDevice` database operations.
*   `SavedSession.kt`: Entity representing a saved workspace/session.
*   `SavedSessionDao.kt`: DAO for `SavedSession`.
*   `SavedSessionRepository.kt`: Repository for session management.

### Scanning & Reconnaissance
*   `BluetoothScanner.kt`: Manages Bluetooth adapter scanning (Classic Discovery & BLE Scanning).
*   `MacLookupClient.kt`: Client for resolving OUI/MAC addresses to vendor names.
*   `VulnerabilityCorrelator.kt`: Logic to cross-reference discovered devices with known CVEs.

### Attack Modules (Implementation)
*   `BleSpamModule.kt`: Implements BLE advertisement spamming logic.
*   `BlueSmackModule.kt`: Implements L2CAP Echo Request flooding (DoS).
*   `BluebuggingModule.kt`: Implements AT command injection attacks.
*   `BluesnarfingModule.kt`: Implements OBEX data exfiltration attacks.
*   `BluffsModule.kt`: Implements the BLUFFS attack suite (CVE-2023-24023).
*   `BrakToothModule.kt`: Implements BrakTooth crash vectors.
*   `BtlejackingModule.kt`: Implements jamming and hijacking logic (requires hardware).
*   `BtlejuiceModule.kt`: Implements GATT proxying and interception.
*   `GattFuzzingModule.kt`: Implements automated fuzzing of GATT characteristics.
*   `GattRelayModule.kt`: Implements relay attacks between devices.
*   `KeystrokeInjectionModule.kt`: Implements HID keystroke injection (BlueDucky).
*   `MitmAttack.kt`: Base logic or helper for Man-in-the-Middle attacks.
*   `PerfektBlueModule.kt`: Implements specific exploit vectors (placeholder/specifics).
*   `SmpBypassModule.kt`: Implements Security Manager Protocol bypass techniques.
*   `SpoofingModule.kt`: Implements MAC and name spoofing using root commands.

### Attack Chaining
*   `AttackChainExecutor.kt`: Engine that executes a defined chain of attack nodes.
*   `AttackChainRepository.kt`: Repository for managing active chains.
*   `AttackChainTemplate.kt`: Entity representing a saved attack workflow template.
*   `AttackChainTemplateDao.kt`: DAO for templates.
*   `AttackChainTemplateRepository.kt`: Repository for accessing templates.
*   `AttackChainTemplates.kt`: Pre-defined templates (factory/seeder).
*   `AttackChainingCanvasModule.kt`: DI module or helper for the canvas logic.

### Managers & Geolocation
*   `CloudBackup.kt`: Logic for backing up data to cloud storage (if implemented).
*   `CompassManager.kt`: Manages device compass/orientation sensors for direction finding.
*   `CooperativeTriangulation.kt`: Math logic for intersecting signals from multiple devices.
*   `DeviceWithLocation.kt`: Helper model combining device data with location fixes.
*   `GeolocationModule.kt`: Core logic for RSSI-based distance estimation and positioning.
*   `HardwareManager.kt`: Manages external hardware (dongles, SDRs).
*   `LocationManager.kt`: Wrapper around Android Location Services (GPS).
*   `TandemManager.kt`: Manages p2p connection for cooperative attacks (Tandem Mode).

### Utilities (Data)
*   `DuckyScriptParser.kt`: Parses DuckyScript text into injectable key events.

## Utilities Layer (`app/src/main/java/com/hereliesaz/blusnu/utils/`)

*   `RootExecutor.kt`: Singleton/Utility for executing shell commands as root.
*   `Trilateration.kt`: Mathematical functions for 2D trilateration.

## UI Layer (`app/src/main/java/com/hereliesaz/blusnu/ui/`)

### Core UI
*   `FilterProtocol.kt`: Enum/Logic for filtering devices by protocol (BLE/Classic).
*   `NavigationMenu.kt`: Defines the app's navigation drawer structure and routes.
*   `SortOption.kt`: Enum for device list sorting options.

### Feature Packages
*   `attackchaining/`:
    *   `AttackChainingScreen.kt`: The visual node editor screen.
    *   `AttackChainingViewModel.kt`: Logic for the editor.
    *   `Node.kt`: UI model for a node.
    *   `nodes/AttackNode.kt`: Specific node implementation for attacks.
*   `blespam/`: `BleSpamScreen.kt`, `BleSpamViewModel.kt`.
*   `bluebugging/`: `BluebuggingScreen.kt`, `BluebuggingViewModel.kt`.
*   `bluesmack/`: `BlueSmackScreen.kt`, `BlueSmackViewModel.kt`.
*   `bluesnarfing/`: `BluesnarfingScreen.kt`, `BluesnarfingViewModel.kt`.
*   `bluetoothlog/`: `BluetoothLogScreen.kt`, `BluetoothLogViewModel.kt`.
*   `bluffs/`: `BluffsScreen.kt`, `BluffsViewModel.kt`.
*   `braktooth/`: `BrakToothScreen.kt`, `BrakToothViewModel.kt`.
*   `btlejacking/`: `BtlejackingScreen.kt`, `BtlejackingViewModel.kt`.
*   `btlejuice/`: `BtlejuiceScreen.kt`, `BtlejuiceViewModel.kt`.
*   `btlejuicemitm/`: `BtlejuiceMitmScreen.kt`, `BtlejuiceMitmViewModel.kt`.
*   `dashboard/`: `DashboardScreen.kt`, `DashboardState.kt`, `DashboardViewModel.kt`.
*   `devicemanagement/`: `DeviceManagementScreen.kt`, `DeviceManagementViewModel.kt`.
*   `gattfuzzing/`: `GattFuzzingScreen.kt`, `GattFuzzingViewModel.kt`.
*   `gattrelay/`: `GattRelayScreen.kt`, `GattRelayViewModel.kt`.
*   `geolocation/`: `FindScreen.kt`, `FindViewModel.kt`.
*   `keystrokeinjection/`: `KeystrokeInjectionScreen.kt`, `KeystrokeInjectionViewModel.kt`.
*   `magisk/`: `MagiskScreen.kt`, `MagiskViewModel.kt`.
*   `perfektblue/`: `PerfektBlueScreen.kt`, `PerfektBlueViewModel.kt`.
*   `rawcommands/`: `RawCommandsScreen.kt`, `RawCommandsViewModel.kt`.
*   `report/`: `ReportScreen.kt`, `ReportViewModel.kt`.
*   `settings/`: `SettingsScreen.kt`, `SettingsViewModel.kt`.
*   `smpbypass/`: `SmpBypassScreen.kt`, `SmpBypassViewModel.kt`.
*   `spoofing/`: `SpoofingScreen.kt`, `SpoofingViewModel.kt`.

### Components & Theme
*   `components/`: `DisclaimerDialog.kt`, `LeafletMapView.kt`, `ProgressDialog.kt`, `ScreenTitle.kt`, `SystemRequirementsDialog.kt`, `Title.kt`.
*   `theme/`: `Color.kt`, `Theme.kt`, `Type.kt`.
