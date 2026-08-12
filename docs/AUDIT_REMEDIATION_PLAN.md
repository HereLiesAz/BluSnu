# BluSnu Audit Remediation Plan

Comprehensive plan to fix all 51 findings from two independent audits:
- **Code Correctness Audit** (GLEE): 4 broken, 14 unsound, 5 incomplete/rot, shell injection surface
- **UX Audit** (GLEE): 9 broken UX, 8 frustrating, 6 confusing, 3 missing features

## Phase 0: Shared Infrastructure (Foundation)

Must complete first. All other phases depend on this.

### 0A. MAC Address Validation Utility
**Findings addressed:** #19 (shell injection surface)

Create `utils/MacValidator.kt` -- extract `MAC_REGEX` from `SpoofingModule.kt` into a shared utility.

```kotlin
object MacValidator {
    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    fun isValid(mac: String): Boolean = MAC_REGEX.matches(mac)
    fun requireValid(mac: String): String {
        require(MAC_REGEX.matches(mac)) { "Invalid MAC address: $mac" }
        return mac
    }
}
```

Add `MacValidator.requireValid()` before every shell interpolation site:
- `BlueSmackModule.kt:111` (`executel2ping()`)
- `BluffsModule.kt:90` (RootExecutor call)
- `SmpBypassModule.kt:109,131,165` (btmgmt/hcitool calls)
- `PerfektBlueModule.kt:117` (`sdptool browse $mac`)
- `BlueSmackViewModel.kt:61` (direct RootExecutor call)

### 0B. Structured CoroutineScope Pattern
**Findings addressed:** #7, #8, #9, #10, #11

Every module with `CoroutineScope(Dispatchers.IO + Job())` gets a `SupervisorJob`-backed scope with a `close()` method:

- `BleSpamModule.kt:73` -- cancel in `stopSpam()`
- `BtlejackingModule.kt:48` -- cancel in `stop()`
- `BtlejuiceModule.kt:46` -- cancel in `stopProxy()`
- `HardwareManager.kt:45` -- cancel in `disconnect()`
- `TandemManager.kt:60` -- critical: `stopSession()` currently launches cleanup inside the scope it's stopping. Fix to synchronously clean up, then cancel.

### 0C. Thread-Safe ActionLogger
**Finding addressed:** #12

Replace `SimpleDateFormat` at `ActionLogger.kt:38` with `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` (thread-safe) or inline-create per call.

---

## Phase 1: Critical Correctness Bugs
**Depends on:** Phase 0. Sub-tasks parallel except 1A-1D (all touch GattRelayModule -- same agent).

### 1A. GattRelayModule -- discoverServices() hang
**Findings:** #1 (BROKEN), #20 (ROT)
**File:** `GattRelayModule.kt:559-589`

Create a single `GattRelayCallback` inner class with mutable continuation fields for both connection and discovery. `connectGattClient()` and `discoverServices()` share this callback instance. Delete the comment at 585-587 that acknowledges-but-defers the bug.

### 1B. GattRelayModule -- missing invokeOnCancellation
**Finding:** #5 (UNSOUND)
**File:** `GattRelayModule.kt:559-572`

After `connectGatt()`, add `cont.invokeOnCancellation { gattInstance?.close() }` to prevent native GATT slot leaks.

### 1C. GattRelayModule -- stale read on Node A
**Findings:** #2 (BROKEN), #21 (ROT)
**File:** `GattRelayModule.kt:225-229`

Add a `readContinuation` field to the merged callback. Wrap the read in `suspendCancellableCoroutine`, resume from `onCharacteristicRead` with actual data. Delete the "For simplicity" comment.

### 1D. GattRelayModule -- blocking TCP on Binder thread
**Finding:** #6 (UNSOUND)
**File:** `GattRelayModule.kt:315-343`

Queue GATT requests into a `Channel<GattRequest>`. A dedicated coroutine reads from the channel, does TCP I/O on `Dispatchers.IO`, and calls `gattServer.sendResponse()` with the result. Callbacks return immediately.

