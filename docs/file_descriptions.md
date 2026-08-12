# Blu Snu: File & Directory Guide

This document provides a detailed breakdown of the project structure, explaining the responsibility of each key file and directory.

---

## Root Directory

- `README.md`: Primary entry point — feature list, installation instructions, ethical disclaimer.
- `build.gradle.kts`: Root-level build script. Configures plugins and repositories for all modules.
- `settings.gradle.kts`: Defines project name and included modules (`:app`).
- `version.properties`: Versioning metadata (Major, Minor, Patch, Build) for CI/CD auto-increment.
- `AGENTS_SETUP.md`: Instructions for setting up automated agent environments (`./setup_env.sh`).
- `setup_env.sh`: Environment bootstrap script used by CI/CD and agent runners.
- `docs/`: All project documentation (see below).
- `app/`: The main Android application module.
- `magisk/`: Magisk module definitions for kernel-level Bluetooth patching.

---

## `app/src/main/java/com/hereliesaz/blusnu/`

### Core

- `MainActivity.kt`: Application entry point. Responsible for:
  - Manual dependency injection via `ViewModelProvider.Factory`.
  - NavHost navigation graph (all routes and composable registrations).
  - Runtime permission requests (Bluetooth, Location).
  - Root / system capability checks.
  - Global UI structure (Scaffold, AzNavRail).

### `data/` — The Logic Layer

All attack modules expose a `Flow<String>` API for live log output, a `stop()` for cancellation, and a `close()` for resource release.

#### Tracking Network Modules
- `NRootTagModule.kt`: Apple Find My network research — scan, broadcast, and track-target modes.
- `FindHubTagModule.kt`: Google FMDN / Find Hub network research — scan and broadcast modes (Service UUID `0xFE6F`).
- `TileTagModule.kt`: Tile / Life360 network research — scan and broadcast modes (Service UUID `0xFEED`).
- `BleTrackingModule.kt`: Passive BLE advertisement MAC correlation for persistent device tracking.
- `BleWhispererModule.kt`: Proximity presence detection via RSSI trace logging.

#### Classic Bluetooth Attack Modules
- `BluffsModule.kt`: BLUFFS (CVE-2023-24023) — session key downgrade via LMP parameter manipulation.
- `BrakToothModule.kt`: BrakTooth — LMP crash vectors (Feature Response flooding, paging scan crash).
- `BreaktoothModule.kt`: Extended BrakTooth crash vectors for additional LMP edge cases.
- `BluesnarfingModule.kt`: OBEX-based data extraction (phonebook, calendar) without authentication.
- `BluebuggingModule.kt`: AT command injection via RFCOMM.
- `BlueSmackModule.kt`: L2CAP flood (DoS) via oversized echo request packets.
- `BlueFragModule.kt`: CVE-2020-0022 — L2CAP fragmented packet injection (Root Required).
- `BlueBorneModule.kt`: BlueBorne CVE suite — RCE, MitM, information disclosure (Root Required).
- `BlueSpyModule.kt`: CVE-2021-43400 — HSP/HFP audio eavesdropping without pairing (Root Required).
- `BiasModule.kt`: CVE-2020-10135 — master/slave role swap authentication bypass (Root Required).
- `BlurModule.kt`: CVE-2020-12762 — malformed advertisement triggering JSON parser heap overflow (Root Required).
- `KnobModule.kt`: CVE-2019-9506 — LMP key entropy downgrade to 1 byte (Root Required).
- `SweynToothModule.kt`: BLE link-layer crash vector suite targeting SoC SDKs (Root Required).
- `MethodConfusionModule.kt`: IO capability manipulation to force weaker pairing method (Root Required).
- `LmpFuzzingModule.kt`: LMP opcode space fuzzer with boundary-value and random payloads.

