# Blu Snu Project: Task List

This document outlines the development tasks required to implement the Blu Snu framework, as detailed in the conceptual blueprint. The project is broken down into modules and milestones.

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

## Milestone 5: Advanced Signal and Pairing Attacks

-   [x] **Task 5.1: Device Geolocation Module**
    -   [x] Implement baseline distance estimation using the log-distance path loss model.
    -   [x] Implement a Kalman filter or moving average to smooth RSSI readings.
    -   [x] Design the "Radar" view UI.
-   [x] **Task 5.2: Keystroke Injection (CVE-2023-45866) Module**
    -   [x] Implement the logic to emulate an HID keyboard and attempt "Just Works" pairing.
    -   [x] Create the UI to send keystroke commands if the attack is successful.
-   [x] **Task 5.3: Bluetooth Spoofing Module**
    -   [x] Implement the logic to change the Bluetooth adapter's MAC address. (Requires Elevated Mode)
    -   [x] Create the UI to allow the user to specify a new MAC address.

## Milestone 6: Automation Core and Finalization

-   [x] **Task 6.1: Attack Chaining Canvas**
    -   [x] Design and implement the visual node-based editor.
    -   [x] Create the initial set of nodes for all implemented modules and logic (If/Else, Wait, Loop).
    -   [x] Implement the data flow logic between connected nodes.
-   [x] **Task 6.2: Pre-built Attack Chain Templates**
    -   [x] Implement the logic to load and save attack chains.
    -   [x] Create the initial set of templates (e.g., "BLE Smart Lock Audit," "Opportunistic Eavesdropping").
-   [ ] **Task 6.3: Professional Reporting Engine**
    -   [ ] Implement the logic to log all actions performed during an assessment.
    -   [ ] Create the functionality to generate and export a detailed report in PDF or Markdown format.
-   [ ] **Task 6.4: Finalize Settings and Database Updates**
    -   [ ] Create a settings screen for user preferences.
    -   [ ] Implement the mechanism for securely downloading updates to the vulnerability and fingerprinting databases.

## Future Enhancements

-   [ ] **Task 7.1: Persistent Device Storage**
    -   [ ] Implement a database (e.g., Room) to automatically save all discovered devices, persisting them across app launches.
-   [ ] **Task 7.2: Encounter Logging and Frequency Analysis**
    -   [ ] Log every time a device is encountered during a scan, including timestamps.
    -   [ ] Add UI options to sort the device list by most and least frequently encountered.
-   [ ] **Task 7.3: Device Encounter Heatmap**
    -   [ ] Design and implement a "heatmap" visualization for each device, showing encounter times and frequency.
-   [ ] **Task 7.4: Device Type Identification Engine**
    -   [ ] Implement a system to make an "educated guess" at the device type (e.g., "headphones," "smartwatch," "car") based on its name, services, and other scan data.