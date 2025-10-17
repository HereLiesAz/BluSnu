

# **Blu Snu: A Conceptual Blueprint for an Integrated Offensive Bluetooth Security Framework**

## **The Blu Snu Vision: An Integrated Architecture for Bluetooth Offensive Security**

The proliferation of Bluetooth-enabled devices, spanning from personal electronics and Internet of Things (IoT) sensors to critical automotive and medical systems, has created a vast and complex attack surface. Security assessment of these systems is often fragmented, relying on a disparate collection of command-line tools, specialized hardware, and platform-specific scripts, primarily designed for Linux-based environments. The Blu Snu project is conceived to address this fragmentation by providing a unified, mobile-first platform for comprehensive Bluetooth security auditing. This document outlines the conceptual blueprint for Blu Snu (com.hereliesaz.blusnu), an Android application designed to automate and simplify sophisticated penetration testing methodologies for both Bluetooth Classic (BR/EDR) and Bluetooth Low Energy (BLE) protocols. The core philosophy is to abstract the underlying complexities of hardware, protocol stacks, and execution environments, presenting the security professional with a powerful, intuitive, and integrated offensive security framework.

The architecture of Blu Snu is founded on a set of core principles designed to ensure modularity, scalability, and maximum operational capability across a range of Android device configurations. These principles are essential to overcoming the inherent limitations of the mobile environment while delivering functionality comparable to, and in some cases exceeding, that of traditional desktop-based tools.

### **Core Architectural Principles**

The Blu Snu framework is designed around four foundational architectural tenets: Unified Protocol Abstraction, a Modular Attack Framework, a sophisticated Orchestration and Automation Engine, and Privilege-Aware Functionality. Together, these principles enable the creation of a versatile and powerful security assessment tool.

* **Unified Protocol Abstraction:** At the heart of the Blu Snu architecture lies a core library designed to provide a consistent Application Programming Interface (API) for interacting with both Bluetooth Classic and BLE targets. The Bluetooth specification is notoriously complex, with significant differences between the BR/EDR and LE physical and logical layers.1 Classic Bluetooth relies on the Service Discovery Protocol (SDP) for service enumeration over RFCOMM channels, while BLE utilizes the Generic Attribute Profile (GATT) for discovering services and characteristics.2 The abstraction layer will manage these protocol-specific details—including connection establishment, service discovery, and data transfer mechanisms—and present a unified "Target Device" object to the higher-level attack modules. This design simplifies the development of attack modules, allowing them to focus on exploit logic rather than protocol implementation nuances.  
* **Modular Attack Framework:** The application will be architected as a collection of discrete, pluggable modules. Each specific attack vector, such as Bluesnarfing 4, Btlejacking 5, or the BLUFFS attack suite 6, will be implemented as a self-contained module. This modularity offers significant advantages: it facilitates the rapid integration of new exploits and vulnerabilities as they are discovered by the security community, allows for independent updates to individual modules without requiring a full application rebuild, and enables the user to load only the necessary modules for a given assessment, optimizing performance and resource usage.  
* **Orchestration and Automation Engine:** A central "Automation Core" will serve as the brain of the application, managing the execution of individual attack modules and, critically, enabling the chaining of multiple techniques into complex, automated attack workflows. This engine will be responsible for state management, passing the output from one module (e.g., a list of discovered MAC addresses from a reconnaissance module) as the input to a subsequent module (e.g., a connection bruteforcer or a targeted DoS attack). This capability transforms the application from a simple collection of tools into a true offensive framework, allowing testers to build and execute sophisticated, multi-stage attack scenarios with ease.  
* **Privilege-Aware Functionality:** Recognizing the diverse landscape of Android devices and user permissions, the Blu Snu architecture will operate in a tiered functional model. This ensures that the application provides maximum utility regardless of the device's configuration.  
  * **Standard Mode (Non-Root):** This baseline mode will operate within the confines of the standard Android Bluetooth APIs.7 Functionality will include device scanning, GATT and SDP enumeration, and the implementation of any attacks that can be achieved through the official, non-privileged API calls.  
  * **Elevated Mode (Root Access):** On rooted devices, the application will request and leverage superuser privileges to interact more directly with the underlying Bluetooth stack. This may involve deploying custom-compiled versions of essential Linux utilities from the BlueZ stack (such as hcitool, btmgmt, l2ping) 9, directly manipulating configuration files, or using native code to perform low-level packet injection. This mode unlocks a significantly more powerful class of attacks, particularly those targeting implementation flaws in the host stack.  
  * **Hardware-Assisted Mode:** For the most advanced and physically demanding attacks—such as comprehensive connection sniffing, precise frequency-hopping jamming, and reliable connection hijacking—the application will interface with external, specialized hardware. This model is inspired by frameworks like BtleJack, which utilizes one or more BBC Micro:Bit devices to overcome the limitations of a single radio and perform tasks like sniffing all three BLE advertising channels simultaneously.5 Blu Snu will support a range of low-cost, compatible hardware (e.g., nRF51822 or nRF52 series development boards), offloading the most difficult tasks to dedicated peripherals while the Android device serves as the command-and-control console.

The following table provides a high-level overview of the core attack modules planned for the Blu Snu framework, categorized by their primary function and target protocol.

| Attack Class | Technique Name | Target Protocol | Description | Primary Research Source/CVE |
| :---- | :---- | :---- | :---- | :---- |
| **Information Theft** | Bluesnarfing | Classic (BR/EDR) | Exploits OBEX protocol vulnerabilities to steal data such as phonebooks, call logs, and messages from legacy devices. | bluesnarfer 4 |
| **Remote Control** | Bluebugging | Classic (BR/EDR) | Exploits firmware flaws in older devices to gain control via AT commands, enabling actions like call initiation and message sending. | Bluebugger 11 |
| **Eavesdropping** | BlueSpy Audio Interception | Classic (BR/EDR) | Connects to audio devices with insecure pairing to record microphone input without the user's awareness. | BlueSpy 13 |
| **Denial of Service** | BlueSmack (L2CAP Flood) | Classic (BR/EDR) | Overwhelms a target device with oversized L2CAP echo request packets, causing its Bluetooth stack to crash or become unresponsive. | blue-smack-attack 14 |
| **Denial of Service** | LMP/Baseband Fuzzing | Classic (BR/EDR) | Sends malformed Link Manager Protocol (LMP) packets to trigger firmware-level vulnerabilities and cause a denial of service. | BlueToolkit 16 |
| **Session Hijacking** | BLUFFS Attack Suite | Classic (BR/EDR) | A set of six attacks exploiting architectural flaws in session key derivation to enable device impersonation and MitM across sessions. | bluffs 6, CVE-2023-24023 17 |
| **Man-in-the-Middle** | GATT Proxy (Btlejuice) | BLE | Intercepts and manipulates GATT traffic between a central and a peripheral device, allowing on-the-fly data modification. | btlejuice 18 |
| **Connection Hijacking** | Btlejacking | BLE | Jams an existing BLE connection to force a disconnect, then immediately connects to the peripheral to take over the session. | BtleJack 5, CVE-2018-7252 20 |
| **Connection Bypass** | Keystroke Injection | Classic/BLE | Exploits flaws in the pairing process (e.g., "Just Works") to pair as a keyboard without user confirmation and inject keystrokes. | CVE-2023-45866 21 |
| **Pairing Attack** | Stealtooth | Classic/BLE | Abuses vulnerabilities in automatic pairing functions to silently overwrite a device's link key, enabling future MitM attacks. | Stealtooth Research 22 |

### **The Android Implementation Challenge**

