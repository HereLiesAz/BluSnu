# Blu Snu Architecture Guide

## Overview

Blu Snu follows a strict **Model-View-ViewModel (MVVM)** architecture, built entirely with **Jetpack Compose** for the UI. The application is designed to be modular, testable, and robust, handling the asynchronous nature of Bluetooth operations and the stability risks of root-level execution.

## 1. The MVVM Pattern

### **View (UI Layer)**
*   **Technology:** Jetpack Compose.
*   **Location:** `app/src/main/java/com/hereliesaz/blusnu/ui/`
*   **Responsibility:** Rendering the UI based on the state provided by the ViewModel. The UI is purely reactive; it does not contain business logic.
*   **Key Components:**
    *   **Screens:** (e.g., `FindScreen.kt`, `BluffsScreen.kt`) Top-level composables that represent a full screen.
    *   **AzNavRail:** The application uses a custom navigation rail library (`AzNavRail`) for consistent navigation and UI components (`AzButton`, `AzTextBox`).

### **ViewModel (State Layer)**
*   **Technology:** Android ViewModel, Kotlin Coroutines, StateFlow.
*   **Location:** `app/src/main/java/com/hereliesaz/blusnu/ui/<feature>/`
*   **Responsibility:**
    *   Holds the UI state (usually a `data class`) in a `MutableStateFlow`.
    *   Exposes immutable `StateFlow` to the UI.
    *   Handles user events (e.g., `onScanClicked()`).
    *   Interacts with the Data Layer (Repositories/Modules) to perform actions.
    *   **Important:** ViewModels *never* hold references to Android Views or Composables.

### **Model (Data Layer)**
*   **Technology:** Room Database, Kotlin Coroutines, Android Bluetooth APIs.
*   **Location:** `app/src/main/java/com/hereliesaz/blusnu/data/`
*   **Responsibility:**
    *   **Repositories:** (e.g., `DeviceRepository`) Abstractions for data access (DB or Network).
    *   **Modules:** (e.g., `BluffsModule`, `SpoofingModule`) Encapsulate the logic for specific attacks.
    *   **Managers:** (e.g., `BluetoothScanner`, `HardwareManager`) Wrappers around Android system services.

---

## 2. Dependency Injection (DI)

Blu Snu currently uses **Manual Dependency Injection**.

*   **Container:** `MainActivity.kt` acts as the main dependency container.
*   **Instantiation:** All singletons (Database, Managers, Modules) are lazily instantiated in `MainActivity`.
*   **Factory:** A custom `ViewModelProvider.Factory` inside `MainActivity` is responsible for creating ViewModels and injecting the required dependencies into their constructors.

**Example Flow:**
1.  `MainActivity` creates `AppDatabase`.
2.  `MainActivity` creates `DeviceRepository` (passing the DB DAO).
3.  `MainActivity` creates `BluffsModule`.
4.  When the user navigates to the "Bluffs" screen, the factory creates `BluffsViewModel`, injecting `DeviceRepository` and `BluffsModule`.

---

## 3. Bluetooth Interaction Strategy

Handling Bluetooth on Android is notoriously difficult due to fragmentation and undocumented behaviors. Blu Snu uses several strategies:

*   **Context Safety:** The `BluetoothScanner` and other managers use the `ApplicationContext` to avoid memory leaks.
*   **Permission Handling:** All permissions (Scan, Connect, Location) are checked and requested in `MainActivity` before any Bluetooth operation is attempted.
*   **State Monitoring:** A `BroadcastReceiver` monitors `BluetoothAdapter.ACTION_STATE_CHANGED` to react to the user turning Bluetooth off/on externally.

---

## 4. Root & System Interaction

For advanced features (Spoofing, HCI Injection), Blu Snu requires Root access.

*   **Execution:** The app uses `ProcessBuilder` or `Runtime.getRuntime().exec()` to spawn `su` shells.
*   **Safety:** Root operations are wrapped in `try-catch` blocks. The app checks for root availability at startup (`isRootAvailable`).
*   **Standard vs. Root:** The app is designed to degrade gracefully. If Root is missing, the "Standard" features (Scanning, GATT Fuzzing) continue to work, while "Root" features (Spoofing) are disabled or show an error.

---

## 5. Navigation

Navigation is handled by the **Jetpack Navigation Compose** library, integrated with **AzNavRail**.

*   **Host:** A `NavHost` in `MainActivity` defines the graph.
*   **Routes:** String-based routes (e.g., `"dashboard"`, `"btlejuice?targetDevice={json}"`) are used.
*   **Rail Integration:** The `AzNavRail` component is rendered alongside the `NavHost`. It observes the current route to highlight the active tab.
