# Blu Snu Project: Task List

This document outlines the development tasks required to implement the Blu Snu framework, as detailed in the conceptual blueprint. The project is broken down into modules and milestones.

## Milestone 1: Project Setup and Core Framework

-   [X] **Task 1.1: Initialize Android Project**
    -   [X] Set up a new Android project with the package name `com.hereliesaz.blusnu`.
    -   [X] Configure required permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `ACCESS_FINE_LOCATION`, `INTERNET`, `WRITE_EXTERNAL_STORAGE`) in `AndroidManifest.xml`.
    -   [X] Implement runtime permission handling for all required permissions.
-   [X] **Task 1.2: Implement Core UI Shell**
    -   [X] Create the main dashboard layout with placeholders for key widgets (Nearby Devices, Active Tasks, etc.).
    -   [X] Set up navigation between the main dashboard and placeholder views for Target Management, Attack Modules, and Settings.
-   [X] **Task 1.3: Implement Unified Protocol Abstraction Layer (Initial)**
    -   [X] Define a `TargetDevice` data class to hold information for both Classic and BLE devices (MAC, name, RSSI, protocol type, etc.).
    -   [X] Create a repository or service class to manage the list of discovered devices.
-   [X] **Task 1.4: Implement Ethical Use Disclaimer**
    -   [X] Create a non-skippable disclaimer dialog that appears on the first launch of the application.

## Milestone 2: Reconnaissance Module

-   [X] **Task 2.1: Multi-Protocol Device Discovery**
    -   [X] Implement Bluetooth Classic (BR/EDR) discovery using `BluetoothAdapter.startDiscovery()`.
    -   [X] Implement Bluetooth Low Energy (BLE) scanning using `BluetoothLeScanner`.
    -   [X] Populate the Target Management View with discovered devices in real-time.
-   [X] **Task 2.2: Implement Target Management UI**
    -   [X] Design and implement the filterable and sortable list for discovered devices.
    -   [X] Implement filtering logic for protocol, RSSI, and vendor.
-   [ ] **Task 2.3: Service Enumeration**
    -   [ ] Implement SDP enumeration for Classic devices (`fetchUuidsWithSdp()`).
    -   [ ] Implement GATT service discovery for BLE devices (`bluetoothGatt.discoverServices()`).
    -   [ ] Display discovered services in the expandable target profile view.
-   [ ] **Task 2.4: Device Fingerprinting and Vulnerability Correlation**
    -   [ ] Create an initial internal database (e.g., in SQLite or as a bundled JSON file) for device fingerprints and known CVEs.
    -   [ ] Implement the fingerprinting engine to match discovered services/UUIDs against the database.
    -   [ ] Implement the vulnerability correlation engine to query the database and flag vulnerable devices in the UI.

## Milestone 3: Bluetooth Classic (BR/EDR) Attack Modules

-   [ ] **Task 3.1: Bluesnarfing Module**
    -   [ ] Create the UI for the Bluesnarfing attack.
    -   [ ] Implement the logic to connect to a target's OBEX service and retrieve data (e.g., phonebook).
-   [ ] **Task 3.2: Bluebugging Module**
    -   [ ] Create the UI for the Bluebugging attack.
    -   [ ] Implement the logic to establish a serial connection and inject AT commands. (Requires Elevated Mode)
-   [ ] **Task 3.3: BlueSmack (L2CAP Flood) Module**
    -   [ ] Create the UI for the L2CAP flood attack with controls for packet size and rate.
    -   [ ] Implement the logic to open an L2CAP socket and send oversized echo requests. (Requires Elevated Mode)

## Milestone 4: Bluetooth Low Energy (BLE) Attack Modules

-   [ ] **Task 4.1: GATT Fuzzing Module**
    -   [ ] Create the UI for the GATT fuzzer.
    -   [ ] Implement the logic to systematically test GATT characteristics (malformed data, auth bypass, etc.).
-   [ ] **Task 4.2: Btlejacking Module (Hardware-Assisted)**
    -   [ ] Implement the Hardware Manager to detect and communicate with a connected BtleJack device.
    -   [ ] Create the UI to control sniffing, jamming, and hijacking operations.
    -   [ ] Implement the command interface to send instructions to the external hardware.
-   [ ] **Task 4.3: Man-in-the-Middle (btlejuice) Module**
    -   [ ] Extend the Hardware Manager to support a second external USB BLE dongle.
    -   [ ] Create the UI to display and modify intercepted GATT traffic in real-time.
    -   [ ] Implement the core proxy logic.

## Milestone 5: Advanced Signal and Pairing Attacks

-   [ ] **Task 5.1: Device Geolocation Module**
    -   [ ] Implement baseline distance estimation using the log-distance path loss model.
    -   [ ] Implement a Kalman filter or moving average to smooth RSSI readings.
    -   [ ] Design the "Radar" view UI.
-   [ ] **Task 5.2: Keystroke Injection (CVE-2023-45866) Module**
    -   [ ] Implement the logic to emulate an HID keyboard and attempt "Just Works" pairing.
    -   [ ] Create the UI to send keystroke commands if the attack is successful.

## Milestone 6: Automation Core and Finalization

-   [ ] **Task 6.1: Attack Chaining Canvas**
    -   [ ] Design and implement the visual node-based editor.
    -   [ ] Create the initial set of nodes for all implemented modules and logic (If/Else, Wait, Loop).
    -   [ ] Implement the data flow logic between connected nodes.
-   [ ] **Task 6.2: Pre-built Attack Chain Templates**
    -   [ ] Implement the logic to load and save attack chains.
    -   [ ] Create the initial set of templates (e.g., "BLE Smart Lock Audit," "Opportunistic Eavesdropping").
-   [ ] **Task 6.3: Professional Reporting Engine**
    -   [ ] Implement the logic to log all actions performed during an assessment.
    -   [ ] Create the functionality to generate and export a detailed report in PDF or Markdown format.
-   [ ] **Task 6.4: Finalize Settings and Database Updates**
    -   [ ] Create a settings screen for user preferences.
    -   [ ] Implement the mechanism for securely downloading updates to the vulnerability and fingerprinting databases.