### 1E. Deprecated BLE write API
**Finding:** #17 (UNSOUND)
**Files:** `GattFuzzingModule.kt:152-159`, `GattRelayModule.kt:249-250`

Branch on `Build.VERSION.SDK_INT >= TIRAMISU`: new API `gatt.writeCharacteristic(char, data, writeType)` vs old API with `@Suppress("DEPRECATION")`.

### 1F. PerfektBlueModule -- crash detection always stable
**Findings:** #3 (BROKEN), #18 (UNSOUND)
**File:** `PerfektBlueModule.kt:241-252`

Replace `iStream.available() >= 0` with a blocking `iStream.read()` with `soTimeout = 1000ms`:
- EOF (-1) or IOException = `TARGET_CRASHED`
- SocketTimeoutException = `TARGET_STABLE`

Also replace `getDefaultAdapter()` at line 213 with `BluetoothManager.adapter` (requires passing `Context`).

### 1G. BlueSmackViewModel bypasses module
**Finding:** #4 (BROKEN)
**File:** `BlueSmackViewModel.kt:59-63`

Inject `BlueSmackModule` via factory. Replace direct `RootExecutor.execute()` with `blueSmackModule.startAttack(mac, packetSize, count).collect {}`. Store the collection `Job`. Add `stopAttack()` that cancels the job + `RootExecutor.execute("killall l2ping")`.

---

## Phase 2: Security & Data Integrity
**Depends on:** Phase 0A. Otherwise parallel with Phase 1.

### 2A. Path traversal in file transfer
**Finding:** #13 (UNSOUND)
**File:** `FileTransferController.kt:235`

Sanitize filename: `val sanitizedName = File(fileName).name` then verify `outFile.canonicalPath.startsWith(downloadsDir.canonicalPath)`.

### 2B. TCP stream framing
**Finding:** #14 (UNSOUND)
**File:** `FileTransferController.kt:239-259`

Add 4-byte length prefix to each data chunk. Sender writes `[MSG_FILE_DATA][4-byte length][data]`. Receiver reads type byte, reads length, reads exactly that many bytes via a readFully loop. Update both sides.

### 2C. CloudBackup -- no HTTPS or auth
**Finding:** #25 (INCOMPLETE)
**File:** `CloudBackup.kt:30-63`

Validate URL starts with `https://`. Accept optional auth token, set as `Authorization: Bearer` header.

### 2D. DatabaseUpdater -- HttpClient leak
**Finding:** #15 (UNSOUND)
**File:** `DatabaseUpdater.kt:29`

Add `close()` method calling `httpClient.close()`. Call from owning lifecycle.

### 2E. AttackChainExecutor -- unbuffered SharedFlow
**Finding:** #16 (UNSOUND)
**File:** `AttackChainExecutor.kt:22`

Change to `MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)`.

---

## Phase 3: Attack Lifecycle UX (Stop Buttons, Guards, Progress)
**Depends on:** Phase 0B + Phase 1G. All sub-tasks parallel.

### Common Pattern

Every attack ViewModel needs:
1. `_isRunning = MutableStateFlow(false)` exposed as `val isRunning: StateFlow<Boolean>`
2. Stored `attackJob: Job?` field
3. `startAttack()`: check `_isRunning.value == true` and return early. Set true, launch collection, store job. Set false in `finally`.
4. `stopAttack()`: cancel job, set false
5. `onCleared()`: cancel job

Every attack Screen needs:
1. Observe `isRunning`
2. Disable "Start" when running
3. Show "Stop" when running
4. Show `CircularProgressIndicator` when running

### 3A. BlueSmack
**Findings:** #27, #42
**Files:** `BlueSmackViewModel.kt`, `BlueSmackScreen.kt`

Apply common pattern. Add `OutlinedTextField` inputs for packet size, count, and interface name.

