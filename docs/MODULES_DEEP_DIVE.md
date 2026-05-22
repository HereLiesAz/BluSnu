# Blu Snu: Attack Modules Deep Dive

This document details the technical implementation of the attack modules within Blu Snu. Each module is designed to target specific layers of the Bluetooth protocol stack (Baseband, LMP, L2CAP, GATT, SMP).

*Note: This documentation describes the implementation logic found in `app/src/main/java/com/hereliesaz/blusnu/data/`.*

## 1. BLUFFS (Bluetooth Forward and Future Secrecy)
*   **Target:** Bluetooth Classic (BR/EDR).
*   **CVE:** CVE-2023-24023.
*   **Mechanism:** The module attempts to force a downgrade of the session key derivation process. It manipulates the Key Negotiation (KMP) by injecting specifically crafted LMP packets during the pairing process.
*   **Implementation:** See `BluffsModule.kt`.

## 2. BrakTooth
*   **Target:** Bluetooth Classic (BR/EDR).
*   **Mechanism:** A collection of crash vectors targeting the Link Manager Protocol (LMP).
*   **Vectors Implemented:**
    *   **Feature Response Flooding:** Sends a barrage of `LMP_features_req_ext` packets to overflow the target's LMP handler stack.
    *   **Paging Scan Crash:** Malformed paging requests.
*   **Implementation:** See `BrakToothModule.kt`.

## 3. Bluesnarfing
*   **Target:** Bluetooth Classic (OBEX).
*   **Mechanism:** Connects to the target's OBEX Push or FTP service without authentication (or using default PINs) to request known file paths (e.g., `telecom/pb.vcf` for Phonebook).
*   **Implementation:** See `BluesnarfingModule.kt`.

## 4. GATT Relay (Man-in-the-Middle)
*   **Target:** Bluetooth Low Energy (BLE).
*   **Mechanism:**
    *   **Node A (Phone):** Connects to the real peripheral.
    *   **Node B (Car/PC):** Advertises a spoofed peripheral.
    *   **Relay:** Packets received on Node B are forwarded to Node A, and responses from Node A are sent back to Node B.
*   **Implementation:** See `GattRelayModule.kt`.

## 5. Btlejuice (GATT Proxy)
*   **Target:** Bluetooth Low Energy (BLE).
*   **Mechanism:** Acts as a transparent proxy. It spins up a dummy GATT server that clones the target's services and characteristics. When a Central connects to the proxy, the proxy connects to the real target and forwards all Read/Write operations.
*   **Implementation:** See `BtlejuiceModule.kt`.

## 6. MAC Spoofing
*   **Target:** Controller / Link Layer.
*   **Requirement:** Root Access.
*   **Mechanism:** Uses `su` commands to invoke `bdaddr` or vendor-specific HCI commands (e.g., `hcitool cmd`) to reprogram the Bluetooth Controller's BD_ADDR.
*   **Implementation:** See `SpoofingModule.kt`.

## 7. Keystroke Injection (BadKB)
*   **Target:** HID over GATT (HOGP).
*   **Mechanism:** The Android device registers itself as a Bluetooth HID Keyboard. It then parses a DuckyScript payload and converts it into HID usage codes sent over the Input Report characteristic.
*   **Implementation:** See `KeystrokeInjectionModule.kt` and `DuckyScriptParser.kt`.