The single greatest technical obstacle in realizing the Blu Snu vision is the translation of powerful, low-level functionalities from the Linux ecosystem to the more restrictive, API-driven environment of the Android operating system. The vast majority of cutting-edge Bluetooth security research tools, including BlueSpy 13, BlueToolkit 23, and bluffs 6, are developed for Linux and fundamentally rely on the direct, granular control afforded by the BlueZ Bluetooth stack.9 These tools assume the ability to execute commands like hcitool for inquiry scanning, btmgmt for modifying local adapter settings, and, most critically, the ability to open raw L2CAP or HCI sockets for crafting and injecting custom packets.

The Android Bluetooth stack, by contrast, is intentionally sandboxed to enhance security and stability for the end-user. It exposes Bluetooth functionality to applications through a curated set of high-level Java and Kotlin APIs, such as BluetoothAdapter, BluetoothLeScanner, and BluetoothGatt.2 Direct access to the underlying hardware, the HCI layer, or the host controller's firmware is strictly prohibited for unprivileged applications. This architectural difference makes a direct port of existing Linux-based tools impossible and necessitates a more nuanced, multi-pronged implementation strategy.

To overcome this challenge, the Blu Snu architecture is designed to dynamically adapt its capabilities based on the available resources and permissions. This approach ensures that the application remains functional and useful for all users, while unlocking its full potential for security professionals with advanced device configurations.

1. **API-First Implementation:** The foundation of the application will be a re-implementation of core attack logic using the official Android Bluetooth APIs wherever feasible. For instance, device discovery will utilize the BluetoothLeScanner for BLE and the BluetoothAdapter.startDiscovery() method for Classic devices. Service enumeration will be performed using the BluetoothGatt class for BLE and the BluetoothDevice.fetchUuidsWithSdp() method for Classic.2 This approach forms the basis of the "Standard Mode," providing a robust set of reconnaissance and high-level interaction tools that can run on any modern Android device.  
2. **Native Development Kit (NDK) and Root Privileges:** For attacks that require lower-level control than the standard APIs provide—such as crafting malformed packets for DoS attacks as cataloged by BlueToolkit 16 or manipulating pairing parameters—the application will leverage the Android Native Development Kit (NDK). This allows for the compilation and execution of C/C++ code, including ports of the core logic from existing Linux tools. However, executing this native code with the necessary permissions to interact directly with the Bluetooth driver, manipulate system configuration files, or open privileged sockets will require root access. In "Elevated Mode," Blu Snu will use these privileges to bridge the gap between the Android and Linux environments, unlocking a significant portion of the advanced attack modules.  
3. **External Hardware Offloading:** Certain functions are physically impossible for a standard smartphone's single-radio chipset to perform effectively. A prime example is sniffing an in-progress BLE connection, which requires knowledge of the connection parameters (access address, channel map, etc.) that are not exposed to the sniffing device. Similarly, sniffing for new connections requires rapidly scanning all three primary advertising channels (37, 38, and 39), a task for which a single radio is ill-suited. To address this, the "Hardware-Assisted Mode" will offload these tasks to dedicated external hardware. Following the model established by BtleJack 5, Blu Snu will act as a sophisticated controller for one or more low-cost nRF51- or nRF52-based boards. The Android application will handle the user interface, target selection, and attack orchestration, while issuing high-level commands to the external hardware to perform the low-level radio operations like multi-channel sniffing, precise jamming, and connection hijacking.

This tiered architecture allows Blu Snu to dynamically scale its functionality. Upon launch, it will detect the device's root status and scan for connected external hardware, enabling or disabling specific attack modules in the UI accordingly. This ensures a seamless user experience, presenting the most powerful available toolset to the security professional while remaining a useful reconnaissance tool for standard users.

## **The Operator's Console: User Experience and Workflow Design**

The effectiveness of a complex security tool is not solely determined by its technical capabilities but also by its usability. For Blu Snu to be the "dream app" for Bluetooth penetration testers, its user interface (UI) and user experience (UX) must be meticulously designed to streamline the workflow from initial reconnaissance to final exploitation. The design philosophy prioritizes clarity, efficiency, and the intuitive visualization of complex data and attack chains, enabling the operator to make informed decisions quickly and execute actions with precision.

### **Main Dashboard**

Upon launching Blu Snu, the operator is greeted by a central dashboard that serves as a mission control center. This screen provides a real-time, at-a-glance summary of the local radio frequency (RF) environment and the status of ongoing operations. The dashboard is composed of several key widgets:

* **Nearby Devices:** A dynamic counter that displays the number of unique Bluetooth devices detected in the vicinity, clearly bifurcated into Bluetooth Classic (BR/EDR) and Bluetooth Low Energy (BLE) categories. This provides an immediate sense of the target environment's density and composition.  
* **Active Tasks:** A status panel that lists any currently running processes, such as an active scan, a connection attempt, or an executing attack chain. It displays progress indicators and allows the operator to pause or terminate tasks directly from the dashboard.  
* **Saved Sessions:** A quick-access list of previously saved reconnaissance sessions and attack configurations, allowing the operator to quickly resume a previous assessment.  
* **Attack Chain Templates:** A library of pre-configured attack workflows, enabling one-tap execution of common assessment scenarios, such as a "BLE Smart Lock Audit" or a "Legacy Headset Eavesdropping" chain.

Prominently displayed on the dashboard are quick-action buttons for "Start Scan," which initiates a comprehensive multi-protocol discovery process, and "Load Session," for importing previous work.

### **Target Management View**

The core of the reconnaissance phase is the Target Management View. This screen presents a dynamic, filterable, and sortable list of all discovered devices. It is a significant enhancement over the simple text output of tools like hcitool scan 14 or bluetoothctl 13, transforming raw data into actionable intelligence. Operators can filter the list by key parameters, including:

* **Protocol:** BR/EDR, BLE, or Dual-Mode.  
* **Signal Strength (RSSI):** To prioritize nearby targets.  
* **Vendor:** Identified via the MAC address Organizationally Unique Identifier (OUI).  
* **Discovered Services:** To find devices running specific profiles (e.g., HID, A2DP, or a custom GATT service).

Each device entry in the list is expandable, revealing a detailed target profile. This profile aggregates all gathered information, including the device's MAC address, broadcast name, a list of advertised services (both SDP for Classic and GATT for BLE), an estimated distance based on RSSI, and, crucially, any potential vulnerabilities that have been automatically flagged by the reconnaissance module's fingerprinting engine.

### **The Attack Module Interface**

Once a target has been selected from the management view, the operator is navigated to the Attack Module Interface. This screen presents a context-aware arsenal of available attack modules. The application intelligently filters the list based on the target's protocol and discovered services. For example, selecting a BLE-only smart bulb will prominently feature BLE-specific attacks like "GATT Interception (Btlejuice)" and "Connection Hijack (BtleJack)," while attacks like "Bluesnarf" would be greyed out or hidden.

Each module is designed for simplicity and ease of use. It presents a clean configuration screen with clearly labeled options and sensible default values. For instance, the BlueSmack module would feature intuitive sliders to adjust the L2CAP packet size and transmission rate, with presets for "Low," "Medium," and "Aggressive" flooding. This point-and-click approach abstracts away the complex command-line syntax of the underlying tools, allowing the operator to focus on the strategic execution of the attack.

### **The Attack Chaining Canvas**

The signature feature of Blu Snu is the Attack Chaining Canvas, a visual, node-based editor that empowers operators to design and execute sophisticated, multi-stage attack workflows. This transforms the application from a mere toolbox into a powerful automation platform.

The canvas allows users to drag-and-drop nodes, each representing a specific action or module, and connect them to define a logical flow of execution. The interface is designed to be intuitive, automatically handling the passing of data between connected nodes. For example, the MAC Address output of a "Scan (BLE)" node can be visually connected to the Target MAC input of a "Connection Hijack (BtleJack)" node.

A practical example of a workflow built on the canvas might be:

