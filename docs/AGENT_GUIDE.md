# Blu Snu: Agent AI Development Guide

## 1. Executive Summary

Blu Snu is an offensive Bluetooth security framework for Android. The goal is a production-quality application that unifies Bluetooth Classic and BLE auditing directly from an Android device. The UI shell and navigation are complete. Attack modules that require Root execute real low-level Bluetooth stack operations via `su`; hardware modules interface with connected ESP32, Ubertooth, or SDR peripherals.

**Core Objective:** Maintain and extend the framework — add new CVE modules, fix architectural issues, and keep the implementation current with new vulnerability research.

## 2. Environment Setup & Build

### Prerequisites
-   **Java:** OpenJDK 17 (Strictly required).
-   **Android SDK:** API Level 34+.
-   **Gradle:** Version 8.13 (Required by AGP 8.13.0).

### Common Build Errors & Fixes
**1. Missing Gradle Wrapper:**
If the build fails with `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`, the `gradle-wrapper.jar` is missing.
*   **Fix:** You must bootstrap the wrapper. Create a temporary folder with a dummy `build.gradle` and `settings.gradle`, run `gradle wrapper --gradle-version 8.13`, and copy the resulting `gradle/` directory and `gradlew` scripts to the project root.

**2. Kotlin Version Compatibility:**
The project uses Kotlin 2.2.20. Ensure your `JAVA_HOME` points to JDK 17.

## 3. Current State Analysis

### Module Status Overview
-   **Navigation:** `AzNavRail` is integrated and working.
-   **Dashboard:** Live data from `ActiveTaskManager`, `DeviceRepository`, and the Room database.
-   **Device Discovery:** `BluetoothScanner` — Classic + BLE scanning via public Android APIs.
-   **Database:** Room database (`TargetDevice`, `SavedSession`).
-   **Root Modules:** `SpoofingModule`, `KeystrokeInjectionModule`, and all CVE attack modules require `su` on the device. Root path uses raw HCI or L2CAP sockets.
-   **Hardware Modules:** `HardwareManager` interfaces with connected USB peripherals (ESP32 via USB-OTG serial, Ubertooth One, CC26xx running Sniffle firmware).

### Critical Bugs & Architectural Issues
1.  **Attack Chain Templates Crash:**
    -   *File:* `data/AttackChainTemplates.kt`
    -   *Issue:* `simpleScanTemplate` tries to connect `StartNode` to `ScanBleNode`, but `ScanBleNode` has `inputs = emptyList()`. This will crash at runtime.
    -   *Fix:* Update `ScanBleNode` to accept an input trigger.
2.  **Serialization of Polymorphic Types:**
    -   *File:* `data/AttackChainRepository.kt`
    -   *Issue:* Uses `Gson` to serialize `AttackChainingState`, which contains a Map of `AttackNode` interfaces. Gson cannot deserialize interfaces back to concrete types (e.g., `BluesnarfNode`) without a custom `TypeAdapter`.
    -   *Fix:* Implement a `RuntimeTypeAdapterFactory` for Gson or switch to `kotlinx.serialization` with polymorphic support.

## 4. Step-by-Step Implementation Roadmap

### Phase 1: Core Stabilization (High Priority)
1.  **Fix Gradle Wrapper:** Ensure the wrapper is committed and working (Completed in this session).
2.  **Fix Attack Chain Templates:** Modify `ScanBleNode` in `AttackNode.kt` to include a generic input (e.g., "start") so it can be connected.
3.  **Fix Serialization:** Implement JSON serialization for `AttackNode` to support saving/loading chains.

### Phase 2: Attack Chain Canvas Completion
1.  **Enhance `AttackChainingScreen`:**
    -   Improve `Canvas` drawing to use dynamic Bézier curves instead of simple lines.
    -   Implement logic to dynamically calculate connector positions based on node size (not hardcoded offsets).
2.  **Implement Logic Execution:**
    -   Flesh out `AttackChainExecutor` to handle real data passing between nodes (e.g., passing a `TargetDevice` from `ScanNode` to `BluesnarfNode`).

### Phase 3: Real-World Implementation
1.  **Hardware Integration:**
    -   Import `com.github.mik3y:usb-serial-for-android`.
    -   Rewrite `HardwareManager` to enumerate USB devices and open serial connections.
2.  **Root Actions:**
    -   Implement a `ShellUtils` helper to execute root commands (`su`).
    -   Update `SpoofingModule` to use `ip link set hci0 address ...` or vendor-specific commands.

### Phase 4: Verification
1.  **Unit Tests:** Write tests for `AttackChainExecutor`.
2.  **Instrumentation Tests:** Verify UI flows.

## 5. Developer Notes
-   **Architecture:** MVVM with `ViewModelProvider.Factory`.
-   **UI:** Jetpack Compose with `AzNavRail`.
-   **Navigation:** `TargetDevice` objects are passed as JSON strings in navigation arguments.
-   **Documentation:** Always update `docs/TODO.md` after completing a task.

**End of Guide**
