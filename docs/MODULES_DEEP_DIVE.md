# Blu Snu: Attack Modules Deep Dive

This document details the technical implementation of the attack modules within Blu Snu. Each module is a self-contained Kotlin class in `app/src/main/java/com/hereliesaz/blusnu/data/` that exposes a `Flow<String>` API for logging, a `stop()` method for cancellation, and a `close()` method for resource release.

---

## Bluetooth Classic (BR/EDR) Modules

### BLUFFS (CVE-2023-24023) — `BluffsModule.kt`
- **Target:** Bluetooth Classic (BR/EDR).
- **CVE:** CVE-2023-24023.
- **Mechanism:** Forces a session key derivation downgrade by injecting crafted LMP Key Negotiation packets during authentication. Modes A1–A6 model different combinations of LSC and SC key request manipulation.
- **Status:** Simulated — requires InternalBlue or custom firmware for over-the-air execution.

### BrakTooth — `BrakToothModule.kt`
- **Target:** Bluetooth Classic (BR/EDR).
- **Mechanism:** LMP crash vector suite targeting SoC link managers — Feature Response flooding (`LMP_features_req_ext` barrage), malformed paging requests, and truncated LMP PDUs.
- **Status:** Simulated — requires ESP32 with custom HCI firmware for live execution.

### Breaktooth — `BreaktoothModule.kt`
- **Target:** Bluetooth Classic (BR/EDR).
- **Mechanism:** Extended BrakTooth crash vectors targeting additional LMP state-machine edge cases discovered after the original BrakTooth disclosure.
- **Status:** Simulated.

### Bluesnarfing — `BluesnarfingModule.kt`
- **Target:** Bluetooth Classic (OBEX Push / OBEX FTP).
- **Mechanism:** Connects to the target's OBEX service without authentication and requests known file paths (`telecom/pb.vcf`, `telecom/cal.vcs`, etc.).

### Bluebugging — `BluebuggingModule.kt`
- **Target:** Bluetooth Classic (RFCOMM / AT commands).
- **Mechanism:** Establishes an RFCOMM channel and injects AT commands to trigger calls, SMS, or data exfiltration. Requires the target to accept the connection.

### BlueSmack — `BlueSmackModule.kt`
- **Target:** Bluetooth Classic (L2CAP).
- **Mechanism:** Opens an L2CAP socket to the target and floods it with oversized echo request packets (> MTU) to trigger a DoS or crash in the L2CAP stack.

### BlueFrag (CVE-2020-0022) — `BlueFragModule.kt`
- **Target:** Android / Linux kernel Bluetooth stack.
- **CVE:** CVE-2020-0022.
- **Mechanism:** Injects crafted fragmented L2CAP packets that trigger a heap overflow in the kernel's packet reassembly path.
- **Status:** Simulated.

### BlueBorne — `BlueBorneModule.kt`
- **Target:** Android, Linux, Windows, iOS Bluetooth stacks.
- **CVEs:** CVE-2017-0781, CVE-2017-0782, CVE-2017-0783, CVE-2017-0785.
- **Mechanism:** Multi-vector suite — RCE via SDP information disclosure, Man-in-the-Middle via logical transport hijacking, and heap overflow via L2CAP fragmentation.
- **Status:** Simulated.

### BlueSpy (CVE-2021-43400) — `BlueSpyModule.kt`
- **Target:** Bluetooth headsets (HSP/HFP).
- **CVE:** CVE-2021-43400.
- **Mechanism:** Abuses GATT callback registration on HSP/HFP devices that do not enforce pairing before allowing audio stream access.
- **Status:** Simulated.

### BIAS (CVE-2020-10135) — `BiasModule.kt`
- **Target:** Bluetooth Classic authentication.
- **CVE:** CVE-2020-10135.
- **Mechanism:** Exploits the ability to switch master/slave roles before the mutual authentication check, allowing an attacker to authenticate as a previously bonded device without knowing the link key.
- **Status:** Simulated.