1. **Start Node:** Initiates the chain.  
2. **Scan (BLE) Node:** Scans for BLE devices in the vicinity.  
3. **Filter by Service UUID Node:** Takes the list of discovered devices and filters it to find only those advertising a specific GATT service UUID corresponding to a known smart lock model.  
4. **Connection Hijack (BtleJack) Node:** Takes the filtered device's MAC address and attempts to perform a Btlejacking attack to take over the connection.  
5. **GATT Write Node:** If the hijack is successful, this node is triggered. It is pre-configured with the specific handle and payload (0x01) required to send the "unlock" command to the compromised lock.

This visual paradigm for attack automation allows for the creation of highly customized and intelligent assessment routines that can be saved, shared, and re-used, dramatically increasing the efficiency and repeatability of penetration tests.

## **Module 1: Passive and Active Reconnaissance**

A successful penetration test is built upon a foundation of thorough and accurate reconnaissance. The Blu Snu Reconnaissance Module is designed to be the definitive tool for this initial phase, systematically gathering and analyzing the maximum possible amount of information about the surrounding Bluetooth environment. It integrates both passive listening and active probing techniques to build a comprehensive operational picture, automating the process of identifying, fingerprinting, and assessing potential targets.

### **Multi-Protocol Device Discovery**

The module's first task is to discover all active Bluetooth devices within range. To achieve this, it initiates simultaneous scanning processes for both major Bluetooth protocols:

* **Bluetooth Classic (BR/EDR):** The module performs a standard Inquiry Scan to discover devices that are in a "discoverable" mode. Following discovery, it initiates a remote name request to resolve the device's user-friendly name, replicating the initial steps of tools like hcitool and bluetoothctl.13  
* **Bluetooth Low Energy (BLE):** The module performs an Advertising Scan, listening for advertising packets broadcast by BLE peripherals. This process is entirely passive and captures a wealth of information directly from the broadcast packets. The module parses this data to extract critical details such as advertised service UUIDs, manufacturer-specific data, and the transmission power level (Tx Power), which is essential for initial distance estimation calculations.24

This dual-scan approach, inspired by the intelligence-gathering capabilities of tools like bluing 25, ensures that no potential target is missed and provides a complete inventory of the local RF landscape.

### **Service Enumeration and Fingerprinting**

Once devices are discovered, the module transitions to an active probing phase to map out their capabilities and identify their specific function and model.

* **Classic (SDP Enumeration):** For each discovered BR/EDR device, the module initiates a connection to query its Service Discovery Protocol (SDP) records. SDP acts as a directory of the services a device offers, such as Hands-Free Profile (HFP), Advanced Audio Distribution Profile (A2DP), or the Object Exchange (OBEX) protocol.3 By enumerating these services, the module can determine the device's primary functions (e.g., headset, speaker, car kit) and, critically, identify the specific RFCOMM channels on which these services operate. This channel information is a prerequisite for many classic attacks, such as Bluesnarfing, which must target the correct channel to be successful.4  
* **BLE (GATT Enumeration):** For discovered BLE peripherals, the module automatically initiates a connection and performs a full discovery of its Generic Attribute Profile (GATT) server. This process systematically reads the device's entire attribute table, mapping out all available services, characteristics, and descriptors.2 The result is a complete profile of the device's functionality, including the UUIDs for each characteristic, their properties (e.g., Read, Write, Write Without Response, Notify, Indicate), and any descriptors that provide additional context, such as human-readable descriptions or presentation formats. This detailed GATT map is essential for planning subsequent attacks, such as data manipulation via btlejuice 26 or direct interaction after a BtleJack hijack.5  
* **Device Fingerprinting:** The gathered data from both SDP and GATT enumeration, combined with the device's name and MAC address OUI, is fed into a powerful fingerprinting engine. This engine cross-references the collected attributes against an extensive, curated internal database. This database correlates specific combinations of services, UUIDs, and vendor information with known device models, manufacturers, and firmware versions. For example, a specific set of GATT services might uniquely identify a device as a "Model X Smart Lock," while a particular SDP record might flag a device as a "Brand Y Automotive IVI System." This automated identification is a critical step that precedes vulnerability analysis.

### **Vulnerability Correlation**

The reconnaissance phase in Blu Snu transcends simple data collection; it is an active intelligence analysis process. The true power of the module lies in its ability to automatically connect the dots between an identified device and its known security weaknesses, saving the penetration tester hours of manual research.

The manual workflow for a security professional typically involves scanning for devices, identifying a model, and then manually searching online databases for publicly disclosed vulnerabilities associated with that model. Blu Snu automates this entire process. As the fingerprinting engine identifies a device, it immediately queries an integrated and regularly updated vulnerability database. This database is populated with information from public sources like the National Vulnerability Database (NVD) and is enriched with the specific exploit catalogs from specialized Bluetooth security tools, most notably the comprehensive set of vulnerability templates provided by the BlueToolkit framework.16

The outcome of this correlation is a significantly enriched Target Management View. Devices with potential vulnerabilities are automatically annotated with clear visual indicators, such as a red flag icon. Tapping this indicator reveals detailed information about the potential weakness, such as "Potentially vulnerable to BLUFFS (CVE-2023-24023)" or "This device's firmware is known to be susceptible to the keystroke injection attack (CVE-2023-45866)." This feature immediately directs the tester's attention to the most promising targets and suggests the most relevant attack modules to employ. By integrating data on a wide range of vulnerabilities, from architectural flaws like BLUFFS 6 to implementation-specific bugs like those affecting ESP32 HCI commands (CVE-2025-27840) 27, the Vulnerability Correlation engine acts as an expert system, providing actionable guidance and transforming the reconnaissance module into a proactive threat identification tool.

## **Module 2: The Bluetooth Classic (BR/EDR) Attack Compendium**

This section details the suite of attack modules specifically designed to target the protocols and common implementation weaknesses of Bluetooth Classic (BR/EDR). These modules range from legacy data theft techniques to modern attacks that exploit fundamental architectural flaws in the Bluetooth specification.

### **Legacy Information Theft and Control**

Many older Bluetooth devices, and even some modern ones with poor security configurations, remain vulnerable to classic attacks that grant unauthorized access to sensitive data and device functions. Blu Snu will automate these attacks, making them easy to execute.

* **Bluesnarfing:** This module provides an automated implementation of the bluesnarfer tool's capabilities.4 Bluesnarfing is an attack that exploits vulnerabilities in the Object Exchange (OBEX) protocol to gain unauthorized access to information on a device.10 The Blu Snu module will first leverage the reconnaissance data to identify targets that expose OBEX services like File Transfer Profile (FTP) or Object Push Profile (OPP). Upon target selection, the module will attempt to connect to the appropriate RFCOMM channel and issue commands to retrieve common files, such as the device's phonebook (telecom/pb.vcf), call history, calendar entries, and text messages. The user interface will provide a checklist of target information, allowing the operator to selectively exfiltrate data. While many modern devices have protections against this attack, it remains a viable vector against legacy or misconfigured systems.4  
* **Bluebugging:** Representing a more severe escalation of Bluesnarfing, Bluebugging allows an attacker to take complete control of a vulnerable device.12 This module will attempt to create a virtual serial port connection to the target and inject AT commands, which are the standard command set for controlling modem functions.4 A successful Bluebugging attack could allow the operator to initiate phone calls, send and receive text messages, read the phonebook, and even redirect incoming calls, effectively turning the target device into a remote bugging tool.12 This attack targets severe firmware vulnerabilities typically found in older devices but is included for comprehensive assessment capabilities.  
* **BlueSpy Audio Interception:** This module is a direct implementation of the proof-of-concept demonstrated by the BlueSpy tool.13 It targets Bluetooth audio devices, such as headsets and earbuds, that feature insecure pairing mechanisms—specifically, those that do not require user interaction (like a button press or PIN entry) to pair. The module automates the entire attack chain: it discovers a vulnerable target, initiates the pairing process, connects to the device's audio source profile (HSP/HFP), and begins recording the live microphone feed. Executing this attack requires low-level control over the pairing process to bypass standard security prompts, which on Android would necessitate the use of "Elevated Mode" (root access) to replicate the btmgmt commands used in the original PoC.13

