# Blu Snu Project: Task List

This document outlines the development tasks required to implement the Blu Snu framework, as detailed in the conceptual blueprint and "The Modern Wireless Arsenal" report. The project is broken down into modules and milestones.

## Milestone 1: Project Setup and Core Framework

-   [x] **Task 1.1: Initialize Android Project**
    -   [x] Set up a new Android project with the package name `com.hereliesaz.blusnu`.
    -   [x] Configure required permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `ACCESS_FINE_LOCATION`, `INTERNET`, `WRITE_EXTERNAL_STORAGE`) in `AndroidManifest.xml`.
    -   [x] Implement runtime permission handling for all required permissions.
-   [x] **Task 1.2: Implement Core UI Shell**
    -   [x] Create the main dashboard layout with placeholders for key widgets (Nearby Devices, Active Tasks, etc.).
    -   [x] Set up navigation between the main dashboard and placeholder views for Target Management, Attack Modules, and Settings.
-   [x] **Task 1.3: Implement Unified Protocol Abstraction Layer (Initial)**
    -   [x] Define a `TargetDevice` data class to hold information for both Classic and BLE devices (MAC, name, RSSI, protocol type, etc.).
    -   [x] Create a repository or service class to manage the list of discovered devices.
-   [x] **Task 1.4: Implement Ethical Use Disclaimer**
    -   [x] Create a non-skippable disclaimer dialog that appears on the first launch of the application.

## Milestone 2: Reconnaissance Module

-   [x] **Task 2.1: Multi-Protocol Device Discovery**
    -   [x] Implement Bluetooth Classic (BR/EDR) discovery using `BluetoothAdapter.startDiscovery()`.
    -   [x] Implement Bluetooth Low Energy (BLE) scanning using `BluetoothLeScanner`.
    -   [x] Populate the Target Management View with discovered devices in real-time.
-   [x] **Task 2.2: Implement Target Management UI**
    -   [x] Design and implement the filterable and sortable list for discovered devices.
    -   [x] Implement filtering logic for protocol, RSSI, and vendor.
    -   [x] Replaced `TargetManagementScreen` with `DeviceManagementScreen` to allow for adding notes to devices.
-   [x] **Task 2.3: Service Enumeration**
    -   [x] Implement SDP enumeration for Classic devices (`fetchUuidsWithSdp()`).
    -   [x] Implement GATT service discovery for BLE devices (`bluetoothGatt.discoverServices()`).
    -   [x] Display discovered services in the expandable target profile view.
-   [x] **Task 2.4: Device Fingerprinting and Vulnerability Correlation**
    -   [x] Create an initial internal database (e.g., in SQLite or as a bundled JSON file) for device fingerprints and known CVEs.
    -   [x] Implement the fingerprinting engine to match discovered services/UUIDs against the database.
    -   [x] Implement the vulnerability correlation engine to query the database and flag vulnerable devices in the UI.

## Milestone 3: Bluetooth Classic (BR/EDR) Attack Modules

-   [x] **Task 3.1: Bluesnarfing Module**
    -   [x] Create the UI for the Bluesnarfing attack.
    -   [x] Implement the logic to connect to a target's OBEX service and retrieve data (e.g., phonebook).
-   [x] **Task 3.2: Bluebugging Module**
    -   [x] Create the UI for the Bluebugging attack.
    -   [x] Implement the logic to establish a serial connection and inject AT commands. (Requires Elevated Mode)
-   [x] **Task 3.3: BlueSmack (L2CAP Flood) Module**
    -   [x] Create the UI for the L2CAP flood attack with controls for packet size and rate.
    -   [x] Implement the logic to open an L2CAP socket and send oversized echo requests. (Requires Elevated Mode)
-   [x] **Task 3.4: BLUFFS (CVE-2023-24023) Module**
    -   [x] Create UI for BLUFFS attack configuration (Modes A1-A6).
    -   [x] Implement root detection and InternalBlue patching requirements (Simulated).
    -   [x] Implement logic to manipulate LMP parameters (Key Size, Nonces) (Simulated).
    -   [x] Implement vulnerability check (Connection accepts 1-byte key) (Simulated).
