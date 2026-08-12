# Blu Snu: The Ultimate Offensive Bluetooth Framework

**Blu Snu** is a state-of-the-art, mobile-first offensive security framework designed to democratize and unify the assessment of Bluetooth Classic (BR/EDR) and Bluetooth Low Energy (BLE) systems. Built natively for Android using modern Jetpack Compose, it empowers security professionals to audit complex Bluetooth attack surfaces—from IoT gadgets to automotive systems—directly from their smartphone, without the need for cumbersome external laptops or specialized hardware dongles (though it supports them for advanced features).

---

## The Vision

Traditional Bluetooth security assessment is fragmented, relying on a patchwork of Linux command-line tools (BlueZ, ubertooth, gatttool) and expensive hardware. **Blu Snu** consolidates these capabilities into a single, cohesive Android application. It abstracts the complexities of the underlying protocol stacks, offering a "point-and-shoot" interface for sophisticated attacks while retaining the depth required for manual exploitation.

---

## Core Architecture

Blu Snu is built on a robust, modular architecture designed for stability and extensibility:

1. **Unified Protocol Abstraction:** A custom abstraction layer allows seamless interaction with both Bluetooth Classic and BLE targets through a single API surface.
2. **Modular Attack Engine:** Every attack is a self-contained "Module" (e.g., `BluffsModule`, `BrakToothModule`). This pluggable design ensures that new vulnerabilities (CVEs) can be integrated rapidly.
3. **Privilege-Aware Execution:** The app intelligently scales its capabilities based on the device's state:
   - **Standard Mode:** Uses public Android APIs for scanning and GATT interaction.
   - **Root Mode:** Leverages `su` access to execute low-level system commands, modify the Bluetooth stack, and perform raw HCI injection.
   - **Hardware Mode:** Interfaces with external USB dongles (via OTG) for promiscuous mode sniffing and jamming.
4. **Attack Chaining:** A visual "Canvas" allows users to link multiple attack modules into automated workflows (e.g., *Scan → Spoof MAC → Connect → Fuzz*).

> **Operational context:** Reconnaissance features (scanning, fingerprinting, GATT/SDP enumeration) run against real devices via public Android APIs. Attack modules that manipulate the Bluetooth stack require Root. Hardware modules require connected peripherals (ESP32, Ubertooth, CC26xx). See `docs/TODO.md` for per-module status.

---

## Key Features & Modules

### Reconnaissance
- **Dashboard:** Real-time overview of the wireless landscape — active devices, signal density, running tasks, saved sessions, and attack chain templates.
- **Device Management:** Detailed fingerprinting of targets: MAC address, RSSI, device class, supported profiles (SDP/GATT), and notes. Filterable and sortable.
- **Bluetooth Log:** Live, filtered log of all HCI events and application audit-trail entries.
- **Vulnerability Correlation:** Matches discovered OUI/service profiles against a bundled CVE database, updated from a remote endpoint.

### Tracking Network Research
- **nRootTag (Apple Find My):** Researches Apple's Find My network by scanning for Find My beacons (manufacturer data `0x004C`, status byte `0x12`), broadcasting synthetic Find My advertisements, or tracking a target device by forging advertisement keys (nRootTag "Snatcher" flow). See `NRootTagModule.kt`.
- **Find Hub Tag (Google FMDN):** Researches Google's Find My Device / FMDN network. Scans for beacons advertising Service UUID `0xFE6F` and extracts 20-byte EID payloads; broadcast mode emits rotating synthetic EID advertisements. See `FindHubTagModule.kt`.
- **Tile Tracker (Tile / Life360):** Researches the Tile network. Scans for beacons advertising Service UUID `0xFEED` with frame type `0x02`; broadcast mode emits 17-byte Tile-format payloads. See `TileTagModule.kt`.
- **BLE Tracking:** Passive scanner that correlates BLE advertisement MAC addresses over time to identify stable identifiers and infer device movement patterns.
- **BLE Whisperer:** Proximity-based BLE interaction — logs RSSI traces to identify when a target device is nearby, suitable for covert device presence detection.

