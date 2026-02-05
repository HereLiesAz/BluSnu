# Blu Snu: The Ultimate Offensive Bluetooth Framework

**Blu Snu** is a state-of-the-art, mobile-first offensive security framework designed to democratize and unify the assessment of Bluetooth Classic (BR/EDR) and Bluetooth Low Energy (BLE) systems. Built natively for Android using modern Jetpack Compose, it empowers security professionals to audit complex Bluetooth attack surfaces—from IoT gadgets to automotive systems—directly from their smartphone, without the need for cumbersome external laptops or specialized hardware dongles (though it supports them for advanced features).

## 🚀 The Vision

Traditional Bluetooth security assessment is fragmented, relying on a patchwork of Linux command-line tools (BlueZ, ubertooth, gatttool) and expensive hardware. **Blu Snu** consolidates these capabilities into a single, cohesive Android application. It abstracts the complexities of the underlying protocol stacks, offering a "point-and-shoot" interface for sophisticated attacks while retaining the depth required for manual exploitation.

---

## 🏗 Core Architecture

Blu Snu is built on a robust, modular architecture designed for stability and extensibility:

1.  **Unified Protocol Abstraction:** A custom abstraction layer allows seamless interaction with both Bluetooth Classic and BLE targets through a single API surface.
2.  **Modular Attack Engine:** Every attack is a self-contained "Module" (e.g., `BluffsModule`, `BrakToothModule`). This pluggable design ensures that new vulnerabilities (CVEs) can be integrated rapidly.
3.  **Privilege-Aware Execution:** The app intelligently scales its capabilities based on the device's state:
    *   **Standard Mode:** Uses public Android APIs for scanning and GATT interaction.
    *   **Root Mode:** leverages `su` access to execute low-level system commands, modify the Bluetooth stack, and perform raw HCI injection.
    *   **Hardware Mode:** Interfaces with external USB dongles (via OTG) for promiscuous mode sniffing and jamming.
4.  **Attack Chaining:** A visual "Canvas" allows users to link multiple attack modules into automated workflows (e.g., *Scan -> Spoof MAC -> Connect -> Fuzz*).

---

## 🛡 Key Features & Modules

Blu Snu is organized into tactical categories found in the navigation rail:

### 1. Reconnaissance
*   **Dashboard:** Real-time overview of the wireless landscape, tracking active devices and signal density.
*   **Device Management:** Detailed fingerprinting of targets, including MAC address, signal strength (RSSI), device class, and supported profiles (SDP/GATT).
*   **Bluetooth Log:** A live, filtered log of all HCI events and application actions for debugging and analysis.

### 2. Impersonation
*   **Spoofing:** (Root Required) Change your device's BD_ADDR (MAC address) and device name to clone a target device.
*   **Gatt Relay:** Act as a Man-in-the-Middle (MitM) relay between a peripheral and a central device, forwarding and logging GATT packets.

### 3. Exploitation (Classic & BLE)
*   **BLUFFS (CVE-2023-24023):** Demonstrates the "Bluetooth Forward and Future Secrecy" attacks to compromise session keys.
*   **BrakTooth:** A suite of crash-inducing PoCs targeting various SoC stacks (LMP flooding, paging scans).
*   **Bluesnarfing:** Unauthorized access to information (phonebooks, calendars) from a Bluetooth-enabled device.
*   **Bluebugging:** Taking control of the target device to make calls or listen in.
*   **BlueSmack:** A Denial of Service (DoS) attack using L2CAP packet flooding.
*   **Btlejuice / MitM:** A full proxy for intercepting and modifying BLE traffic.
*   **Btlejacking:** Jamming an existing BLE connection and hijacking the session during reconnection.
*   **Gatt Fuzzing:** Automated fuzzing of GATT characteristics to identify buffer overflows or logic errors.
*   **Keystroke Injection (BadKB):** Emulate a Bluetooth keyboard to inject malicious keystrokes (DuckyScript support included).
*   **SMP Bypass:** Techniques to bypass Security Manager Protocol pairing requirements.
*   **PerfektBlue:** Exploits specific implementation flaws in popular BLE stacks.

### 4. Disruption
*   **BLE Spam:** Floods the environment with fake advertisement packets to disrupt scanning tools and confuse users.

### 5. Utilities
*   **Find (Geolocation):** Uses a fuzzy-logic RSSI gradient and device rotation to triangulate the physical location of a target device.
*   **Raw Commands:** (Root Required) Execute arbitrary shell commands directly against the Bluetooth stack.
*   **Magisk Manager:** Integration with Magisk modules for kernel-level patching.
*   **Report Generation:** Compile all findings into professional JSON/PDF reports.

---

## ⚠️ Ethical Use & Disclaimer

**Blu Snu is a weapon.** It is designed exclusively for:
1.  **Authorized Penetration Testing:** Assessing networks you own or have explicit written permission to test.
2.  **Security Research:** Academic or professional research into protocol vulnerabilities.
3.  **Education:** Learning about Bluetooth security concepts in a controlled environment.

> **WARNING:** Using this tool against devices without authorization is a federal crime in many jurisdictions. The developers assume **NO LIABILITY** for misuse. You are responsible for your actions.

---

## 🔧 Installation & Setup

1.  **Prerequisites:**
    *   Android Device (Android 10+ recommended).
    *   **Root Access (Magisk)** is strongly recommended for full functionality (Spoofing, HCI injection).
    *   Location Services enabled (required for Bluetooth Scanning on Android).

2.  **Building from Source:**
    ```bash
    git clone https://github.com/hereliesaz/blusnu.git
    cd blusnu
    ./gradlew assembleDebug
    ```

3.  **Permissions:**
    Upon first launch, grant Location and Bluetooth permissions. For Root features, grant Superuser access when prompted by Magisk.

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for our style guides (AzNavRail enforcement, MVVM patterns) and code of conduct.