-   [x] **Task 3.5: BrakTooth Module**
    -   [x] Create UI for BrakTooth fuzzing control.
    -   [x] Implement external hardware interface (ESP32 via USB-OTG) (Simulated).
    -   [x] Implement LMP packet injection for specific crash vectors (Simulated).

## Milestone 4: Bluetooth Low Energy (BLE) Attack Modules

-   [x] **Task 4.1: GATT Fuzzing Module**
    -   [x] Create the UI for the GATT fuzzer.
    -   [x] Implement the logic to systematically test GATT characteristics (malformed data, auth bypass, etc.).
-   [x] **Task 4.2: Btlejacking Module (Hardware-Assisted)**
    -   [x] Implement the Hardware Manager to detect and communicate with a connected BtleJack device.
    -   [x] Create the UI to control sniffing, jamming, and hijacking operations.
    -   [x] Implement the command interface to send instructions to the external hardware.
-   [x] **Task 4.3: Man-in-the-Middle (btlejuice) Module**
    -   [x] Extend the Hardware Manager to support a second external USB BLE dongle.
    -   [x] Create the UI to display and modify intercepted GATT traffic in real-time.
    -   [x] Implement the core proxy logic.
-   [x] **Task 4.4: BLE Spam (Advertisement Flooding) Module**
    -   [x] Create UI to select payload types (Apple, Google, Microsoft).
    -   [x] Implement `BluetoothLeAdvertiser` logic to send spoofed packets.
    -   [x] Implement MAC address rotation and high-frequency advertising.
-   [x] **Task 4.5: GATT Relay (Tesla Attack) Module**
    -   [x] Create UI for Node A (Car Side) and Node B (Phone Side).
    -   [x] Implement WebSocket/MQTT relay between two Android devices (Simulated).
    -   [x] Implement RTT measurement to verify relay viability (Simulated).

## Milestone 5: Advanced Signal and Pairing Attacks

-   [x] **Task 5.1: Device Geolocation Module**
    -   [x] Implement baseline distance estimation using the log-distance path loss model.
    -   [x] Implement a Kalman filter or moving average to smooth RSSI readings.
    -   [x] Design the "Map" view UI.
-   [x] **Task 5.2: Keystroke Injection (CVE-2023-45866 / "BlueDucky") Module**
    -   [x] Implement the logic to emulate an HID keyboard and attempt "Just Works" pairing.
    -   [x] Create the UI to send keystroke commands if the attack is successful.
    -   [x] Add DuckyScript parsing support.
    -   [x] Implement raw L2CAP socket method for root users (bypass Android API pairing prompts) (Simulated).
-   [x] **Task 5.3: Bluetooth Spoofing Module**
    -   [x] Implement the logic to change the Bluetooth adapter's MAC address. (Requires Elevated Mode)
    -   [x] Create the UI to allow the user to specify a new MAC address.
-   [x] **Task 5.4: PerfektBlue (Automotive RCE) Module**
    -   [x] Create UI for Automotive IVI auditing.
    -   [x] Implement AVRCP/L2CAP fuzzing logic with malformed metadata (Simulated).
    -   [x] Implement connection health monitoring to detect crashes (Simulated).
-   [x] **Task 5.5: Android SMP Bypass (CVE-2024-34722) Module**
    -   [x] Create UI for SMP Bypass testing.
    -   [x] Implement logic to inject out-of-order `SMP_PAIRING_RANDOM` packets (Simulated).

## Milestone 6: Automation Core and Finalization

-   [x] **Task 6.1: Attack Chaining Canvas**
    -   [x] Design and implement the visual node-based editor.
    -   [x] Create the initial set of nodes for all implemented modules and logic (If/Else, Wait, Loop).
    -   [x] Implement the data flow logic between connected nodes (Implemented via Executor Context).
