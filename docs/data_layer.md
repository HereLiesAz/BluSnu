# Blu Snu: Data Layer

This document describes the data layer of the Blu Snu application, including the unified protocol abstraction, database models, and data sources.

## Unified Protocol Abstraction

At the heart of the Blu Snu architecture lies a core library designed to provide a consistent Application Programming Interface (API) for interacting with both Bluetooth Classic and BLE targets. The Bluetooth specification is notoriously complex, with significant differences between the BR/EDR and LE physical and logical layers. Classic Bluetooth relies on the Service Discovery Protocol (SDP) for service enumeration over RFCOMM channels, while BLE utilizes the Generic Attribute Profile (GATT) for discovering services and characteristics. The abstraction layer will manage these protocol-specific details—including connection establishment, service discovery, and data transfer mechanisms—and present a unified "Target Device" object to the higher-level attack modules. This design simplifies the development of attack modules, allowing them to focus on exploit logic rather than protocol implementation nuances.

## Data Models

The primary data model in the Blu Snu application is the `TargetDevice` data class. This class is designed to hold all information for both Classic and BLE devices, providing a unified representation of a target. The `TargetDevice` data class includes the following properties:

*   **MAC Address:** The unique hardware address of the device.
*   **Name:** The broadcast name of the device.
*   **RSSI:** The Received Signal Strength Indication, used for distance estimation.
*   **Protocol Type:** An enumeration indicating whether the device is Bluetooth Classic, BLE, or Dual-Mode.
*   **Services:** A list of discovered services, either from SDP or GATT.
*   **Vulnerabilities:** A list of potential vulnerabilities associated with the device.

## Data Sources

The Blu Snu application draws data from two primary sources:

*   **Live Bluetooth Scanning:** The application's reconnaissance module performs simultaneous scanning for both Bluetooth Classic and BLE devices. This provides a real-time stream of data about the local RF environment.
*   **Internal Vulnerability Database:** The application includes a curated internal database of device fingerprints and known vulnerabilities. This database is populated with information from public sources like the National Vulnerability Database (NVD) and is enriched with the specific exploit catalogs from specialized Bluetooth security tools.

The application's fingerprinting engine uses the data from live scanning to query the internal vulnerability database, allowing for the automatic correlation of discovered devices with known security weaknesses.