### BLUR (CVE-2020-12762) — `BlurModule.kt`
- **Target:** Embedded BLE stacks that use `json-c` or similar JSON parsers.
- **CVE:** CVE-2020-12762.
- **Mechanism:** Sends malformed advertisement fields crafted to trigger integer overflow in the JSON parser's integer handling, leading to heap corruption.
- **Status:** Simulated.

### KNOB (CVE-2019-9506) — `KnobModule.kt`
- **Target:** Bluetooth Classic key negotiation.
- **CVE:** CVE-2019-9506.
- **Mechanism:** During LMP Encryption Key Size Negotiation, proposes a 1-byte key length. Vulnerable devices accept the weak key, enabling brute-force decryption of the session.
- **Status:** Simulated.

### SweynTooth — `SweynToothModule.kt`
- **Target:** BLE SoC SDKs (Nordic, TI, Cypress, Telink, Microchip, Dialog).
- **Mechanism:** Link-layer crash vectors — sequence number mismatch, invalid `llid` field values, truncated LL control PDUs, and connection establishment state-machine abuse.
- **Status:** Simulated.

### Method Confusion — `MethodConfusionModule.kt`
- **Target:** BLE pairing.
- **Mechanism:** Responds to IO capability requests with values that force the pairing to use a weaker method (e.g., Just Works instead of Numeric Comparison), enabling a passive MitM.
- **Status:** Simulated.

### LMP Fuzzing — `LmpFuzzingModule.kt`
- **Target:** Bluetooth Classic LMP layer.
- **Mechanism:** Iterates over LMP opcode space and sends boundary-value / random payloads to identify crash-inducing state machine transitions.

---

## BLE Modules

### GATT Fuzzing — `GattFuzzingModule.kt`
- **Target:** BLE GATT server.
- **Mechanism:** Systematically writes malformed data to writable characteristics and reads from all discovered handles. Tests for buffer overflows, null dereferences, and auth-bypass conditions.

### GATT Relay (MitM) — `GattRelayModule.kt`
- **Target:** BLE GATT client/server pair.
- **Mechanism:** Two-node relay. Node A connects to the real peripheral; Node B advertises a spoofed clone and accepts connections from the legitimate central. All PDUs are forwarded over a local relay transport with RTT measurement.
- **Status:** Simulated (relay transport is local, not over-the-air).

### BLESA — `BlesaModule.kt`
- **Target:** BLE reconnection procedure.
- **Mechanism:** Exploits insufficient authentication during reconnection — impersonates a previously bonded peripheral by advertising with the bonded device's address and service data, causing the central to reconnect without re-verifying identity.
- **Status:** Simulated.

### SMP Bypass (CVE-2024-34722) — `SmpBypassModule.kt`
- **Target:** Android Bluetooth SMP implementation.
- **CVE:** CVE-2024-34722.
- **Mechanism:** Injects out-of-order `SMP_PAIRING_RANDOM` packets to skip mutual confirmation checks and complete pairing without the legitimate peer's participation.
- **Status:** Simulated.

### Injectable (InjectaBLE / CVE-2021-31615) — `InjectableModule.kt`
- **Target:** Active BLE connections.
- **CVE:** CVE-2021-31615.
- **Mechanism:** Injects malicious packets into an established BLE connection by synchronising to the connection event timing.
- **Status:** Simulated.

### L2CAP Fuzzing — `L2capFuzzingModule.kt`
- **Target:** L2CAP signalling channel (BLE and Classic).
- **Mechanism:** Sends malformed signalling PDUs — invalid CID values, truncated frames, conflicting MTU proposals, and fragmentation edge cases.

### Stealtooth — `StealtoothModule.kt`
- **Target:** BLE peripherals with rotating addresses.
- **Mechanism:** Records advertisement payload fingerprints (service UUIDs, manufacturer data, TX power) across capture windows. Correlates fingerprints across MAC rotation events to maintain persistent tracking identity.

