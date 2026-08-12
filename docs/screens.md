# Blu Snu: Screens

This document provides a description of each screen in the Blu Snu application, organized by category.

---

## Reconnaissance

### Dashboard
The main landing screen. Real-time overview of the local Bluetooth environment — nearby device count, active tasks widget, saved sessions widget, and attack chain templates widget. All data is live-updated from `ActiveTaskManager`, `DeviceRepository`, and the Room database.

### Device Management
The core reconnaissance screen. A filterable, sortable list of all discovered Bluetooth Classic and BLE devices. Each entry shows MAC address, RSSI, OUI vendor, device class, and any correlated CVEs. Tap an entry to expand to the full profile: discovered GATT services, SDP records, and notes. Formerly called "Target Management".

### Bluetooth Log
Live, filtered log of all HCI events and application audit-trail entries (`ActionLogger`). Useful for debugging, evidence collection, and step-by-step review of actions taken during an assessment.

---

## Tracking Network Research

### nRootTag (Apple Find My)
Research module for Apple's Find My network. Three modes:
- **Scan:** Passive BLE scan filtering on Apple manufacturer data with Find My status byte `0x12`.
- **Broadcast:** Advertise as a Find My tag with a rotating synthetic public key.
- **Track Target:** Forge Find My advertisement keys to covertly track a target device via the Apple network (nRootTag "Snatcher" flow).

### Find Hub Tag (Google FMDN)
Research module for Google's Find My Device / FMDN network. Two modes:
- **Scan:** Passive scan for beacons advertising Service UUID `0xFE6F`; extracts and logs 20-byte EID payloads.
- **Broadcast:** Emit rotating synthetic EID advertisements using `addServiceData()`.

### Tile Tracker (Tile / Life360)
Research module for the Tile network. Two modes:
- **Scan:** Passive scan for beacons advertising Service UUID `0xFEED` with frame type `0x02`; logs device ID and nonce fields.
- **Broadcast:** Emit 17-byte Tile-format payloads (frame type `0x02` + 8-byte device ID + 8-byte nonce).

### BLE Tracking
Passive scanner that correlates BLE advertisement MAC addresses over time to identify stable identifiers and infer device movement patterns.

### BLE Whisperer
Proximity-based BLE interaction. Logs RSSI traces for known device addresses to determine when a target is nearby — suitable for covert presence detection and dwell-time analysis.

---

## Impersonation & Flooding

### BLE Spam
Floods the environment with fake BLE advertisement packets. Supports Apple, Google, Microsoft, and Samsung spam payloads. Configurable interval and MAC rotation.

### Spoofing
(Root Required) Changes the Bluetooth adapter's BD_ADDR and device name to clone a target device. Uses raw HCI commands via `su`.

### Bad Bluetooth
Profile confusion attacks — advertises as an unexpected device class (e.g., a keyboard claiming to be a speaker) to probe how host operating systems and pairing stacks react.

---

## Classic Bluetooth (BR/EDR) Attacks

### Bluesnarfing
Unauthorized access to phonebooks, calendars, and messages via OBEX without authentication.

### Bluebugging
Hijacks a Classic device by establishing a serial connection and injecting AT commands (requires elevated privileges). Can initiate calls or exfiltrate data.

### BlueSmack
L2CAP packet flood (DoS). Sends oversized L2CAP echo requests to crash or degrade target devices. Configurable packet size and rate.

### BLUFFS (CVE-2023-24023)
(Root Required — InternalBlue / custom HCI firmware) Session key downgrade via LMP Key Negotiation parameter manipulation. Models the forward/future secrecy bypass flow across modes A1–A6.

### BrakTooth
(ESP32 Required) LMP crash vector suite targeting SoC stacks — Feature Response flooding, paging scan crash, truncated LMP PDUs. Interfaces with a connected ESP32 running custom Bluetooth firmware.

### Breaktooth
(ESP32 Required) Extended BrakTooth crash vectors targeting additional LMP state-machine edge cases beyond the original BrakTooth disclosure.

### BlueFrag (CVE-2020-0022)
(Root Required) Fragmented L2CAP packet injection triggering heap overflow in the Android/Linux kernel Bluetooth packet reassembly path.

### BlueBorne
(Root Required) Multi-vector attack suite — RCE, MitM, and information disclosure from the 2017 BlueBorne research (CVE-2017-0781/0782/0783/0785).

### BlueSpy (CVE-2021-43400)
(Root Required) Eavesdropping on Bluetooth audio (HSP/HFP) by registering GATT callbacks on headsets that do not enforce pairing before audio stream access.

### BIAS (CVE-2020-10135)
(Root Required) Bluetooth Impersonation Attack — master/slave role swap before mutual authentication, allowing impersonation of a bonded peer without the link key.

### BLUR (CVE-2020-12762)
(Root Required) Malformed Bluetooth advertisement fields triggering integer overflow in `json-c` and similar parsers used by embedded Bluetooth stacks.

### KNOB (CVE-2019-9506)
(Root Required) Key Negotiation of Bluetooth — proposes 1-byte key length during LMP Encryption Key Size Negotiation; brute-forces the session key on acceptance.

### SweynTooth
(Root Required) BLE link-layer crash vectors — sequence number mismatch, invalid LLID values, truncated LL PDUs — targeting Nordic, TI, Cypress, Telink, and Microchip SoC SDKs.

### Method Confusion
(Root Required) IO capability manipulation to force weaker pairing method (Just Works instead of Numeric Comparison), enabling passive MitM without physical access.

### LMP Fuzzing
Fuzzes Link Manager Protocol fields with random and boundary-value payloads to identify crash-inducing state machine transitions in BR/EDR stacks.

