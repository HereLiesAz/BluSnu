# Blu Snu: UI/UX Design

The effectiveness of a complex security tool is not solely determined by its technical capabilities but also by its usability. For Blu Snu to be the "dream app" for Bluetooth penetration testers, its user interface (UI) and user experience (UX) must be meticulously designed to streamline the workflow from initial reconnaissance to final exploitation. The design philosophy prioritizes clarity, efficiency, and the intuitive visualization of complex data and attack chains, enabling the operator to make informed decisions quickly and execute actions with precision.

## Main Dashboard

Upon launching Blu Snu, the operator is greeted by a central dashboard that serves as a mission control center. This screen provides a real-time, at-a-glance summary of the local radio frequency (RF) environment and the status of ongoing operations. The dashboard is composed of several key widgets:

*   **Nearby Devices:** A dynamic counter that displays the number of unique Bluetooth devices detected in the vicinity, clearly bifurcated into Bluetooth Classic (BR/EDR) and Bluetooth Low Energy (BLE) categories. This provides an immediate sense of the target environment's density and composition.
*   **Active Tasks:** A status panel that lists any currently running processes, such as an active scan, a connection attempt, or an executing attack chain. It displays progress indicators and allows the operator to pause or terminate tasks directly from the dashboard.
*   **Saved Sessions:** A quick-access list of previously saved reconnaissance sessions and attack configurations, allowing the operator to quickly resume a previous assessment.
*   **Attack Chain Templates:** A library of pre-configured attack workflows, enabling one-tap execution of common assessment scenarios, such as a "BLE Smart Lock Audit" or a "Legacy Headset Eavesdropping" chain.

Prominently displayed on the dashboard are quick-action buttons for "Start Scan," which initiates a comprehensive multi-protocol discovery process, and "Load Session," for importing previous work.

## Target Management View

The core of the reconnaissance phase is the Target Management View. This screen presents a dynamic, filterable, and sortable list of all discovered devices. It is a significant enhancement over the simple text output of tools like `hcitool scan` or `bluetoothctl`, transforming raw data into actionable intelligence. Operators can filter the list by key parameters, including:

*   **Protocol:** BR/EDR, BLE, or Dual-Mode.
*   **Signal Strength (RSSI):** To prioritize nearby targets.
*   **Vendor:** Identified via the MAC address Organizationally Unique Identifier (OUI).
*   **Discovered Services:** To find devices running specific profiles (e.g., HID, A2DP, or a custom GATT service).

Each device entry in the list is expandable, revealing a detailed target profile. This profile aggregates all gathered information, including the device's MAC address, broadcast name, a list of advertised services (both SDP for Classic and GATT for BLE), an estimated distance based on RSSI, and, crucially, any potential vulnerabilities that have been automatically flagged by the reconnaissance module's fingerprinting engine.

## The Attack Module Interface

Once a target has been selected from the management view, the operator is navigated to the Attack Module Interface. This screen presents a context-aware arsenal of available attack modules. The application intelligently filters the list based on the target's protocol and discovered services. For example, selecting a BLE-only smart bulb will prominently feature BLE-specific attacks like "GATT Interception (Btlejuice)" and "Connection Hijack (BtleJack)," while attacks like "Bluesnarf" would be greyed out or hidden.

Each module is designed for simplicity and ease of use. It presents a clean configuration screen with clearly labeled options and sensible default values. For instance, the BlueSmack module would feature intuitive sliders to adjust the L2CAP packet size and transmission rate, with presets for "Low," "Medium," and "Aggressive" flooding. This point-and-click approach abstracts away the complex command-line syntax of the underlying tools, allowing the operator to focus on the strategic execution of the attack.

## The Attack Chaining Canvas

The signature feature of Blu Snu is the Attack Chaining Canvas, a visual, node-based editor that empowers operators to design and execute sophisticated, multi-stage attack workflows. This transforms the application from a mere toolbox into a powerful automation platform.

The canvas allows users to drag-and-drop nodes, each representing a specific action or module, and connect them to define a logical flow of execution. The interface is designed to be intuitive, automatically handling the passing of data between connected nodes. For example, the MAC Address output of a "Scan (BLE)" node can be visually connected to the Target MAC input of a "Connection Hijack (BtleJack)" node.

A practical example of a workflow built on the canvas might be:

1.  **Start Node:** Initiates the chain.
2.  **Scan (BLE) Node:** Scans for BLE devices in the vicinity.
3.  **Filter by Service UUID Node:** Takes the list of discovered devices and filters it to find only those advertising a specific GATT service UUID corresponding to a known smart lock model.
4.  **Connection Hijack (BtleJack) Node:** Takes the filtered device's MAC address and attempts to perform a Btlejacking attack to take over the connection.
5.  **GATT Write Node:** If the hijack is successful, this node is triggered. It is pre-configured with the specific handle and payload (0x01) required to send the "unlock" command to the compromised lock.

This visual paradigm for attack automation allows for the creation of highly customized and intelligent assessment routines that can be saved, shared, and re-used, dramatically increasing the efficiency and repeatability of penetration tests.