### 3B. Bluebugging
**Finding:** #28
**Files:** `BluebuggingViewModel.kt`, `BluebuggingScreen.kt`

Apply common pattern. `stopAttack()` calls `bluebuggingModule.disconnect()`.

### 3C. Bluesnarfing
**Finding:** #29
**Files:** `BluesnarfingViewModel.kt`, `BluesnarfingScreen.kt`

Apply common pattern.

### 3D. GATT Relay
**Finding:** #30
**File:** `GattRelayScreen.kt`

Add "Stop Relay" button calling `viewModel.stopRelay()` when `isRunning`. The ViewModel already has the method.

### 3E. PerfektBlue, SMP Bypass, BLUFFS
**Finding:** #31
**Files:** `PerfektBlueScreen.kt`, `SmpBypassScreen.kt`, `BluffsScreen.kt`

All three already have `isRunning` and spinners. Just add the Stop button to each screen.

---

## Phase 4: Navigation, Labels, and Wiring
**Independent -- can run parallel with Phases 1-3.**
**Note:** 4D + 4F both touch `MainActivity.kt` -- same agent.

### 4A. DisclaimerDialog
**Finding:** #26
**Files:** `DisclaimerDialog.kt:50`, `MainActivity.kt:278`

Rename "Decline and Accept" to "Decline". When declined, call `finish()` or keep showing the dialog.

### 4B. BtleJuice nav rail bug
**Finding:** #32
**File:** `MainActivity.kt:389`

Change `onStartProxy = { targetDevice?.let { viewModel.onStartProxy(it) } }` to `onStartProxy = { it?.let { dev -> viewModel.onStartProxy(dev) } }` so it uses the screen-local `selectedDevice`.

### 4C. Bluetooth Log
**Findings:** #33, #41
**Files:** `BluetoothLogScreen.kt`, `BluetoothLogViewModel.kt`