### Impersonation & Flooding
- **BLE Spam:** Floods the environment with fake BLE advertisement packets to disrupt scanning tools and confuse users. Supports Apple, Google, Microsoft, and Samsung spam payloads. See `BleSpamModule.kt`.
- **Spoofing:** (Root Required) Changes the device's BD_ADDR and name to clone a target. Uses raw HCI commands via `su`.
- **Bad Bluetooth:** Profile confusion attacks — advertises as an unexpected device class to probe how host OSes and pairing stacks react to mismatched profiles (e.g., keyboard claiming to be a speaker).

### Classic Bluetooth (BR/EDR) Attacks
- **Bluesnarfing:** Unauthorized access to phonebooks, calendars, and messages via OBEX without authentication.
- **Bluebugging:** Hijacks a Classic device by injecting AT commands (requires elevated privileges).
- **BlueSmack:** L2CAP packet flood (DoS) — sends oversized echo request packets to crash or degrade target devices.
- **BLUFFS (CVE-2023-24023):** (Root Required — InternalBlue / custom HCI firmware) Session key downgrade via LMP Key Negotiation manipulation; bypasses forward and future secrecy.
- **BrakTooth:** (ESP32 Required) LMP crash vector suite — Feature Response flooding, paging scan crash, and truncated LMP PDUs targeting SoC stacks.
- **Breaktooth:** (ESP32 Required) Extended BrakTooth crash vectors for additional LMP state-machine edge cases.
- **BlueFrag (CVE-2020-0022):** (Root Required) Fragmented L2CAP packet injection targeting the Android/Linux kernel Bluetooth packet reassembly heap.
- **BlueBorne:** (Root Required) Multi-vector suite — RCE, MitM, and information disclosure from the 2017 BlueBorne research (CVE-2017-0781/0782/0783/0785).
- **BlueSpy (CVE-2021-43400):** (Root Required) Eavesdropping on Bluetooth audio (HSP/HFP) via GATT callback registration on unpaired headsets.
- **BIAS (CVE-2020-10135):** (Root Required) Master/slave role swap before mutual authentication, allowing impersonation of a bonded peer without the link key.
- **BLUR (CVE-2020-12762):** (Root Required) Malformed advertisement fields triggering integer overflow in embedded Bluetooth stack JSON parsers.
- **KNOB (CVE-2019-9506):** (Root Required) LMP key entropy downgrade to 1 byte during LMP Encryption Key Size Negotiation.
- **SweynTooth:** (Root Required) BLE link-layer crash vectors — sequence number mismatch, invalid LLID, truncated LL PDUs — targeting Nordic, TI, Cypress, and Telink SoC SDKs.
- **Method Confusion:** (Root Required) IO capability manipulation to force weaker pairing (Just Works instead of Numeric Comparison), enabling passive MitM.
- **LMP Fuzzing:** Fuzzes Link Manager Protocol fields with malformed values to identify crash-inducing state machine transitions.

### BLE Attacks
- **GATT Fuzzing:** Systematically tests GATT characteristics by sending malformed data, testing auth bypasses, and probing for boundary conditions.
- **GATT Relay (Tesla Attack):** Two-node GATT MitM relay — Node A connects to the real peripheral; Node B advertises a spoofed clone and relays all PDUs with RTT measurement.
- **BLESA (BLE Spoofing Attack):** (Root Required) Exploits weak reconnection procedures — impersonates a bonded peripheral to convince a central to reconnect without re-verifying identity.
- **SMP Bypass (CVE-2024-34722):** (Root Required) Out-of-order `SMP_PAIRING_RANDOM` injection to skip mutual confirmation and complete pairing without the legitimate peer.
- **Injectable (InjectaBLE / CVE-2021-31615):** (Root Required) Link-layer packet injection into an established BLE connection by synchronising to connection event timing.
- **L2CAP Fuzzing:** Fuzzes L2CAP signalling channel fields (MTU, channel identifiers, fragmentation) for BLE and Classic.
- **Stealtooth:** Persistent BLE device tracking by recording and re-correlating rotating MAC addresses using payload fingerprinting.
- **Screaming Channels:** (SDR Hardware Required) EM side-channel attack — correlates electromagnetic emissions with BLE advertisement timing to extract AES keys.
- **Btlejacking (CVE-2018-7252):** (Ubertooth Required) Jams an active BLE connection and hijacks the session during reconnection using a connected Ubertooth One or compatible sniffer.
- **Btlejuice (GATT Proxy):** Transparent GATT proxy — clones target services and characteristics, intercepts and optionally modifies all Read/Write operations.
- **KNOB BLE (CVE-2019-9506 BLE variant):** (Root Required) Proposes minimal key size during LE link setup to downgrade BLE session encryption.
- **Passkey Reflection:** (Root Required) Passkey entry MitM via timing reflection — extracts the passkey without user interaction.
- **Mesh Provisioning:** Researches Bluetooth Mesh — unprovisioned beacon scanning, rogue provisioner advertisement, and replay attacks against mesh networks.