### Screaming Channels — `ScreamingChannelsModule.kt`
- **Target:** BLE devices that couple the radio and MCU on a shared die.
- **Mechanism:** Correlates electromagnetic side-channel emissions with known BLE advertisement timing to extract cryptographic keys from the MCU.
- **Status:** Simulated — requires dedicated SDR equipment for live execution.

### Btlejacking (CVE-2018-7252) — `BtlejackingModule.kt`
- **Target:** Active BLE connections.
- **CVE:** CVE-2018-7252.
- **Mechanism:** Identifies an active BLE connection, jams it at the right moment to desynchronize the peripheral, then hijacks the session when the peripheral initiates reconnection.
- **Status:** Simulated — requires Ubertooth One or compatible hardware for live execution.

### Btlejuice (GATT Proxy) — `BtlejuiceModule.kt`
- **Target:** BLE GATT traffic.
- **Mechanism:** Acts as a transparent GATT proxy. Clones the real peripheral's service tree. All central Read/Write/Notification requests are intercepted, optionally modified, and forwarded to the real peripheral.
- **Status:** Simulated (local proxy, not over-the-air relay).

### KNOB BLE — `KnobBleModule.kt`
- **Target:** BLE LE Secure Connections key negotiation.
- **Mechanism:** BLE-specific variant of the KNOB attack — proposes a minimal key size during LE link setup.
- **Status:** Simulated.

### Passkey Reflection — `PasskeyReflectionModule.kt`
- **Target:** BLE Passkey Entry pairing.
- **Mechanism:** Reflects the passkey from one pairing session back to another — a timing-based MitM that extracts the passkey without user interaction.
- **Status:** Simulated.

### Mesh Provisioning — `MeshProvisioningModule.kt`
- **Target:** Bluetooth Mesh networks.
- **Mechanism:** Scans for unprovisioned beacon advertisements, advertises as a rogue provisioner, and models replay attacks against provisioning PDU sequences.

---

## Tracking Network Modules

### nRootTag (Apple Find My) — `NRootTagModule.kt`
- **Target:** Apple's Find My network.
- **BLE Layer:** Manufacturer data `companyId = 0x004C`, status byte = `0x12`.
- **Modes:**
  - **Scan:** Passive scan filtering on Apple Find My manufacturer data.
  - **Broadcast:** Advertises as a Find My tag with a rotating synthetic 28-byte public key payload.
  - **Track Target:** Forges Find My advertisement keys to covertly track a target device via the Apple crowdsourced network (nRootTag "Snatcher" flow).
- **Reference:** nRootTag (2024) — Milan et al.

### Find Hub Tag (Google FMDN) — `FindHubTagModule.kt`
- **Target:** Google's Find My Device / FMDN network.
- **BLE Layer:** Service UUID `0x0000FE6F-0000-1000-8000-00805F9B34FB`, 20-byte EID payload.
- **Modes:**
  - **Scan:** Passive scan filtering on the FMDN service UUID; logs EID payloads.
  - **Broadcast:** Emits rotating synthetic 20-byte EID advertisements using `addServiceData()`.

### Tile Tracker — `TileTagModule.kt`
- **Target:** Tile / Life360 tracking network.
- **BLE Layer:** Service UUID `0x0000FEED-0000-1000-8000-00805F9B34FB`, frame type `0x02`, 17-byte payload (frame type + 8-byte device ID + 8-byte nonce).
- **Modes:**
  - **Scan:** Passive scan filtering on the Tile service UUID and frame type.
  - **Broadcast:** Emits rotating 17-byte Tile-format payloads.

### BLE Tracking — `BleTrackingModule.kt`
- **Target:** BLE devices advertising with rotating MAC addresses.
- **Mechanism:** Passive capture of advertisement packets. Builds a correlation table between advertisement payloads and MAC addresses to identify stable tracking identifiers across MAC rotation events.

### BLE Whisperer — `BleWhispererModule.kt`
- **Target:** Known BLE device address.
- **Mechanism:** Continuously scans for a target MAC address. Logs RSSI history and alerts when the device enters a configurable proximity threshold. Suitable for covert presence detection and dwell-time analysis.