### **Denial of Service (DoS) Attacks**

Blu Snu will incorporate several modules for testing the resilience of Bluetooth devices against Denial of Service attacks, which aim to render the device's wireless functionality unusable.

* **BlueSmack (L2CAP Flood):** This is a classic DoS attack that functions similarly to an IP-based "Ping of Death".14 The module targets the Logical Link Control and Adaptation Protocol (L2CAP) layer by sending a flood of oversized L2CAP echo request packets (pings). The target device's Bluetooth stack, unable to handle the malformed or high-volume requests, may overflow its input buffers, leading to a crash or a complete freeze of its Bluetooth services.14 The Blu Snu interface will allow the operator to select a target and configure the packet size and transmission rate for the flood.  
* **Advanced DoS (BrakTooth/BlueToolkit Integration):** For more sophisticated testing, this module will incorporate a range of DoS attacks that target deeper vulnerabilities within the Bluetooth firmware, specifically at the Link Manager Protocol (LMP) layer. These attacks, cataloged by research projects like BrakTooth and implemented in frameworks like BlueToolkit 16, exploit flaws in how devices handle specific LMP feature requests and timing parameters. Examples include "V4 Feature Response Flooding," "V5 LMP Auto Rate Overflow," and "V12 AU Rand Flooding".16 Sending these specifically crafted, malformed LMP packets is beyond the capabilities of standard Bluetooth APIs and will require either "Elevated (Root)" or "Hardware-Assisted" mode to achieve the necessary level of control over the radio.

### **Protocol and Session Hijacking (BLUFFS)**

The integration of the BLUFFS (Bluetooth Forward and Future Secrecy Attacks and Defenses) attack suite is essential for any state-of-the-art Bluetooth security tool. Identified by CVE-2023-24023, BLUFFS represents a fundamental break in the security model of the Bluetooth standard itself, moving beyond simple implementation bugs to exploit architectural flaws in session key derivation.6

The core vulnerability exploited by BLUFFS is the lack of guaranteed forward and future secrecy in Bluetooth sessions. The attacks demonstrate that by compromising a *single* session key, an attacker can impersonate either the central or peripheral device in *future* sessions, enabling persistent Man-in-the-Middle (MitM) attacks.6

A direct port of the original bluffs research toolkit is not feasible for a general-purpose Android application, as the toolkit relies on dynamically patching the closed-source firmware of a specific Bluetooth chipset to manipulate key derivation.6 Instead, the Blu Snu BLUFFS module will be an automated workflow that implements the *methodology* of the attack by chaining together several precursor vulnerabilities that are often prerequisites for a successful BLUFFS exploit.32 The automated attack chain will proceed as follows:

1. **Step 1 (Key Weakening \- KNOB Attack):** The module will first attempt a Key Negotiation of Bluetooth (KNOB) attack. During the pairing process, it will try to influence the negotiation of the encryption key's entropy, forcing the devices to agree on a very short key (e.g., 1 byte). A shorter key is dramatically easier to brute-force.32  
2. **Step 2 (Authentication Downgrade \- BIAS Attack):** Next, the module will attempt a Bluetooth Impersonation AttackS (BIAS). It will impersonate a previously paired device to the target and attempt to force a downgrade of the security level from the more robust Secure Connections (SC) mode to the vulnerable Legacy Secure Connections (LSC) mode, potentially bypassing the authentication procedure.32  
3. **Step 3 (Session Key Derivation Exploit):** If the previous steps are successful and a session key is compromised (either through brute-forcing the weak key or by exploiting the LSC pairing), the module will then attempt the core BLUFFS exploit. In a subsequent connection attempt between the legitimate devices, the module will intervene and force the reuse of the previously compromised key, thereby breaking the session independence and establishing a MitM position.

Executing this complex chain requires extremely fine-grained control over the pairing, authentication, and key negotiation protocols. As such, the BLUFFS module will be one of the most advanced features of Blu Snu, requiring the highest level of privilege in either "Elevated (Root)" or "Hardware-Assisted" mode.

## **Module 3: The Bluetooth Low Energy (BLE) Attack Compendium**

Bluetooth Low Energy (BLE) introduces a distinct attack surface centered around its stateful, connection-oriented nature and the Generic Attribute Profile (GATT) protocol. This section outlines the Blu Snu modules designed specifically to audit and exploit vulnerabilities in BLE devices, from real-time data manipulation to complete connection hijacking.

### **Man-in-the-Middle (MitM) Framework**

This module provides an integrated, user-friendly implementation of the powerful MitM capabilities pioneered by the btlejuice framework.18 To function, this attack requires the Blu Snu host device to utilize two separate Bluetooth adapters: the phone's internal radio and a compatible external USB dongle. This two-adapter setup allows the device to simultaneously act as a fake peripheral to the legitimate central (e.g., the user's smartphone app) and as a fake central to the legitimate peripheral (e.g., a smart device).

The attack workflow is automated by the module:

1. The operator selects a target peripheral device from the reconnaissance scan.  
2. Blu Snu uses one adapter to begin advertising with the same identity as the target peripheral, effectively spoofing it.  
3. When the legitimate central device attempts to connect, it connects to Blu Snu's fake peripheral.  
4. Simultaneously, Blu Snu uses the second adapter to connect to the real peripheral device.  
5. With the proxy established, Blu Snu relays all GATT traffic between the two legitimate devices.

The UI for this module will present a real-time log of all intercepted GATT operations: reads, writes, and notifications. Crucially, it will allow the operator to intercept, view, and modify any data packet on the fly before it is forwarded to its destination. This functionality is modeled after the HookingInterface provided by the btlejuice-python-bindings.26 For example, an operator could intercept a WRITE request containing a password, capture it, and then forward it unmodified. Alternatively, they could intercept a READ response from a medical sensor containing a glucose reading and alter the value before it is displayed on the user's monitoring app. This powerful capability allows for deep inspection and manipulation of a device's application-layer protocol.

### **Sniffing, Jamming, and Connection Hijacking**

This module integrates the advanced radio-layer attack capabilities of BtleJack 5 and is fundamentally dependent on the "Hardware-Assisted Mode," requiring an external, compatible nRF51-based board (such as a BBC Micro:Bit) flashed with the BtleJack firmware. The Blu Snu application serves as the command-and-control interface for this external hardware.

* **Sniffing:** The operator can select a target device or an area to monitor. Blu Snu will then command the external hardware to enter a promiscuous sniffing mode. If a specific connection is targeted, the hardware will attempt to derive the connection parameters (access address, CRC initialization value, channel map, and hopping interval) and capture all link-layer data packets exchanged between the central and peripheral. The captured data can be streamed in real-time to the Android device for live analysis or saved to a standard PCAP file, which can be exported for in-depth inspection using tools like Wireshark.5  
* **Jamming:** The module can perform targeted jamming of a specific BLE connection. Unlike a brute-force RF jammer, this is a precise attack. Once a connection's parameters are sniffed, Blu Snu instructs the hardware to transmit noise only during the time slots and on the frequencies being used by the target connection. This selective jamming is highly effective at forcing a disconnect by causing the connection to exceed its "Supervision Timeout" value without receiving valid packets.20  
* **Hijacking (Btlejacking \- CVE-2018-7252):** This is the module's most potent capability. It orchestrates a full connection takeover by chaining the previous two techniques. The process, known as Btlejacking, is as follows 20:  
  1. Blu Snu sniffs the target connection to acquire its parameters.  
  2. It then initiates a targeted jamming attack to force the legitimate central device to disconnect.  
  3. The instant the peripheral device becomes available again, Blu Snu uses the sniffed parameters to connect to it, effectively taking the place of the original central.