-   [x] **Task 6.2: Pre-built Attack Chain Templates**
    -   [x] Implement the logic to load and save attack chains.
    -   [x] Create the initial set of templates (e.g., "Simple Scan," "Snarf and Inject").
-   [x] **Task 6.3: Professional Reporting Engine**
    -   [x] Implement the logic to log all actions performed during an assessment.
    -   [x] Create the functionality to generate and export a detailed report in PDF or Markdown format.
-   [x] **Task 6.4: Finalize Settings and Database Updates**
    -   [x] Create a settings screen for user preferences.
    -   [x] Implement the mechanism for securely downloading updates to the vulnerability and fingerprinting databases.

## Milestone 7: UI/UX Overhaul and Feature Expansion

-   [x] **Task 7.1: Global UI Consistency**
    -   [x] Remove all hardcoded screen titles from individual composables.
    -   [x] Implement a global layout rule: all screen content must have a top margin of 20% of the screen height.
    -   [x] Implement a global layout rule: all screen content must be right-aligned.
-   [x] **Task 7.2: Advanced Geolocation with Map**
    -   [x] Replace the "Radar" view with a real map using OpenStreetMaps.
    -   [x] Implement a device triangulation algorithm that improves in accuracy as the user moves their phone.
    -   [x] Add a heatmap widget to the Dashboard showing where devices have been found in scans.
    -   [x] Rename "Reporting" to "Report".
    -   [x] Implement manual 3-point triangulation logic.
    -   [x] Add support for USB Dongle (Secondary RSSI).
    -   [x] Add support for Tandem Mode (Multi-device tracking).
-   [x] **Task 7.3: Dashboard and Data Persistence**
    -   [x] The vulnerability database should be loaded automatically on app open, not manually.
    -   [x] Remove the "Load Session" button from the Dashboard.
    -   [x] Implement the "Active Tasks", "Saved Sessions", and "Attack Chain Templates" widgets to display real data.
    -   [x] Devices found in previous scans should be listed on the Targets screen upon app open.
    -   [x] When scanning, newly discovered devices should have their text colored with the primary blue.
-   [x] **Task 7.4: Implement Placeholder Screens**
    -   [x] Implement the UI and basic functionality for the Bluebugging screen.
    -   [x] Implement the UI and basic functionality for the Bluesnarfing screen.
    -   [x] Implement the UI and basic functionality for the BlueSmack screen.
    -   [x] Implement the UI and basic functionality for the GATT Fuzzing screen.
    -   [x] Implement the UI and basic functionality for the Btlejacking screen.
    -   [x] Implement the UI and basic functionality for the Attack Chaining screen.
    -   [x] Implement the UI and basic functionality for Keystroke Injection (BlueDucky).
    -   [x] Implement the UI and basic functionality for Spoofing.
    -   [x] Implement the UI and basic functionality for Magisk/Root.
    -   [x] Implement the UI and basic functionality for Raw Commands.
    -   [x] Implement the UI and basic functionality for BLUFFS.
    -   [x] Implement the UI and basic functionality for BrakTooth.
    -   [x] Implement the UI and basic functionality for BLE Spam.
    -   [x] Implement the UI and basic functionality for GATT Relay.
    -   [x] Implement the UI and basic functionality for PerfektBlue.
    -   [x] Implement the UI and basic functionality for SMP Bypass.
-   [x] **Task 7.5: Data Sharing and Analytics**
    -   [x] Add a sentence to the ethical use prompt asking the user to share anonymized data with the developer.
    -   [x] Add "Agree" and "Cancel" buttons to the new prompt.
    -   [x] If the user agrees, implement functionality to back up their local database to a secure cloud database.

## Future Research
- https://github.com/francozappa/blur
- https://github.com/francozappa/bias
- https://github.com/francozappa/bluff
- https://github.com/francozappa/sniffle
- https://github.com/francozappa/knob
