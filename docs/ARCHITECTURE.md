# Blu Snu Architecture

## Overview
Blu Snu is a comprehensive, mobile-first offensive security framework for Bluetooth, designed to unify the assessment of both Bluetooth Classic (BR/EDR) and Bluetooth Low Energy (BLE) systems. It abstracts hardware and protocol complexities to provide a unified platform for security professionals.

## Core Architectural Principles

### 1. Unified Protocol Abstraction
A core library provides a consistent API for interacting with both Bluetooth Classic and BLE targets.
- **Classic (BR/EDR)**: Manages SDP for service enumeration over RFCOMM.
- **BLE**: Utilizes GATT for discovering services and characteristics.
- **Abstraction**: Presents a unified `TargetDevice` object to higher-level attack modules.

### 2. Modular Attack Framework
The application is architected as a collection of discrete, pluggable modules.
- **Isolation**: Each attack vector (e.g., Bluesnarfing, Btlejacking) is a self-contained module.
- **Pluggability**: Modules can be loaded independently, optimizing performance.

### 3. Orchestration and Automation Engine
A central "Automation Core" enables chaining multiple attack techniques.
- **Attack Chaining Canvas**: A node-based editor for designing workflows.
- **State Management**: Passes output from one module (e.g., MAC address) as input to another.

### 4. Privilege-Aware Functionality
The architecture operates in a tiered functional model:
- **Standard Mode (Non-Root)**: Uses standard Android Bluetooth APIs (`BluetoothAdapter`, `BluetoothLeScanner`).
- **Elevated Mode (Root Access)**: Uses `su` to execute Linux utilities (`hcitool`, `btmgmt`) and native code.
- **Hardware-Assisted Mode**: Offloads tasks to external hardware (e.g., nRF52 sniffers) via USB/OTG.

## System Components

### Data Layer (`com.hereliesaz.blusnu.data`)
- **Core Models**: `TargetDevice`, `ActiveTask`, `BluetoothLog`.
- **Repositories**: `DeviceRepository`, `AttackChainRepository`.
- **Managers**:
    - `HardwareManager`: Manages external dongles and radio modes.
    - `LocationManager`: Handles GPS and geolocation logic.
    - `TandemManager`: Manages cooperative triangulation with other devices.
- **Modules**: Implementation of specific attacks (e.g., `BluffsModule`, `SpoofingModule`).

### UI Layer (`com.hereliesaz.blusnu.ui`)
- **Architecture**: MVVM (Model-View-ViewModel) using Jetpack Compose.
- **Navigation**: `NavigationMenu` defines the structure.
- **Components**: Reusable UI elements (`LeafletMapView`, `DisclaimerDialog`).
- **Screens**: One package per feature (e.g., `ui/spoofing/`).

### Utilities (`com.hereliesaz.blusnu.utils`)
- **RootExecutor**: Handles execution of shell commands with root privileges.
- **Trilateration**: Mathematical utilities for position calculation.

## Data Flow
1.  **Reconnaissance**: `BluetoothScanner` populates `DeviceRepository` with `TargetDevice` entities.
2.  **Vulnerability Analysis**: `VulnerabilityCorrelator` enriches devices with CVE data.
3.  **Attack Execution**: Users select a target -> ViewModel invokes Data Module -> Module executes logic (API/Root) -> Results logged to `ActionLogger`.
4.  **Reporting**: `ReportViewModel` aggregates logs and generates exportable reports.