Once the connection is hijacked, Blu Snu provides the operator with an interactive prompt, similar to that of the standalone BtleJack tool.5 From this prompt, the operator can perform any GATT operation on the now-compromised device: discover its full service profile, read sensitive data from characteristics, or write malicious commands to control its behavior.

### **GATT Fuzzing and Exploitation**

This module provides an automated tool for probing a BLE device's GATT implementation for common vulnerabilities. After performing an initial GATT service discovery, the fuzzer will systematically test every characteristic for weaknesses:

* **Malformed Data Injection:** For every writable characteristic, the module will send a variety of malformed data payloads, including oversized packets, null values, and data of an incorrect type (e.g., sending a string to a characteristic expecting an integer). This aims to identify buffer overflows or parsing errors in the peripheral's firmware that could lead to a crash or other unexpected behavior.  
* **Information Disclosure Testing:** The module will attempt to READ from every characteristic, including those that may not have the read property explicitly flagged, to check for potential information leaks.  
* **Notification/Indication Abuse:** It will attempt to subscribe to notifications or indications on every available characteristic to see if the device leaks data without proper authorization.  
* **Authorization Bypass:** The core function of the fuzzer is to test for authorization and authentication bypasses. It will attempt to read from and write to characteristics that should be protected (e.g., requiring a bonded and encrypted link) without first establishing that level of security. A successful write to a "protected" characteristic on an unauthenticated link would represent a critical vulnerability.

This automated probing helps to quickly identify common implementation flaws in a device's BLE stack and application logic.

## **Module 4: Advanced Signal and Connection Manipulation**

This section details Blu Snu's most advanced capabilities, which move beyond standard protocol exploitation to manipulate the physical radio layer and the fundamental processes of connection establishment. These modules often require specialized techniques and, in some cases, external hardware to achieve their objectives.

### **Device Geolocation and Tracking**

This module transforms the raw Received Signal Strength Indication (RSSI) data, often dismissed as a simple signal meter, into a source of actionable physical intelligence. While RSSI can be used to estimate the distance to a transmitter, its raw values are notoriously unstable and susceptible to environmental factors such as obstacles, antenna orientation, and multipath fading.35 A "dream" implementation must therefore incorporate advanced signal processing and positioning algorithms to provide reliable location data.

The development of this feature follows a logical progression from simple estimation to sophisticated tracking:

1. **Baseline Distance Estimation:** The initial step is to convert raw RSSI values into a rough distance estimate. This is typically done using the log-distance path loss model, often expressed by the formula $d \= 10^{((P-S)/(10N))}$, where $d$ is the distance, $P$ is the device's calibrated transmission power at 1 meter (often included in the advertising packet), $S$ is the measured RSSI, and $N$ is the path-loss exponent, an environmental constant.24 This formula provides a basic, albeit noisy, distance approximation.  
2. **Signal Filtering and Smoothing:** To counter the inherent volatility of RSSI readings, the module will implement signal processing filters. A common and effective approach is the use of a Kalman filter, which is well-suited for estimating the state of a dynamic system in the presence of noisy measurements.36 Simpler methods, such as a moving average filter, will also be available to smooth out erratic fluctuations and provide a more stable distance estimate over time.24  
3. **Multi-Beacon Trilateration:** For more precise 2D positioning, the module will support trilateration. This technique requires RSSI measurements from a single target device to be taken from at least three different known locations. In the context of Blu Snu, this can be achieved by using multiple instances of the app on different phones or by using a single host device equipped with multiple external Bluetooth dongles positioned apart from each other. By calculating the distance to the target from each of these points, the module can geometrically determine the target's approximate coordinates.24  
4. **RSSI Fingerprinting:** For assessments within a fixed, known environment like an office building or a laboratory, the module will support RSSI fingerprinting. This advanced technique involves two phases. First, in an offline "training" phase, the operator walks through the environment and records the RSSI signature of the target device at various known coordinates, building a detailed "radio map" or fingerprint database. Second, in the online "tracking" phase, the module measures the real-time RSSI from the target and compares it against the fingerprint database to find the closest match, thereby estimating the device's location with a much higher degree of accuracy than simple formula-based methods can achieve.37

The UI for this module will feature a "Radar" view, plotting nearby devices with their estimated distance and direction (if multiple antennas are used). It will also include a "Proximity Alert" feature, which can be configured to trigger other actions or attack chains when a specific target enters or leaves a defined range.

### **Targeted Radio Frequency Jamming**

This highly advanced module is designed to perform targeted jamming of Bluetooth Classic connections and requires external, software-defined radio (SDR) hardware controlled by the Blu Snu application. The primary challenge in jamming Bluetooth Classic is its use of Frequency-Hopping Spread Spectrum (FHSS), a technique that makes it highly resilient to simple, single-frequency interference. A BR/EDR piconet rapidly switches between 79 different 1 MHz-wide channels in a pseudo-random sequence, hopping up to 1600 times per second.38

To overcome this, the module will implement a "reactive jamming" strategy. The process is as follows:

1. **Sequence Acquisition:** The SDR hardware will first passively sniff a portion of the target piconet's traffic. The hopping sequence is determined by the master device's clock and its Bluetooth Device Address (BD\_ADDR).38 By capturing a series of packets and analyzing their frequencies and timing, the module can begin to predict the hopping pattern.  
2. **Predictive Jamming:** Once a partial sequence is learned and the master's clock is synchronized, the module will command the SDR to transmit bursts of noise on the specific frequencies that the piconet is predicted to hop to in the immediate future. By intelligently targeting only the active channels, this method can effectively disrupt communication with far less power than a brute-force broadband jammer.

This sophisticated technique is distinct from the simpler BLE jamming used in Btlejacking, which merely aims to cause a supervision timeout. Reactive FHSS jamming is designed to disrupt the fundamental communication layer of a resilient protocol.

### **Automated Connection and Pairing Attacks**

This module automates several attacks that target vulnerabilities in the device pairing and connection establishment process.

* **Legacy PIN Bruteforcing:** Many older Bluetooth devices and peripherals use a simple 4-digit numeric PIN for legacy pairing. This module will automate the process of attempting to pair with a target by iterating through all 10,000 possible PINs (from "0000" to "9999"). The module will also incorporate logic to test for known vulnerabilities that can facilitate such attacks, such as the Android "Porsche pairing conflict" flaw, which, in certain Android versions, created an exploitable condition where a hard-coded PIN of "0000" could be used to bypass the standard pairing process.39  
* **Just Works Pairing Bypass (CVE-2023-45866):** This module provides a one-click test for the critical keystroke injection vulnerability identified in late 2023\. The vulnerability allows an attacker to pair with a vulnerable host (including Android, Linux, macOS, and iOS devices) by emulating a Human Interface Device (HID), such as a keyboard, using the unauthenticated "Just Works" pairing method. On vulnerable systems, this pairing completes without any confirmation or prompt displayed to the user.21 Once the silent pairing is complete, the attacker can inject keystrokes to perform arbitrary actions with the user's privileges. The Blu Snu module will automate this entire process: it will emulate a keyboard, attempt the "Just Works" pairing, and if successful, will flag the device as vulnerable and provide an interface for the operator to send keystroke commands.  
* **Stealtooth Attack Probe:** Based on recent research into vulnerabilities in automatic pairing functionalities 22, this module will test if a device is susceptible to a "Stealtooth" attack. Many consumer devices (especially audio products) are designed to automatically enter pairing mode under specific conditions to improve user convenience (e.g., when they are turned on and not connected to a known device). The Stealtooth attack abuses this behavior to force a device into pairing mode and then silently pairs with it, overwriting the legitimate owner's stored link key. This allows the attacker to subsequently perform MitM attacks. The Blu Snu module will attempt to trigger these conditions and execute a silent pairing, reporting whether the device is vulnerable to this form of pairing hijack.

## **The Automation Core: A Framework for Attack Chaining**