---

## BLE Attacks

### GATT Fuzzing
Systematically tests GATT characteristics by sending malformed data, testing auth bypasses, and probing for boundary conditions. Configurable target GATT server and attribute handle range.

### GATT Relay (Tesla Attack)
Two-node GATT MitM relay with RTT measurement. Node A connects to the real BLE peripheral; Node B advertises a spoofed clone and accepts connections from the legitimate central. Used to model the relay viability measurement in BLE proximity bypass research (e.g., Tesla unlock bypass).

### BLESA (BLE Spoofing Attack)
(Root Required) Exploits insufficient authentication during BLE reconnection — impersonates a bonded peripheral to convince the central to reconnect without re-verifying identity.

### SMP Bypass (CVE-2024-34722)
(Root Required) Out-of-order `SMP_PAIRING_RANDOM` injection to skip mutual confirmation and complete pairing without the legitimate peer's participation.

### Injectable (InjectaBLE / CVE-2021-31615)
(Root Required) Link-layer packet injection into an established BLE connection by synchronising to connection event timing.

### L2CAP Fuzzing
Fuzzes L2CAP signalling channel fields (MTU negotiation, channel identifiers, fragmentation header bits) for both BLE and Classic targets.

### Stealtooth
Persistent BLE device tracking by recording advertisement payload fingerprints and re-correlating rotating MAC addresses across capture windows.

### Screaming Channels
(SDR Hardware Required) EM side-channel attack — correlates electromagnetic emissions from a combined-die BLE SoC with known advertisement timing to extract AES session keys.

### Btlejacking (CVE-2018-7252)
(Ubertooth Required) Jams an active BLE connection and hijacks the session during reconnection. Interfaces with a connected Ubertooth One or compatible sniffer via the Ubertooth host tools.

### Btlejuice (GATT Proxy)
Transparent GATT proxy — clones the real peripheral's service tree, intercepts all Read/Write/Notification requests, optionally modifies values, and forwards to the real peripheral.

### KNOB BLE
(Root Required) BLE-specific KNOB attack — proposes minimal key size during LE link setup to downgrade BLE session encryption strength.

### Passkey Reflection
(Root Required) Passkey entry MitM via timing reflection — reflects the passkey from one pairing session back to another to extract it without user interaction.

### Mesh Provisioning
Researches Bluetooth Mesh networks — unprovisioned beacon scanning, rogue provisioner advertisement, and replay attacks against mesh provisioning PDUs.

---

## HID & Keystroke Injection

### Keystroke Injection (CVE-2023-45866 / "BlueDucky")
Emulates a Bluetooth HID keyboard and attempts silent "Just Works" pairing with target hosts. DuckyScript parsing is included for scripted payloads. Root path uses raw L2CAP sockets to bypass Android's pairing UI prompt.

### HID Controller
Direct HID-over-GATT peripheral emulation — controlled key injection, mouse movement payloads, and media control buttons. Lower-level than the Keystroke Injection DuckyScript path.

### Android BT RCE
Researches known remote code execution CVEs targeting the Android Bluetooth stack (Stagefright-era and post-2020 vulnerabilities).

---

## Hardware Integration

### ESP32 HCI
Direct HCI command interface to a connected ESP32 running custom firmware. Sends raw HCI commands, receives events, and can be used for packet injection, promiscuous sniffing, and carrier-level interference.

### Sniffle
(Hardware required) Control interface for the Sniffle BLE sniffer firmware on TI CC26xx hardware. Initiates captures, decrypts connections given the LTK, and streams results.

### RF Jamming
Broadband 2.4 GHz interference via the connected ESP32 or compatible SDR hardware. Configurable channel mask and burst timing.

### Battery Exhaustion
Rapid connection/disconnection flooding and continuous GATT read storms targeting BLE peripherals to drain their batteries or force a DoS through aggressive power consumption.

---

## Advanced

### PerfektBlue (Automotive RCE)
(Root Required) Probes automotive IVI Bluetooth stacks via AVRCP metadata fuzzing and L2CAP PDU injection. Connection health monitoring detects stack crashes in the IVI unit.

### BlueTrust
Models trust-escalation attacks — repeated unpairing and re-pairing events to probe "bond just works" and auto-reconnect policies on headsets, phones, and IoT devices.

### File Transfer
Bluetooth-based file exfiltration and delivery via OBEX Push and OBEX FTP profiles. Supports push-to-target and pull-from-target operations.

### Attack Chaining Canvas
Visual node-based editor for designing, saving, and executing multi-stage attack workflows. Drag-and-drop nodes representing modules and logic (If/Else, Wait, Loop), connect them to define flow, and run the chain with live log output.

---

## Utilities

### Geolocation (Find)
RSSI-gradient triangulation using an OpenStreetMaps overlay. Features: Kalman-filtered distance estimation, cooperative 3-point manual fix, device heatmap, USB dongle secondary RSSI, and Tandem Mode for multi-device collaborative tracking.

### Raw Commands
(Root Required) Execute arbitrary shell commands directly against the Bluetooth stack — `hcitool`, `bluetoothctl`, `hciconfig`, and custom HCI command sequences.

### Magisk Manager
Integration with Magisk modules for kernel-level patching of the Bluetooth HAL. Manages module installation and status.

### Report Generation
Compiles all `ActionLogger` entries and session findings into Markdown and JSON reports. Export via share sheet for off-device delivery.

### Settings
Application preferences: database update interval, cloud backup endpoint, theme selection, root-mode toggle, and advertising interval tuning.