Add device picker dropdown (fixes #33). Replace `Environment.getExternalStoragePublicDirectory()` with `ActivityResultContracts.CreateDocument` via SAF (fixes #41).

### 4D. Nav rail grouping
**Finding:** #35
**File:** `MainActivity.kt:297-323`

Group into sections: Overview, Classic Attacks, BLE Attacks, Tools, Location, Advanced, Info. Fix labels: "Bugging" -> "Bluebugging", "Smack" -> "BlueSmack", "Jacking" -> "BtleJacking", "Juice" -> "BtleJuice", "SMP" -> "SMP Bypass".

### 4E. Device details -- "Attack with..." menu
**Finding:** #36
**File:** `DeviceManagementScreen.kt`

Replace single "BtleJuice" button with protocol-aware attack menu. Classic: Bluebugging/Bluesnarfing/BlueSmack/BLUFFS/PerfektBlue. BLE: GATT Fuzzing/BLE Spam/SMP Bypass/BtleJuice.

### 4F. Merge Find into Geolocation
**Finding:** #45
**Files:** `FindScreen.kt`, `GeolocationScreen.kt`, `MainActivity.kt`

Combine as tabs ("Track" / "Find"). Remove "Find" nav item.

### 4G. BLUFFS mode labels
**Finding:** #46
**Files:** `BluffsModule.kt`, `BluffsScreen.kt`

Add `description` property to `BluffsMode` enum. Display `"${mode.name}: ${mode.description}"`.

### 4H. Empty dropdown hints
**Finding:** #48
**Files:** `BluffsScreen.kt`, `PerfektBlueScreen.kt`, `SmpBypassScreen.kt`, `BrakToothScreen.kt`

Add disabled "No devices found -- run a scan first" menu item when device list is empty (copy pattern from `BlueSmackScreen`).

### 4I. Protocol-filtered dropdowns
**Finding:** #39

Filter `deviceRepository.allDevices` by protocol in each attack ViewModel: Classic attacks -> `CLASSIC`/`DUAL`, BLE attacks -> `BLE`/`DUAL`.

### 4J. "Start Scan" auto-starts
**Finding:** #43
**File:** `MainActivity.kt:339`

Navigate with `navController.navigate("targets?startScan=true")`. Add nav argument to route.

### 4K. Spoofing labels
**Finding:** #44
**File:** `SpoofingScreen.kt`

"Target Device" -> "Clone Identity From", "New MAC Address" -> "Spoof Adapter MAC To". Add explanatory help text.

### 4L. HID/File Transfer device source
**Finding:** #47
**Files:** `HidScreen.kt`, `FileTransferScreen.kt`

Add explanatory text: "Shows paired Bluetooth devices. Pair via system Settings first."

---

## Phase 5: Feature Wiring
**Independent -- parallel with other phases.**

### 5A. Dashboard Active Tasks
**Finding:** #49

Create `ActiveTaskManager` singleton (like `ActionLogger`). `DashboardViewModel` observes it. Attack ViewModels call `add()`/`remove()` on start/stop.

### 5B. Device list sort/filter
**Finding:** #50
**Files:** `DeviceManagementViewModel.kt`, `DeviceManagementScreen.kt`

Add `_sortOption` and `_filterProtocol` state flows. Derive visible list by combining with `allDevices`. Add `FilterChip` row and sort toggle to screen.

### 5C. SystemRequirementsDialog
**Finding:** #51
**File:** `MainActivity.kt`

After disclaimer accepted, check Bluetooth + Location enabled. Show dialog if not met. Fix hardcoded white background for dark theme.

### 5D. Interactive dashboard cards
**Finding:** #40
**File:** `DashboardScreen.kt`

Add `onClick` to `DashboardCard`. Sessions navigate to detail view. Templates navigate to Attack Chaining with template pre-loaded.

### 5E. Copy/export attack results
**Finding:** #37

All attack screens: add Copy (clipboard) and Share (`Intent.ACTION_SEND`) icon buttons below result area.

### 5F. Reporting captures actual results
**Finding:** #38

All attack ViewModels: after attack completes, call `ActionLogger.log("Result: ${result.take(200)}")` in addition to existing start/finish labels.

---

## Phase 6: Cleanup & Tests
**Depends on:** All prior phases.

### 6A. BluetoothScanner.destroy()
**Finding:** #23
**File:** `BluetoothScanner.kt:157-159`

Call `stopBleScan()` and `stopClassicDiscovery()` before `scope.cancel()`.

### 6B. Dead GattFuzzing no-op
**Finding:** #22
**File:** `GattFuzzingModule.kt:197-200`

Delete `executeAttack(device: BluetoothDevice)` method.

### 6C. AttackChainExecutorTest connector IDs
**Finding:** #24
**File:** `AttackChainExecutorTest.kt:29`

Fix test to use correct connector names matching `StartNode`/`WaitNode` declarations.

### 6D. Attack Chaining canvas usability
**Finding:** #34
**File:** `AttackChainingScreen.kt`

Add `detectTransformGestures` for zoom/pan, label connectors, add dismissable onboarding overlay, make log panel a collapsible bottom sheet.

---

## Dependency Graph

```
Phase 0 (Foundation)
  |
  +---> Phase 1 (Critical bugs)      [parallel]
  +---> Phase 2 (Security)           [parallel]
  +---> Phase 3 (Stop buttons)       [needs 0B + 1G]
  +---> Phase 4 (Nav/Labels/Wiring)  [independent]
  +---> Phase 5 (Feature wiring)     [independent]
           |
           +---> Phase 6 (Cleanup)   [after all]
```

## Parallelism Notes

Within each phase, all lettered sub-tasks can run as separate agents except:
- **1A-1D** all touch `GattRelayModule.kt` -- same agent
- **4D + 4F** both touch `MainActivity.kt` -- same agent

## Scope Estimate

~35 files changed, ~2,500 lines added/modified across all phases.