The true innovation of Blu Snu lies not just in its comprehensive collection of individual attack modules, but in its ability to weave them together into intelligent, automated sequences. The Automation Core is the engine that drives this capability, providing a visual, logic-based framework for designing, executing, and saving complex attack chains. This elevates Blu Snu from a simple toolbox to a strategic platform for offensive security operations, enabling testers to automate repetitive tasks and build sophisticated, context-aware attack workflows.

### **Node-Based Logic**

The foundation of the Automation Core is a visual, node-based editor referred to as the Attack Chaining Canvas. This intuitive interface allows operators to construct attack flows by dragging, dropping, and connecting nodes, each representing a specific action or logical step.

* **Nodes:** Each module and sub-function within Blu Snu is represented as a distinct node. Examples include a Scan for BLE Devices node, a Connect to Target node, a Read GATT Characteristic node, and an Execute L2CAP Flood node. Each node has defined inputs (e.g., a Target MAC Address) and outputs (e.g., a List of Discovered Devices or a Characteristic Value).  
* **Connectors:** Operators can draw connections between the output of one node and the input of another. These connectors represent the flow of both data and control. For instance, the MAC Address output from a Scan node can be piped directly into the Target MAC input of a Connect node, automating the process of selecting a target discovered during a scan.  
* **Conditional Logic:** To enable the creation of truly intelligent and adaptive attack chains, the framework includes nodes for conditional logic. These nodes allow the workflow to make decisions based on the results of previous steps.  
  * **If/Else Node:** This node evaluates an input condition (e.g., "Was the connection successful?") and directs the workflow down one of two paths.  
  * **Wait Node:** This node pauses the execution for a specified duration or until a specific condition is met.  
  * **Loop Node:** This node allows a sequence of actions to be repeated a set number of times or until a condition is met.

This combination of action nodes and logic nodes allows for the construction of highly complex scenarios. For example, an operator could design a chain that continuously monitors the RSSI of a target device and, if the signal strength exceeds a certain threshold (indicating proximity), automatically launches a BlueSmack DoS attack: Loop \-\> Read RSSI \-\> If RSSI \> \-50dBm \-\> Execute BlueSmack \-\> Wait 60s \-\> End Loop.

### **Pre-built Attack Chain Templates**

To accelerate the assessment process and provide guidance for common scenarios, Blu Snu will ship with a library of pre-built attack chain templates. These templates can be loaded onto the canvas, customized by the operator, and executed with a single tap. Examples of these templates include:

* **Template 1: Automated BLE Smart Lock Audit:**  
  1. Scan for BLE Devices  
  2. Filter by Service UUID (pre-configured with the known GATT service UUID for a popular smart lock brand)  
  3. Attempt Btlejack Connection Hijack on the filtered device  
  4. If Hijack Successful \-\> Write GATT Characteristic (pre-configured with the handle and payload for the "unlock" command)  
  5. Log Result  
* **Template 2: BLUFFS Attack Chain Simulation:**  
  1. Scan for Classic Devices  
  2. Initiate Pairing with the target  
  3. Attempt KNOB Attack to negotiate low key entropy  
  4. If Key Entropy \< 7 bytes \-\> Attempt BIAS Impersonation to downgrade to Legacy Secure Connections  
  5. If Downgrade Successful \-\> Attempt Session Key Reuse on a subsequent connection  
  6. Log Vulnerability Status  
* **Template 3: Opportunistic Audio Eavesdropping:**  
  1. Scan for Classic Devices  
  2. Filter by Device Class (to identify headsets and audio devices)  
  3. For Each Device in List \-\> Test for Insecure Pairing (check if pairing completes without user interaction)  
  4. If Insecure \-\> Execute BlueSpy Audio Record for 120 seconds  
  5. Exfiltrate Recorded Audio to local storage

### **Custom Triggers and Variables**

To further enhance the power of the automation engine, operators can define custom variables within a chain (e.g., TARGET\_MAC, UNLOCK\_PAYLOAD) that can be used across multiple nodes. The engine will also support a variety of triggers to initiate a chain automatically, allowing for "set-and-forget" operations. These triggers can be based on:

* **Time:** Execute a chain at a specific time or on a recurring schedule.  
* **Location:** Using the device's GPS, initiate a chain when the operator enters a predefined geographic area (geofencing).  
* **Signal Strength:** Begin a workflow when a specific target device's RSSI value crosses a defined threshold, indicating it has come into close proximity.

This powerful combination of visual workflow design, conditional logic, pre-built templates, and automated triggers makes the Automation Core the central feature that distinguishes Blu Snu as a premier tool for professional Bluetooth security assessment.

## **Implementation Blueprint and Ethical Mandates**

The final section of this blueprint addresses the practical considerations for developing the Blu Snu application and establishes the critical ethical framework that must govern its use. A tool of this power demands a responsible approach to its implementation, distribution, and operational guidelines.

### **Android Permissions and Requirements**

To function correctly, the Blu Snu application will require a specific set of permissions to be declared in its Android manifest file. The application must provide clear and transparent justifications to the user for each requested permission, particularly those that are sensitive or have privacy implications.

* **Core Bluetooth Permissions (Android 12 and higher):**  
  * BLUETOOTH\_SCAN: Required to discover nearby Bluetooth devices. This is a runtime permission that must be explicitly granted by the user.7  
  * BLUETOOTH\_CONNECT: Required to initiate connections to, and communicate with, paired Bluetooth devices. This is also a runtime permission.7  
  * BLUETOOTH\_ADVERTISE: Required for modules that need to broadcast as a BLE peripheral, such as the Btlejuice MitM framework. This is a runtime permission.7  
* **Location Permissions:**  
  * ACCESS\_FINE\_LOCATION: On Android versions prior to 12, this permission is mandatory for any app that performs Bluetooth scanning. For Android 12 and higher, it is required for the RSSI Geolocation and Tracking module. For all other functions, the application will set the android:usesPermissionFlags="neverForLocation" attribute in the BLUETOOTH\_SCAN permission declaration. This is a strong assertion to the system and the user that the scan results are not being used to determine the user's physical location, which can help build user trust and is a best practice when location is not a core function.7  
  * ACCESS\_BACKGROUND\_LOCATION: This highly sensitive permission will only be requested if the user explicitly enables a feature that requires continuous monitoring or scanning while the app is not in the foreground, such as a persistent proximity alert.  
* **Other Permissions:**  
  * INTERNET: Required for downloading updates to the internal vulnerability and device fingerprinting databases.  
  * WRITE\_EXTERNAL\_STORAGE: Required for saving session data, captured packets (PCAP files), and generated penetration test reports.

### **Hardware and Software Dependencies**

The functionality of Blu Snu is tiered and depends on the available hardware and software environment.

* **Internal Hardware:** The application requires a host device with a modern Bluetooth chipset, ideally supporting Bluetooth 5.0 or higher. Newer chipsets provide better support for advanced BLE features like extended advertising and higher data rates, which can be beneficial for certain reconnaissance tasks.  
* **External Hardware:** The application must clearly communicate to the user that its full capabilities are unlocked only with the use of external hardware. The "Hardware Manager" section of the app will provide detailed instructions, compatibility lists, and even automated scripts for setting up and flashing the required firmware onto these devices.  
  * **For MitM Attacks:** A compatible USB On-The-Go (OTG) adapter and a supported external USB Bluetooth dongle are required for the btlejuice-based MitM module.  
  * **For Sniffing/Jamming/Hijacking:** A compatible nRF51- or nRF52-based development board (e.g., BBC Micro:Bit, Adafruit Bluefruit LE Sniffer) is required for all BtleJack-based functionalities.5  
* **Software:** The application's effectiveness is heavily reliant on its internal databases. It must include a mechanism for regularly and securely updating its database of device fingerprints, service UUIDs, and known CVEs to remain current with the evolving threat landscape.

### **Ethical Use Mandate and Reporting**

