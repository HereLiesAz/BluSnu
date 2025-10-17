# Blu Snu: An Integrated Offensive Bluetooth Security Framework

Blu Snu is a comprehensive, mobile-first offensive security framework for Bluetooth, designed to unify the assessment of both Bluetooth Classic (BR/EDR) and Bluetooth Low Energy (BLE) systems. The project's vision is to provide security professionals with a powerful, intuitive, and integrated platform for auditing the complex and ever-expanding Bluetooth attack surface, from IoT devices to automotive and medical systems.

## The Blu Snu Vision

The security assessment of Bluetooth-enabled systems is often fragmented, relying on a disparate collection of command-line tools, specialized hardware, and platform-specific scripts. Blu Snu aims to solve this by providing a single, powerful Android application that automates and simplifies sophisticated penetration testing methodologies. By abstracting the underlying complexities of hardware and protocol stacks, Blu Snu presents the user with a unified and streamlined workflow.

## Core Architectural Principles

The Blu Snu framework is built upon four foundational principles:

1.  **Unified Protocol Abstraction:** A core library provides a consistent API for interacting with both Bluetooth Classic and BLE targets, simplifying the development of attack modules.
2.  **Modular Attack Framework:** The application is architected as a collection of discrete, pluggable modules, allowing for the rapid integration of new exploits and enabling users to load only the necessary tools for a given assessment.
3.  **Orchestration and Automation Engine:** A central "Automation Core" enables the chaining of multiple attack techniques into complex, automated workflows, transforming the application from a collection of tools into a true offensive framework.
4.  **Privilege-Aware Functionality:** Blu Snu operates in a tiered functional model (Standard, Elevated/Root, and Hardware-Assisted) to provide maximum utility across a wide range of Android devices and user permissions.

## Key Features

*   **Multi-Protocol Reconnaissance:** Simultaneous discovery of Bluetooth Classic and BLE devices, with automated service enumeration (SDP and GATT) and device fingerprinting.
*   **Vulnerability Correlation:** Automatic cross-referencing of discovered devices against a database of known vulnerabilities (CVEs) and exploit PoCs.
*   **Comprehensive Attack Modules:** A full suite of attack modules for both Classic and BLE protocols, including:
    *   **Classic (BR/EDR):** Bluesnarfing, Bluebugging, BlueSmack (L2CAP Flood), and the advanced BLUFFS attack suite (CVE-2023-24023).
    *   **BLE:** GATT-based Man-in-the-Middle (similar to Btlejuice), connection hijacking (Btlejacking), and GATT fuzzing.
*   **Advanced Signal Manipulation:** Device geolocation via RSSI analysis and targeted RF jamming using external SDR hardware.
*   **Attack Chaining Canvas:** A visual, node-based editor for designing, saving, and executing multi-stage attack workflows.
*   **Professional Reporting:** A robust engine for generating detailed penetration test reports that log all actions, targets, and results.

## Ethical Use Mandate

Blu Snu is a powerful tool designed exclusively for legitimate security research and professional penetration testing. Its use is subject to the following ethical mandate:

> **This tool is intended for use by security professionals for educational purposes and for security assessments on networks and devices for which you have received explicit, written authorization. Using this tool for unauthorized access or malicious activity is illegal. The developers assume no liability for its misuse.**

The application includes features, such as a mandatory disclaimer and a professional reporting engine, to encourage a methodical, accountable, and ethical approach to security testing.