#### BLE Attack Modules
- `GattFuzzingModule.kt`: Systematic GATT characteristic fuzzing (malformed data, auth bypass, boundary conditions).
- `GattRelayModule.kt`: Two-node GATT MitM relay with RTT measurement (Root Required).
- `BlesaModule.kt`: BLE reconnection spoofing — impersonates a bonded peripheral (Root Required).
- `SmpBypassModule.kt`: CVE-2024-34722 — out-of-order SMP_PAIRING_RANDOM injection (Root Required).
- `InjectableModule.kt`: CVE-2021-31615 / InjectaBLE — link-layer packet injection into active connections (Root Required).
- `L2capFuzzingModule.kt`: L2CAP signalling channel fuzzer (MTU, CID, fragmentation edge cases).
- `StealtoothModule.kt`: Persistent BLE tracking via advertisement payload fingerprinting across MAC rotations.
- `ScreamingChannelsModule.kt`: EM side-channel AES key extraction (Root Required).
- `BtlejackingModule.kt`: CVE-2018-7252 — BLE connection jam and hijack (Ubertooth Required).
- `BtlejuiceModule.kt`: Transparent GATT proxy — clone, intercept, and optionally modify BLE traffic (Root Required).
- `KnobBleModule.kt`: BLE-specific KNOB attack on LE key negotiation (Root Required).
- `PasskeyReflectionModule.kt`: Passkey entry MitM via timing reflection (Root Required).
- `MeshProvisioningModule.kt`: Bluetooth Mesh rogue provisioner and replay attack research.

#### HID & Injection Modules
- `KeystrokeInjectionModule.kt`: CVE-2023-45866 / BlueDucky — HID keyboard emulation with DuckyScript parsing.
- `HidController.kt`: Lower-level HID-over-GATT peripheral emulation.
- `BleHidController.kt`: BLE-specific HID controller (reports, descriptors).
- `HidKeyMap.kt`: HID usage code lookup table for DuckyScript translation.
- `AndroidBtRceModule.kt`: Android Bluetooth stack RCE CVE research module.
- `BadBluetoothModule.kt`: Profile confusion attacks via mismatched device class advertisements.
- `DuckyScriptParser.kt`: Parses DuckyScript files into `HidKeyEvent` sequences.

#### Hardware Modules
- `Esp32HciModule.kt`: Direct HCI command interface for a connected ESP32 — raw injection, sniffing, carrier interference.
- `SniffleModule.kt`: Sniffle BLE sniffer control interface for TI CC26xx hardware.
- `RfJammingModule.kt`: 2.4 GHz broadband jamming via ESP32 or SDR.
- `BatteryExhaustionModule.kt`: Rapid connect/disconnect flooding and GATT read storm for battery drain.

#### Advanced & Persistence Modules
- `PerfektBlueModule.kt`: Automotive IVI Bluetooth stack auditing via AVRCP/L2CAP fuzzing (Root Required).
- `BlueTrustModule.kt`: Trust escalation via repeated pairing event probing.
- `AttackChainExecutor.kt`: Executes a directed graph of attack nodes using `Flow<String>` primitives.
- `AttackChainingCanvasModule.kt`: Visual canvas state model for the node-based editor.
- `AttackChainTemplate.kt`, `AttackChainTemplates.kt`: Pre-built chain definitions.
- `AttackChainTemplateDao.kt`, `AttackChainTemplateRepository.kt`: Room persistence for user-saved chains.
- `AttackChainRepository.kt`: Runtime state for the active chain execution.
- `FileTransferController.kt`, `FileTransferProtocol.kt`: OBEX Push/FTP file exfiltration and delivery.

#### Impersonation & Spam Modules
- `BleSpamModule.kt`: Advertisement flooding — Apple, Google, Microsoft, Samsung payloads.
- `SpoofingModule.kt`: BD_ADDR and device name spoofing (Root Required).
- `MitmAttack.kt`: Generic MitM scaffolding shared by relay-type modules.