The development and distribution of a powerful offensive security tool like Blu Snu carry a significant ethical responsibility. The tool must be designed and positioned explicitly for legitimate security research and professional penetration testing, and must incorporate features that discourage malicious use.

* **Mandatory Disclaimer:** Upon first launch, the application will display a prominent, non-skippable disclaimer. This statement will clearly articulate that the tool is intended for use by security professionals for educational purposes and for security assessments on networks and devices for which they have received explicit, written authorization. It will state unequivocally that using the tool for unauthorized access or malicious activity is illegal and that the developers assume no liability for its misuse.  
* **Professional Reporting Engine:** To reinforce its role as a professional assessment tool, Blu Snu will feature a robust reporting engine. After completing an assessment, whether using individual modules or complex attack chains, the operator can generate a detailed report. This report, exportable in formats like PDF and Markdown, will serve as official documentation for a penetration testing engagement. It will automatically log:  
  * A timeline of all actions performed.  
  * The specific targets (MAC addresses and device names) of each action.  
  * The configuration parameters used for each attack module.  
  * The results and logs of each operation (e.g., "Connection successful," "Vulnerability CVE-2023-45866 detected," "Data exfiltrated from phonebook").

This feature is not only essential for professional deliverables but also encourages a methodical and accountable approach to security testing.

* **Transparency and No Obfuscation:** The tool's purpose will never be misrepresented. It will be marketed and described openly as an offensive security and penetration testing framework. This transparent approach aligns with the ethos of the open-source security research community, from which many of the integrated tools and concepts originate.6

By embedding these principles directly into the application's design and user workflow, the Blu Snu project aims to provide an unparalleled tool for advancing Bluetooth security while simultaneously promoting a culture of responsible and ethical use.

### **Appendix: Bluetooth Security Modes and Levels**

Understanding the various security modes and levels defined in the Bluetooth specifications is crucial for contextualizing the impact of many attacks. Vulnerabilities that enable a security downgrade—forcing a connection to use a weaker mode than it is capable of—are particularly critical. This table provides a reference for the security postures of both Bluetooth Classic and BLE.

| Security Mode/Level | Target Protocol | Authentication | Encryption | MITM Protection | Description |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **Classic Security Mode 1** | Classic (BR/EDR) | No | No | No | Non-secure mode. No security measures are enforced. |
| **Classic Security Mode 2** | Classic (BR/EDR) | Optional | Optional | No | Service-level enforced security. Security is initiated after the channel is established. |
| **Classic Security Mode 3** | Classic (BR/EDR) | Yes | Yes | No | Link-level enforced security. Security is initiated before the channel is established. Uses legacy pairing. |
| **Classic Security Mode 4** | Classic (BR/EDR) | Yes | Yes | Yes | Service-level enforced security with Secure Simple Pairing (SSP) and Secure Connections. Provides MITM protection. 1 |
| **BLE Security Mode 1, Level 1** | BLE | No | No | No | No security (No authentication and no encryption). |
| **BLE Security Mode 1, Level 2** | BLE | No | Yes | No | Unauthenticated pairing with encryption. Vulnerable to MITM. Achieved via "Just Works." 40 |
| **BLE Security Mode 1, Level 3** | BLE | Yes | Yes | Yes | Authenticated pairing with encryption. Provides MITM protection. Achieved via Passkey Entry with Legacy Pairing. 40 |
| **BLE Security Mode 1, Level 4** | BLE | Yes | Yes | Yes | Authenticated LE Secure Connections pairing with encryption. Highest level of security. Achieved via Numeric Comparison or Passkey Entry with Secure Connections. 40 |

#### **Works cited**

