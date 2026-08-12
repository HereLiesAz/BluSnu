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
(Root Required — Simulated) Models changing the Bluetooth adapter's BD_ADDR and device name to clone a target. The current implementation simulates the root HCI commands without altering the adapter's hardware address.

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
(Simulated) Demonstrates session key downgrade via LMP parameter manipulation. Models the forward/future secrecy bypass flow (modes A1–A6). Requires root for real execution; current build simulates the HCI patching.

### BrakTooth
(Simulated) LMP crash vectors targeting SoC stacks — Feature Response flooding, paging scan crash, and others. Requires an ESP32 for live execution; current build simulates the hardware interface and packet injection.

### Breaktooth
(Simulated) Extended BrakTooth crash vectors targeting additional LMP state-machine edge cases.

### BlueFrag (CVE-2020-0022)
(Simulated) Models fragmented L2CAP packet injection targeting Android and Linux kernel vulnerabilities in Bluetooth packet reassembly.

### BlueBorne
(Simulated) Models the suite of remote code execution, information disclosure, and MitM vectors from the BlueBorne 2017 research (CVE-2017-0781/0782/0783/0785).

### BlueSpy (CVE-2021-43400)
(Simulated) Models eavesdropping on Bluetooth audio (HSP/HFP) by abusing GATT callback registration without a pairing requirement.

### BIAS (CVE-2020-10135)
(Simulated) Bluetooth Impersonation Attacks — models role switching and master identity spoofing during the authentication phase to bypass mutual verification.

### BLUR (CVE-2020-12762)
(Simulated) JSON library memory corruption triggered via malformed Bluetooth advertisement fields, targeting embedded stacks that parse advertisement metadata with vulnerable JSON parsers.

### KNOB (CVE-2019-9506)
(Simulated) Key Negotiation of Bluetooth — forces session key entropy to 1 byte during LMP negotiation, enabling practical brute-force decryption of captured traffic.

### SweynTooth
(Simulated) A family of BLE link-layer crash vectors (sequence number mismatch, invalid length, LL connection, etc.) targeting Nordic Semiconductor, Texas Instruments, Cypress, and other SoC SDKs.

### Method Confusion
(Simulated) Forces pairing to use a weaker authentication method by manipulating IO capability exchange frames.

### LMP Fuzzing
Fuzzes Link Manager Protocol fields with random and boundary-value payloads to identify crash-inducing state machine transitions in BR/EDR stacks.

---

## BLE Attacks

### GATT Fuzzing
Systematically tests GATT characteristics by sending malformed data, testing auth bypasses, and probing for boundary conditions. Configurable target GATT server and attribute handle range.

### GATT Relay (Tesla Attack)
(Simulated) Man-in-the-Middle relay — Node A connects to the real BLE peripheral; Node B advertises a spoofed clone. Models the relay viability measurement used in BLE proximity bypass research (e.g., Tesla unlock bypass).

### BLESA (BLE Spoofing Attack)
(Simulated) Models exploiting weak reconnection procedures to inject forged advertisements and convince a bonded central to reconnect to an impostor peripheral.

### SMP Bypass (CVE-2024-34722)
(Simulated) Injects out-of-order `SMP_PAIRING_RANDOM` packets to bypass Security Manager Protocol pairing requirements.

### Injectable (InjectaBLE / CVE-2021-31615)
(Simulated) Models malicious packet injection into an established BLE connection at the link layer — targeting the connection state machine rather than the application layer.

### L2CAP Fuzzing
Fuzzes L2CAP signalling channel fields (MTU negotiation, channel identifiers, fragmentation header bits) for both BLE and Classic targets.

### Stealtooth
Persistent BLE device tracking by recording advertisement payload fingerprints and re-correlating rotating MAC addresses across capture windows.

### Screaming Channels
(Simulated) Side-channel attack — models extracting AES keys from BLE devices by correlating electromagnetic emissions with known advertisement timing.

### Btlejacking (CVE-2018-7252)
(Simulated — hardware required) Jams an existing BLE connection and hijacks the session during reconnection. Requires an external Ubertooth One or compatible radio for live execution; current build simulates the hardware command interface.

### Btlejuice (GATT Proxy)
(Simulated) Transparent GATT proxy — clones target services and characteristics, intercepts Read/Write operations, and optionally modifies values in transit.

### KNOB BLE
BLE-variant of the KNOB attack targeting the BLE-specific key negotiation path.

### Passkey Reflection
(Simulated) Models the passkey entry MitM attack — reflects a Just Works pairing event to extract the passkey via timing side-channels.

### Mesh Provisioning
Researches Bluetooth Mesh networks — unprovisioned beacon scanning, rogue provisioner advertisement, and replay attacks against mesh provisioning PDUs.

---

## HID & Keystroke Injection

### Keystroke Injection (CVE-2023-45866 / "BlueDucky")
Emulates a Bluetooth HID keyboard and attempts silent "Just Works" pairing with target hosts. DuckyScript parsing is included for scripted payloads. The raw L2CAP socket path (for bypassing pairing prompts without UI interaction) is simulated for root users.

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
(Simulated) Probes automotive IVI Bluetooth stacks via AVRCP and L2CAP fuzzing with malformed metadata strings. Connection health monitoring detects crashes in the target stack.

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
