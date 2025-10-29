# Blu Snu: Screens

This document provides a detailed description of each screen in the Blu Snu application.

## Main Dashboard

The main dashboard is the central hub of the application. It provides a real-time overview of the local Bluetooth environment and the status of ongoing operations. The dashboard includes widgets for:

*   **Nearby Devices:** A summary of the number of discovered Bluetooth Classic and BLE devices.
*   **Active Tasks:** A list of any currently running processes, such as scans or attacks.
*   **Saved Sessions:** Quick access to previously saved reconnaissance sessions.
*   **Attack Chain Templates:** A library of pre-configured attack workflows.

## Device Management

This screen is the core of the reconnaissance phase. It displays a dynamic, filterable, and sortable list of all discovered devices. Users can filter devices by protocol, signal strength, vendor, and discovered services. Each device entry can be expanded to view a detailed profile, including its MAC address, name, services, and any potential vulnerabilities. This screen was formerly known as the Target Management screen.

## Attack Chaining

The Attack Chaining screen features a visual, node-based editor for designing, saving, and executing multi-stage attack workflows. Users can drag and drop nodes representing different actions or modules, connect them to define a logical flow, and create custom, automated assessment routines.

## Bluesnarfing

This screen provides the UI for the Bluesnarfing attack module. It allows the user to select a target device and attempt to exploit vulnerabilities in the OBEX protocol to retrieve data such as the device's phonebook, call history, and messages.

## Bluebugging

This screen provides the UI for the Bluebugging attack module. It allows the user to attempt to take control of a vulnerable device by injecting AT commands, which could enable actions like initiating phone calls or sending messages. This attack typically requires elevated (root) privileges.

## BlueSmack

This screen provides the UI for the BlueSmack (L2CAP Flood) attack module. It allows the user to launch a denial-of-service attack against a target device by sending a flood of oversized L2CAP echo request packets. The UI includes controls for configuring the packet size and transmission rate.

## Btlejacking

This screen provides the UI for the Btlejacking attack module, which requires external hardware. It allows the user to perform sniffing, jamming, and connection hijacking attacks against BLE devices.

## GATT Fuzzing

This screen provides the UI for the GATT Fuzzing module. It allows the user to probe a BLE device's GATT implementation for vulnerabilities by sending malformed data, testing for authorization bypasses, and checking for other common implementation flaws.

## Keystroke Injection

This screen provides the UI for the Keystroke Injection attack module (CVE-2023-45866). It allows the user to test if a host device is vulnerable to silent pairing with a fake HID (keyboard) device and, if successful, to send keystroke commands.

## Spoofing

This screen provides the UI for the Spoofing module. It allows the user to change the Bluetooth adapter's MAC address, which requires elevated (root) privileges.

## Geolocation

This screen provides a map-based view for device geolocation and tracking. It uses RSSI values to estimate the location of target devices and can display them on an OpenStreetMaps overlay.

## Reporting

This screen provides the UI for the professional reporting engine. It allows the user to generate detailed reports of an assessment, logging all actions, targets, and results in formats like PDF or Markdown.

## Settings

This screen provides a central location for configuring application settings and user preferences.

## Bluetooth Log

This screen displays a log of Bluetooth events and activities within the application, useful for debugging and monitoring.