1. Bluetooth Security Primer \- Classic \+ BLE \- Thyrasec, accessed October 16, 2025, [https://www.thyrasec.com/blog/bluetooth-security-primer-classic-ble/](https://www.thyrasec.com/blog/bluetooth-security-primer-classic-ble/)  
2. Bluetooth Low Energy | Connectivity \- Android Developers, accessed October 16, 2025, [https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)  
3. Classic Bluetooth Security and Exploitation \- ResearchGate, accessed October 16, 2025, [https://www.researchgate.net/profile/Yuksel-Celik-2/publication/383849201\_Classic\_Bluetooth\_Security\_and\_Exploitation\_19th\_Annual\_Symposium\_on\_Information\_Assurance\_ASIA\_'24\_Classic\_Bluetooth\_Security\_and\_Exploitation/links/66dc8260fa5e11512ca54da4/Classic-Bluetooth-Security-and-Exploitation-19th-Annual-Symposium-on-Information-Assurance-ASIA-24-Classic-Bluetooth-Security-and-Exploitation.pdf](https://www.researchgate.net/profile/Yuksel-Celik-2/publication/383849201_Classic_Bluetooth_Security_and_Exploitation_19th_Annual_Symposium_on_Information_Assurance_ASIA_'24_Classic_Bluetooth_Security_and_Exploitation/links/66dc8260fa5e11512ca54da4/Classic-Bluetooth-Security-and-Exploitation-19th-Annual-Symposium-on-Information-Assurance-ASIA-24-Classic-Bluetooth-Security-and-Exploitation.pdf)  
4. kimbo/bluesnarfer: Bluetooth hack, forked from https://gitlab.com/kalilinux/packages/bluesnarfer \- GitHub, accessed October 16, 2025, [https://github.com/kimbo/bluesnarfer](https://github.com/kimbo/bluesnarfer)  
5. Charmve/BtleJack: Bluetooth Low Energy Swiss-army Knife @virtualabs @Charmve \- GitHub, accessed October 16, 2025, [https://github.com/Charmve/BtleJack](https://github.com/Charmve/BtleJack)  
6. francozappa/bluffs: Bluetooth Forward and Future Secrecy Attacks and Defenses (BLUFFS) \[CVE 2023-24023\] \- GitHub, accessed October 16, 2025, [https://github.com/francozappa/bluffs](https://github.com/francozappa/bluffs)  
7. Bluetooth permissions | Connectivity \- Android Developers, accessed October 16, 2025, [https://developer.android.com/develop/connectivity/bluetooth/bt-permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)  
8. Bluetooth overview | Connectivity \- Android Developers, accessed October 16, 2025, [https://developer.android.com/develop/connectivity/bluetooth](https://developer.android.com/develop/connectivity/bluetooth)  
9. bluez | Kali Linux Tools, accessed October 16, 2025, [https://www.kali.org/tools/bluez/](https://www.kali.org/tools/bluez/)  
10. Bluesnarfing \- Wikipedia, accessed October 16, 2025, [https://en.wikipedia.org/wiki/Bluesnarfing](https://en.wikipedia.org/wiki/Bluesnarfing)  
11. bluebugging · GitHub Topics, accessed October 16, 2025, [https://github.com/topics/bluebugging](https://github.com/topics/bluebugging)  
12. Bluebugging \- Wikipedia, accessed October 16, 2025, [https://en.wikipedia.org/wiki/Bluebugging](https://en.wikipedia.org/wiki/Bluebugging)  
13. TarlogicSecurity/BlueSpy: PoC to record audio from a Bluetooth device \- GitHub, accessed October 16, 2025, [https://github.com/TarlogicSecurity/BlueSpy](https://github.com/TarlogicSecurity/BlueSpy)  
14. guava0603/blue-smack-attack: Config files for my GitHub profile., accessed October 16, 2025, [https://github.com/guava0603/blue-smack-attack](https://github.com/guava0603/blue-smack-attack)  
15. CAPEC-666: BlueSmacking (Version 3.9) \- Mitre, accessed October 16, 2025, [https://capec.mitre.org/data/definitions/666.html](https://capec.mitre.org/data/definitions/666.html)  
16. bluetooth-classic · GitHub Topics · GitHub, accessed October 16, 2025, [https://github.com/topics/bluetooth-classic](https://github.com/topics/bluetooth-classic)  
17. 37C3 \- BLUFFS: Bluetooth Forward and Future Secrecy Attacks and Defenses \- YouTube, accessed October 16, 2025, [https://www.youtube.com/watch?v=2HstGZPZpZY](https://www.youtube.com/watch?v=2HstGZPZpZY)  
18. BtleJuice Bluetooth Smart (LE) Man-in-the-Middle framework \- GitHub, accessed October 16, 2025, [https://github.com/DigitalSecurity/btlejuice](https://github.com/DigitalSecurity/btlejuice)  
19. Bluetooth MitM Attacks \- Bastille Networks, accessed October 16, 2025, [https://bastille.net/video-snippet-bluetooth-mitm-attacks/](https://bastille.net/video-snippet-bluetooth-mitm-attacks/)  
20. Bluetooth Devices at Risk From Btlejacking Jamming and Takeover Attack \- eWeek, accessed October 16, 2025, [https://www.eweek.com/security/bluetooth-devices-at-risk-from-btlejacking-takeover-attack/](https://www.eweek.com/security/bluetooth-devices-at-risk-from-btlejacking-takeover-attack/)  
21. CVE-2023-45866 Impact, Exploitability, and Mitigation Steps | Wiz, accessed October 16, 2025, [https://www.wiz.io/vulnerability-database/cve/cve-2023-45866](https://www.wiz.io/vulnerability-database/cve/cve-2023-45866)  
22. \[2507.00847\] Stealtooth: Breaking Bluetooth Security Abusing Silent Automatic Pairing \- arXiv, accessed October 16, 2025, [https://arxiv.org/abs/2507.00847](https://arxiv.org/abs/2507.00847)  
23. sgxgsx/BlueToolkit: BlueToolkit is an extensible Bluetooth Classic vulnerability testing framework that helps uncover new and old vulnerabilities in Bluetooth-enabled devices. Could be used in the vulnerability research, penetration testing and bluetooth hacking. We also collected and classified Bluetooth vulnerabilities in an "Awesome Bluetooth Security" way \- GitHub, accessed October 16, 2025, [https://github.com/sgxgsx/BlueToolkit](https://github.com/sgxgsx/BlueToolkit)  
24. Micro-Location Part 2: BLE and RSSI \- Abalta Technologies, Inc., accessed October 16, 2025, [https://abaltatech.com/blog/2021/01/microlocation2/](https://abaltatech.com/blog/2021/01/microlocation2/)  
25. bluetooth · GitHub Topics · GitHub, accessed October 16, 2025, [https://github.com/topics/bluetooth?l=python\&o=asc%3Fu%3Dhttp%3A%2F%2Fgithub.com%2Fsponsors%2Fjustcallmekoko](https://github.com/topics/bluetooth?l=python&o=asc?u%3Dhttp://github.com/sponsors/justcallmekoko)  
26. DigitalSecurity/btlejuice-python-bindings: Python 2.x/3.x bindings for BtleJuice (BLE MitM framework) \- GitHub, accessed October 16, 2025, [https://github.com/DigitalSecurity/btlejuice-python-bindings](https://github.com/DigitalSecurity/btlejuice-python-bindings)  
27. CVE-2025-27840: Vulnerability Exploitation in Espressif ESP32 Bluetooth Chips Can Lead to Unauthorized Access to Devices | SOC Prime, accessed October 16, 2025, [https://socprime.com/blog/cve-2025-27840-vulnerability-in-esp32-bluetooth-chips/](https://socprime.com/blog/cve-2025-27840-vulnerability-in-esp32-bluetooth-chips/)  
28. What is Bluesnarfing? V2 \- Huntress, accessed October 16, 2025, [https://www.huntress.com/cybersecurity-101/topic/what-is-bluesnarfing-bluetooth-cybersecurity-guide](https://www.huntress.com/cybersecurity-101/topic/what-is-bluesnarfing-bluetooth-cybersecurity-guide)  
29. Bluesnarfing targets devices through Bluetooth \- McAfee, accessed October 16, 2025, [https://www.mcafee.com/learn/what-is-a-bluesnarfing-attack-and-why-should-you-be-aware-of-it/](https://www.mcafee.com/learn/what-is-a-bluesnarfing-attack-and-why-should-you-be-aware-of-it/)  
30. A Bad Case of Bluebug: Mitigating Risks of Bluetooth Attacks \- AMI, accessed October 16, 2025, [https://www.ami.com/blog/2017/11/09/a-bad-case-of-bluebug-mitigating-risks-of-bluetooth-attacks/](https://www.ami.com/blog/2017/11/09/a-bad-case-of-bluebug-mitigating-risks-of-bluetooth-attacks/)  
31. Bluetooth Attacks and Security Tips – Awareness Results in Better Protection, accessed October 16, 2025, [https://home.sophos.com/en-us/security-news/2021/bluetooth-attacks](https://home.sophos.com/en-us/security-news/2021/bluetooth-attacks)  
32. Security Advisory Concerning the Bluetooth BLUFFS Vulnerability, accessed October 16, 2025, [https://www.espressif.com/sites/default/files/advisory\_downloads/AR2023-010%20Security%20Advisory%20Concerning%20the%20Bluetooth%20BLUFFS%20Vulnerability%20EN.pdf](https://www.espressif.com/sites/default/files/advisory_downloads/AR2023-010%20Security%20Advisory%20Concerning%20the%20Bluetooth%20BLUFFS%20Vulnerability%20EN.pdf)  
33. btlejack · GitHub Topics, accessed October 16, 2025, [https://github.com/topics/btlejack](https://github.com/topics/btlejack)  
34. Bluetooth connections sniffing \- BSAM-RES-04 \- Tarlogic Security, accessed October 16, 2025, [https://www.tarlogic.com/bsam/resources/bluetooth-connection-sniffing/](https://www.tarlogic.com/bsam/resources/bluetooth-connection-sniffing/)  
35. Understanding the Measures of Bluetooth RSSI \- MOKOBlue, accessed October 16, 2025, [https://www.mokoblue.com/measures-of-bluetooth-rssi/](https://www.mokoblue.com/measures-of-bluetooth-rssi/)  
36. Bluetooth RSSI Measurement for Indoor Positioning – BeaconZone Blog, accessed October 16, 2025, [https://www.beaconzone.co.uk/blog/bluetooth-rssi-measurement-for-indoor-positioning/](https://www.beaconzone.co.uk/blog/bluetooth-rssi-measurement-for-indoor-positioning/)  
37. The Role of Bluetooth RSSI in Indoor Positioning \- MOKO Smart, accessed October 16, 2025, [https://www.mokosmart.com/the-role-of-bluetooth-rssi-in-indoor-positioning/](https://www.mokosmart.com/the-role-of-bluetooth-rssi-in-indoor-positioning/)  
38. Bluetooth Jamming \- ETH Zürich, accessed October 16, 2025, [https://pub.tik.ee.ethz.ch/students/2012-HS/BA-2012-16.pdf](https://pub.tik.ee.ethz.ch/students/2012-HS/BA-2012-16.pdf)  
39. Bluetooth Pairing Authentication Bypass \- WithSecure™ Labs, accessed October 16, 2025, [https://labs.withsecure.com/content/dam/labs/docs/mwri-android-bluetooth-pairing-bypass-2016-04-12.pdf](https://labs.withsecure.com/content/dam/labs/docs/mwri-android-bluetooth-pairing-bypass-2016-04-12.pdf)  
40. Security modes \- Nordic Developer Academy, accessed October 16, 2025, [https://academy.nordicsemi.com/courses/bluetooth-low-energy-fundamentals/lessons/lesson-5-bluetooth-le-security-fundamentals/topic/security-modes/](https://academy.nordicsemi.com/courses/bluetooth-low-energy-fundamentals/lessons/lesson-5-bluetooth-le-security-fundamentals/topic/security-modes/)