---

## HID & Injection Modules

### Keystroke Injection (BadKB / CVE-2023-45866) — `KeystrokeInjectionModule.kt`
- **Target:** Bluetooth-capable hosts (computers, phones).
- **CVE:** CVE-2023-45866.
- **Mechanism:** Registers the Android device as a Bluetooth HID keyboard. Attempts "Just Works" pairing with the target host. Parses DuckyScript payloads and converts them into HID usage codes delivered via the HID Input Report characteristic.
- **Root Path:** Raw L2CAP socket to bypass the Android pairing UI prompt (simulated).

### HID Controller — `HidController.kt`
- **Target:** HID-over-GATT hosts.
- **Mechanism:** Lower-level HID-over-GATT peripheral emulation — programmatic key presses, mouse movements, and media control reports without DuckyScript abstraction.

### Android BT RCE — `AndroidBtRceModule.kt`
- **Target:** Android Bluetooth stack.
- **Mechanism:** Researches and models known RCE CVEs targeting the Android Bluetooth stack, including Stagefright-era and post-2020 vulnerabilities. Each vector is encapsulated in an `AndroidRceVector` enum entry with its CVE identifier.
- **Status:** Simulated.

### Bad Bluetooth (BadBluetooth) — `BadBluetoothModule.kt`
- **Target:** Host OS Bluetooth pairing stack.
- **Mechanism:** Profile confusion — advertises as a device class that mismatches its actual capabilities (e.g., a keyboard advertising as a speaker). Probes whether the host OS handles profile mismatch gracefully or exposes unexpected behaviour.

---

## Hardware Modules

### ESP32 HCI — `Esp32HciModule.kt`
- **Interface:** USB-OTG serial (or TCP bridge).
- **Mechanism:** Sends raw HCI command packets to a connected ESP32 running custom BT firmware. Receives HCI event responses and streams them to the log. Supports arbitrary command codes via the `Esp32HciCommand` enum.

### Sniffle — `SniffleModule.kt`
- **Hardware:** TI CC2652 / CC2640R2 running Sniffle firmware.
- **Mechanism:** Initiates BLE captures, optionally provides LTK for decryption, and streams captured PDUs to the log view.

### RF Jamming — `RfJammingModule.kt`
- **Mechanism:** Drives a connected ESP32 or SDR to emit broadband 2.4 GHz noise across configurable channel masks, disrupting BLE and Wi-Fi operation in the target area.

### Battery Exhaustion — `BatteryExhaustionModule.kt`
- **Target:** BLE peripherals.
- **Mechanism:** Rapid `connect → disconnect` cycling combined with continuous GATT read storms to maximise the target device's radio duty cycle and drain its battery.

---

## Advanced Modules

### PerfektBlue (Automotive RCE) — `PerfektBlueModule.kt`
- **Target:** Automotive IVI Bluetooth stacks.
- **Mechanism:** Fuzzes AVRCP metadata strings and L2CAP PDUs with malformed values. Monitors connection health to detect stack crashes in IVI units.
- **Status:** Simulated.

### BlueTrust — `BlueTrustModule.kt`
- **Target:** BLE/Classic devices with persistent bond stores.
- **Mechanism:** Repeatedly unpairs and re-pairs with a target to probe "bond just works" and auto-reconnect policies. Can escalate trust level without user confirmation on vulnerable stacks.

### Attack Chaining — `AttackChainExecutor.kt`, `AttackChainingCanvasModule.kt`
- **Mechanism:** Executes a directed graph of attack nodes. Each node maps to a module `Flow<String>` invocation. Supports `If/Else`, `Wait`, and `Loop` logic nodes. Chains are persisted via `AttackChainTemplateRepository` (Room-backed).

### File Transfer — `FileTransferController.kt`, `FileTransferProtocol.kt`
- **Target:** Bluetooth Classic OBEX Push / OBEX FTP.
- **Mechanism:** Pushes or pulls files to/from a target device. Supports both authenticated and unauthenticated OBEX sessions.