### HID & Injection
- **Keystroke Injection (CVE-2023-45866 / "BlueDucky"):** Emulates a Bluetooth HID keyboard and attempts silent "Just Works" pairing with target hosts. DuckyScript parsing included. Root path uses raw L2CAP sockets to bypass Android's pairing UI.
- **HID Controller:** Direct HID-over-GATT peripheral emulation for controlled key injection and mouse movement payloads.
- **Android BT RCE:** Researches known remote code execution CVEs targeting the Android Bluetooth stack (Stagefright-era and post-2020).
- **Bad Bluetooth:** Profile confusion attacks using HID, A2DP, and AVRCP profile metadata to trigger unexpected host-OS behavior.

### Hardware Integration
- **ESP32 HCI:** Direct HCI command interface to a connected ESP32 running custom firmware — for raw packet injection, promiscuous sniffing, and carrier injection.
- **Sniffle:** (Hardware required) Control interface for the Sniffle BLE sniffer firmware on TI CC26xx hardware — captures and decrypts BLE connections.
- **RF Jamming:** Broadband 2.4 GHz interference modeling using the ESP32 or compatible SDR hardware.
- **Battery Exhaustion:** Rapid connection/disconnection flooding and continuous GATT read storms to drain target device batteries.

### Advanced
- **PerfektBlue (Automotive RCE):** (Root Required) Probes automotive IVI Bluetooth stacks via AVRCP metadata fuzzing and L2CAP PDU injection. Connection health monitoring detects stack crashes.
- **BlueTrust:** Models trust-escalation via repeated unpairing and re-pairing events to probe "bond just works" policies.
- **File Transfer:** Bluetooth-based file exfiltration and delivery via OBEX Push and FTP profiles.
- **Attack Chaining Canvas:** Visual node-based editor — link any combination of modules into automated multi-stage assessment workflows, save/load templates, and execute chains with live logging.
- **Geolocation (Find):** RSSI-gradient triangulation using OpenStreetMaps — Kalman-filtered distance estimation, 3-point manual fix, heatmap overlay, USB dongle secondary RSSI, and Tandem Mode for multi-device tracking.

### Utilities
- **Raw Commands:** (Root Required) Execute arbitrary shell commands directly against the Bluetooth stack.
- **Magisk Manager:** Integration with Magisk modules for kernel-level patching of the Bluetooth HAL.
- **Report Generation:** Compile all `ActionLogger` entries and session findings into Markdown and JSON reports.
- **Settings:** Database update preferences, cloud backup configuration, theme, and root-mode toggle.

---

## Ethical Use & Disclaimer

**Blu Snu is a weapon.** It is designed exclusively for:
1. **Authorized Penetration Testing:** Assessing networks you own or have explicit written permission to test.
2. **Security Research:** Academic or professional research into protocol vulnerabilities.
3. **Education:** Learning about Bluetooth security concepts in a controlled environment.

> **WARNING:** Using this tool against devices without authorization is a federal crime in many jurisdictions. The developers assume **NO LIABILITY** for misuse. You are responsible for your actions.

---

## Installation & Setup

**Prerequisites:**
- Android 10+ (Android 12+ strongly recommended for full BLE advertiser API support).
- **Root Access (Magisk)** for Spoofing, HCI injection, and raw L2CAP features.
- Location Services enabled (required by Android for Bluetooth scanning).

**Building from Source:**
```bash
git clone https://github.com/hereliesaz/blusnu.git
cd blusnu
./gradlew assembleDebug
```

**Permissions:**
Grant Location and Bluetooth permissions on first launch. For Root features, grant Superuser access when prompted by Magisk.

---

## Contributing

See `docs/workflow.md` for branch conventions, `docs/ARCHITECTURE.md` for the MVVM/module patterns, and `docs/AZNAVRAIL_COMPLETE_GUIDE.md` for nav rail integration rules.