#### Reconnaissance & Infrastructure
- `BluetoothScanner.kt`: Wrapper for `BluetoothLeScanner` and Classic discovery. Parses scan results.
- `BluetoothLog.kt`: Custom HCI/application event logger; feeds the Bluetooth Log screen.
- `DeviceRepository.kt`: Single source of truth for `TargetDevice` data (Room + in-memory).
- `TargetDevice.kt`, `TargetDeviceDao.kt`: Room entity and DAO for discovered devices.
- `DeviceWithLocation.kt`: Join entity — device + GPS fix recorded at discovery time.
- `AppDatabase.kt`: Room database definition. Registers all DAOs and migration paths.
- `Converters.kt`: Room TypeConverters (e.g., `List<String>` ↔ JSON).
- `VulnerabilityCorrelator.kt`: Matches OUI/service profiles against the bundled CVE database.
- `DatabaseUpdater.kt`: Downloads updated `vulnerabilities.json` from a remote endpoint.
- `MacLookupClient.kt`: Resolves OUI prefixes to vendor names.
- `HardwareManager.kt`: Detects and manages external USB/Serial hardware (Ubertooth, ESP32).
- `GeolocationModule.kt`: RSSI-based distance estimation, Kalman filter, and 3-point triangulation.
- `CooperativeTriangulation.kt`: Multi-device collaborative triangulation (Tandem Mode).
- `TandemManager.kt`: Manages Tandem Mode peer sessions over local network.
- `CompassManager.kt`: Sensor fusion for compass heading, used in Geolocation tracking.
- `LocationManager.kt`: GPS/Network location updates for triangulation fixes.
- `ActionLogger.kt`: Singleton audit-trail logger. All module start/stop/result events are logged here and surfaced in the Report screen.
- `ActiveTask.kt`, `ActiveTaskManager.kt`: Track currently running module tasks; feeds the Dashboard "Active Tasks" widget.
- `SavedSession.kt`, `SavedSessionDao.kt`, `SavedSessionRepository.kt`: Room entities for persisting and reloading assessment sessions.
- `CloudBackup.kt`: Uploads session data to a configured cloud endpoint (no-op without a configured URL).

### `ui/` — The Presentation Layer

Each feature package contains a `*Screen.kt` (Composable) and a `*ViewModel.kt` (AndroidViewModel).

#### Tracking Networks
- `nroottag/`: nRootTag Apple Find My screen and ViewModel.
- `findhubtag/`: Google FMDN / Find Hub screen and ViewModel.
- `tiletracker/`: Tile / Life360 screen and ViewModel.
- `bletracking/`: BLE Tracking screen and ViewModel.
- `blewhisperer/`: BLE Whisperer screen and ViewModel.

#### Classic Bluetooth
- `bluffs/`, `braktooth/`, `breaktooth/`, `bluefrag/`, `blueborne/`, `bluespy/`, `bias/`, `blur/`, `knob/`, `sweyntooth/`, `methodconfusion/`, `lmpfuzzing/`: Classic attack screens and ViewModels.
- `bluesnarfing/`, `bluebugging/`, `bluesmack/`: Core Classic attack UIs.

#### BLE Attacks
- `gattfuzzing/`, `gattrelay/`, `blesa/`, `smpbypass/`, `injectable/`, `l2capfuzzing/`, `stealtooth/`, `screamingchannels/`, `btlejacking/`, `btlejuice/`, `knobble/`, `passkeyreflection/`, `meshprovisioning/`: BLE attack screens and ViewModels.

#### HID & Injection
- `keystrokeinjection/`: DuckyScript HID keyboard injection UI.
- `hid/`: Low-level HID controller UI.
- `androidbtrce/`: Android BT RCE research screen.
- `badbluetooth/`: Profile confusion attack screen.
- `batteryexhaustion/`: Battery drain attack screen.

#### Hardware
- `esp32hci/`: ESP32 HCI command terminal.
- `sniffle/`: Sniffle BLE sniffer control UI.
- `rfjamming/`: RF jamming control UI.

#### Impersonation
- `blespam/`: BLE advertisement spam UI.
- `spoofing/`: MAC address spoofing UI.

#### Advanced
- `attackchaining/`: Visual node-based attack chain canvas and template browser.
- `perfektblue/`: Automotive stack auditing UI.
- `bluetrust/`: Trust escalation research UI.
- `filetransfer/`: OBEX file transfer UI.

#### Utilities
- `geolocation/`: RSSI triangulation + OpenStreetMaps overlay.
- `rawcommands/`: Root shell command terminal.
- `magisk/`: Magisk module manager UI.
- `reporting/`: Report generation and export UI.
- `settings/`: Application preferences.
- `dashboard/`: Main landing screen with live stats widgets.
- `devicemanagement/`: Filterable/sortable target device list.
- `bluetoothlog/`: HCI event and audit-trail log viewer.

#### Shared
- `components/`: Reusable Compose components — `ResultActions` (copy/share log output), etc.
- `theme/`: Material 3 colour schemes, typography, and shape definitions.
- `FilterProtocol.kt`, `SortOption.kt`: Enum types for Device Management filter/sort controls.

---

## `app/src/main/assets/`

- `leaflet/`: Offline Leaflet.js library for the Geolocation map view.
- `leaflet_map.html`: HTML entry point for the Geolocation WebView map.
- `vulnerabilities.json`: Bundled CVE / OUI vulnerability database (updated via `DatabaseUpdater`).
