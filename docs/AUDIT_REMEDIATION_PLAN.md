# BluSnu GLEE Audit Remediation Plan v2

163 findings from 14 adversarial GLEE agents + 24 missing attack vectors from research.
Each module is remediated as a phase with every finding addressed individually.
After each phase, a dedicated GLEE re-audit agent verifies fixes before moving on.

**Totals:** 52 broken, 50 unsound, 33 incomplete, 22 rot/dead, 6 unsupported

---

## Phase 0: Cross-Cutting Infrastructure

Shared patterns referenced by every module phase. Must complete first.

### 0.1 CancellationException Rethrow Pattern
**Modules affected:** Bluesnarfing, Bluebugging, BlueSmack, BLE Spam
**Pattern:** Every `catch (e: Exception)` that could catch a coroutine cancellation must rethrow:
```kotlin
catch (e: Exception) {
    if (e is CancellationException) throw e
    // handle real error
}
```
Apply at every catch-all site identified in module phases below.

### 0.2 @Volatile on Cross-Thread Mutable Fields
**Modules affected:** Bluebugging, GATT Fuzzing, BtleJack
**Pattern:** Any `var` field written by a GATT/BLE callback and read by a coroutine gets `@Volatile`.

### 0.3 SupervisorJob Pattern
**Modules affected:** BLE Spam, BtleJack, BtleJuice, Attack Chaining, App Cohesiveness
**Pattern:** Replace `CoroutineScope(Dispatchers.IO + Job())` with `CoroutineScope(Dispatchers.IO + SupervisorJob())` and add `close()` that calls `scope.cancel()`.

### 0.4 MacValidator Enforcement
**Modules affected:** BtleJack, BtleJuice, BLUFFS+BrakTooth, Bluesnarfing, Spoofing
**Pattern:** Call `MacValidator.requireValid(mac)` before any string interpolation into shell commands or serial commands.

### 0.5 onCleared Lifecycle Pattern
**Modules affected:** Keystroke Injection, BtleJack, BtleJuice, BLE Spam, BlueSmack, App Cohesiveness
**Pattern:** Every ViewModel that launches coroutines or holds hardware resources overrides `onCleared()` to cancel jobs, close connections, and release resources.

### 0.6 ActiveTaskManager + ActionLogger Integration
**Modules affected:** App Cohesiveness (11 of 14 VMs missing ActiveTaskManager, 6 missing ActionLogger)
**Pattern:** Every attack ViewModel calls `ActiveTaskManager.add()` on start, `remove()` on stop/complete, and `ActionLogger.log()` on start/result/error.

---

## Phase 1: GATT Relay (16 findings)

The relay module is the most broken in the app. Two concurrent TCP readers corrupt framing, notifications are dead, wrong BluetoothProfile constant, and the UI mislabels IP as MAC.

### 1.1 BROKEN: onCharacteristicChanged not overridden
**File:** `GattRelayModule.kt`
**Fix:** Override `onCharacteristicChanged(gatt, characteristic, value)` (API 33+ signature) AND the deprecated `onCharacteristicChanged(gatt, characteristic)` (API <33). Forward the notification value over the TCP backchannel to Node B using the existing length-prefixed framing with a new `MSG_NOTIFICATION` type byte.

### 1.2 BROKEN: Two concurrent TCP stream readers corrupt framing
**File:** `GattRelayModule.kt`
**Fix:** Replace dual read loops with a single `tcpReaderCoroutine` that reads length-prefixed frames sequentially from the TCP socket. Demultiplex by message type byte and dispatch to the appropriate handler (read response, write response, notification).

### 1.3 BROKEN: responseNeeded mismatch — response sent before relay acknowledgment
**File:** `GattRelayModule.kt`
**Fix:** When `responseNeeded == true`, defer `gattServer.sendResponse()` until the TCP round-trip completes. Queue the request into a `Channel<GattRequest>` and process asynchronously on `Dispatchers.IO`. Send GATT response only after receiving the relay acknowledgment.

### 1.4 BROKEN: Null characteristic lookup deadlocks Node B
**File:** `GattRelayModule.kt`
**Fix:** When UUID lookup returns null, log "Unknown characteristic UUID" and send an error response back over TCP instead of hanging. Add a `withTimeout(5000)` around every `CompletableDeferred.await()`.

### 1.5 BROKEN: Node A mode crashes — no address input in UI
**File:** `GattRelayScreen.kt`
**Fix:** Add a second `OutlinedTextField` for the target BLE device MAC address. Show it only when mode is Node A. Wire it through the ViewModel to `GattRelayModule.connectToTarget(macAddress)`.

### 1.6 BROKEN: addService tight loop violates Android async requirement
**File:** `GattRelayModule.kt`
**Fix:** Replace the for-loop with a sequential coroutine that adds one service at a time, awaiting `onServiceAdded` via `suspendCancellableCoroutine` before adding the next. Add a 200ms timeout per service.

### 1.7 BROKEN: Wrong BluetoothProfile constant (GATT vs GATT_SERVER)
**File:** `GattRelayModule.kt`
**Fix:** Replace `BluetoothProfile.GATT` with `BluetoothProfile.GATT_SERVER` in the `onConnectionStateChange` callback and in `openGattServer()`.

### 1.8 BROKEN: UI label says MAC but Socket() needs IP address
**File:** `GattRelayScreen.kt`
**Fix:** Rename label from "Target MAC" to "Partner IP Address". Add input validation for IPv4 format. Add help text: "Enter the IP address of the other BluSnu node."

### 1.9 UNSOUND: readCharacteristicAsync can hang forever
**File:** `GattRelayModule.kt`
**Fix:** Wrap `CompletableDeferred.await()` in `withTimeout(5000)`. On timeout, emit log "Read timed out for characteristic {uuid}" and return an error result.

### 1.10 UNSOUND: Concurrent cleanup without synchronization
**File:** `GattRelayModule.kt`
**Fix:** Add a `Mutex` for cleanup. Wrap `close()`/`disconnect()` in `mutex.withLock { ... }`. Add an `isClosed` flag checked at the top of cleanup.

### 1.11 UNSOUND: writeDescriptor loop without callback waits
**File:** `GattRelayModule.kt`
**Fix:** Like 1.6, use `suspendCancellableCoroutine` per descriptor write, awaiting `onDescriptorWrite` before proceeding to the next.

### 1.12 UNSOUND: TCP backchannel has no auth/TLS on 0.0.0.0:9876
**File:** `GattRelayModule.kt`
**Fix:** Add a shared secret (user-entered passphrase). First TCP message is a 32-byte HMAC-SHA256 of the passphrase. Receiver verifies before accepting commands. Bind to specific interface IP instead of 0.0.0.0. Log warning about plaintext.

### 1.13 INCOMPLETE: No tests for any GATT Relay component
**Fix:** Add unit tests for TCP framing (encode/decode length-prefixed messages), characteristic UUID lookup, and the sequential service-add coroutine.

### 1.14 ROT: Dead MSG_DESC constants never referenced
**Fix:** Delete unused `MSG_DESC_READ`, `MSG_DESC_WRITE` constants.

### 1.15 ROT: Comment rot across multiple docstrings
**Fix:** Delete or rewrite all comments that describe behavior the code doesn't implement.

### 1.16 ROT: Write response sent before relay acknowledgment
**Fix:** Already addressed by 1.3. Remove redundant early `sendResponse()` call.

### Re-audit checkpoint
Launch GLEE agent targeting `GattRelayModule.kt`, `GattRelayScreen.kt`, and `GattRelayViewModel.kt`. Verify all 16 findings resolved. Agent should attempt the full relay flow: Node A connects to target, Node B advertises cloned services, data relayed bidirectionally.

---

## Phase 2: Keystroke Injection (12 findings)

The BLE HID keyboard emulation cannot inject a single keystroke. The long-read offset bug corrupts the 101-byte Report Map, addService race drops the HID service, and zero-delay keystroke firing produces garbled input.

### 2.1 BROKEN: onCharacteristicReadRequest ignores offset for long reads
**File:** `BleHidController.kt:96-102`
**Fix:** Handle the `offset` parameter in `onCharacteristicReadRequest`. For the Report Map descriptor (101 bytes at default MTU 23):
```kotlin
val value = characteristic.value ?: byteArrayOf()
val chunk = value.copyOfRange(offset, minOf(offset + mtu - 1, value.size))
gattServer.sendResponse(device, requestId, GATT_SUCCESS, offset, chunk)
```

### 2.2 BROKEN: Three addService calls without waiting for onServiceAdded
**File:** `BleHidController.kt:211,228,308`
**Fix:** Same pattern as GATT Relay 1.6. Add services sequentially via `suspendCancellableCoroutine`, awaiting `onServiceAdded` callback between each. Order: Device Info -> Battery -> HID.

### 2.3 BROKEN: No delay between key press and release notifications
**File:** `BleHidController.kt:354-358`
**Fix:** Add `delay(10)` between press and release notifications. Check `notifyCharacteristicChanged()` return value — if false, retry after `delay(20)`.

### 2.4 BROKEN: typeString fires hundreds of notifications in tight CPU loop
**File:** `BleHidController.kt:360-363`
**Fix:** Add `delay(8)` between each character's press+release pair. Check `notifyCharacteristicChanged()` return value; if false (queue full), back off with `delay(50)` and retry up to 3 times.

### 2.5 BROKEN: Advertising failure doesn't update connectionState
**File:** `BleHidController.kt:148-151`
**Fix:** In `onStartFailure`, set `connectionState = ConnectionState.ERROR` and emit error log. Callers checking state will see the failure immediately instead of waiting 30s.

### 2.6 BROKEN: DuckyScript GUI command silently dropped
**File:** `KeystrokeInjectionViewModel.kt:77-80`
**Fix:** Implement `GUI` (and `WINDOWS`, `COMMAND` aliases) by sending the appropriate modifier key code. Map `GUI r` to `LEFT_META + r`, `GUI d` to `LEFT_META + d`, etc.

### 2.7 UNSOUND: No onCleared — GATT server and advertising never cleaned up
**File:** `KeystrokeInjectionViewModel.kt`
**Fix:** Override `onCleared()`. Call `bleHidController.close()` which stops advertising, closes GATT server, and cancels scope. Guard `initialize()` against re-entry with an `isInitialized` flag.

### 2.8 UNSOUND: device parameter accepted but never used
**File:** `KeystrokeInjectionModule.kt:33`
**Fix:** Store the target device address. In the GATT server's `onConnectionStateChange`, verify `device.address == targetAddress`. Reject connections from non-target devices with `gattServer.cancelConnection(device)`.

### 2.9 UNSOUND: sendKeystrokes returns true unconditionally
**File:** `KeystrokeInjectionModule.kt:61-68`
**Fix:** Track each `notifyCharacteristicChanged()` return value. Count failures. Return false if >10% of keystrokes failed. Log per-character success/failure.

### 2.10 UNSOUND: Two independent BleHidController instances
**File:** `MainActivity.kt:129 + HidViewModel.kt:38`
**Fix:** Make `BleHidController` a singleton (or provide via DI). Both ViewModels share the same instance. Second `openGattServer` call reuses existing server if already open.

### 2.11 INCOMPLETE: KEYBOARD_REPORT_DESCRIPTOR and MOUSE_REPORT_DESCRIPTOR unused
**File:** `HidKeyMap.kt:165-220`
**Fix:** Delete the unused descriptors, or wire them into `BleHidController` if they're the correct ones (compare against the hardcoded descriptor in BleHidController).

### 2.12 ROT: Docstring says "pair with the target device" but code accepts any device
**File:** `KeystrokeInjectionModule.kt:29-31`
**Fix:** Delete the misleading docstring. Behavior will match reality after fix 2.8.

### Re-audit checkpoint
Launch GLEE agent targeting `BleHidController.kt`, `KeystrokeInjectionModule.kt`, `KeystrokeInjectionViewModel.kt`, `HidViewModel.kt`, `HidKeyMap.kt`. Verify HID Report Map survives long reads, services register sequentially, keystrokes fire with pacing, DuckyScript GUI command works.

---

## Phase 3: BtleJack + BtleJuice (14 findings)

Three components, zero lifecycle cleanup, wrong CVE citation, docstring describing unbuilt architecture, and port leaks on every failed connection.

### 3.1 BROKEN: close() is dead code — scopes and USB ports never released
**File:** `BtlejackingModule.kt:157`, `BtlejuiceModule.kt:110`, `HardwareManager.kt:145`
**Fix:** Add `onCleared()` to both ViewModels. Call `module.close()` which calls `hardwareManager.disconnect()`. `disconnect()` cancels scope, closes USB port, stops read loop.

### 3.2 BROKEN: Port opened but leaked if setParameters throws
**File:** `HardwareManager.kt:75-95`
**Fix:** Wrap `setParameters()` in try-catch. On failure, call `port.close()` before rethrowing. Use a `try { port.open(); port.setParameters(...) } catch { port.close(); throw }` pattern.

### 3.3 BROKEN: connect() has no re-entrancy guard
**File:** `HardwareManager.kt:53-97`
**Fix:** Add a `Mutex`. `connect()` acquires it. If `primaryPort != null`, disconnect first, then connect the new port. Store reference only after successful setup.

### 3.4 BROKEN: startSniffing() overwrites attackJob without cancelling previous
**File:** `BtlejackingModule.kt:61-85`
**Fix:** Cancel existing `attackJob` before launching new one: `attackJob?.cancel(); attackJob = scope.launch { ... }`.

### 3.5 BROKEN: disconnect() performs blocking USB I/O on main thread
**File:** `HardwareManager.kt:135-143`
**Fix:** Wrap USB close operations in `withContext(Dispatchers.IO) { port.close() }`. Or change the ViewModel scope to use `Dispatchers.IO` instead of `Dispatchers.Main.immediate`.

### 3.6 UNSOUND: disconnect() and sendCommand() race on primaryPort
**File:** `HardwareManager.kt:135-142`
**Fix:** Mark `primaryPort` as `@Volatile`. Use the same `Mutex` from 3.3 to guard both `disconnect()` and `sendCommand()`.

### 3.7 UNSOUND: isActive check in collect lambda does not stop collection
**File:** `BtlejuiceModule.kt:70`
**Fix:** Replace `if (!isActive) return@collect` with a structured approach: launch the collection in a `Job`, cancel the job from `stopProxy()`.

### 3.8 UNSOUND: MAC address interpolated into serial command with no validation
**File:** `BtlejackingModule.kt:64`, `BtlejuiceModule.kt:65`
**Fix:** Call `MacValidator.requireValid(mac)` before building the serial command string. This prevents `\r\n` injection.

### 3.9 UNSOUND: Log emissions via scope.launch unbounded
**File:** `HardwareManager.kt:189-217`
**Fix:** Replace per-line `scope.launch { _log.emit(line) }` with `_log.tryEmit(line)` (non-suspending, drops if buffer full). Or use `MutableSharedFlow(extraBufferCapacity = 256)`.

### 3.10 UNSOUND: Log/traffic lists grow without bound
**File:** ViewModels
**Fix:** Cap log lists at 1000 entries. When adding a new entry beyond the cap, drop the oldest: `if (list.size > 1000) list.removeFirst()`.

### 3.11 UNSOUND: getSecondaryRssi() performs blocking I/O on caller's thread
**File:** `HardwareManager.kt:166-175`
**Fix:** Make the function `suspend` and wrap blocking I/O in `withContext(Dispatchers.IO)`.

### 3.12 ROT: Docstring cites CVE-2018-7252 — which is a Samba vulnerability
**File:** `BtlejackingModule.kt:28`
**Fix:** Delete the CVE reference entirely. BtleJack exploits BLE connection-following, not any specific CVE.

### 3.13 ROT: Docstring claims dual-radio proxying but module uses only primary port
**File:** `BtlejuiceModule.kt:29-30`
**Fix:** Rewrite docstring to accurately describe single-radio serial-command proxying via external hardware.

### 3.14 INCOMPLETE: No onCleared() in either ViewModel
**Fix:** Already addressed by 3.1.

### Re-audit checkpoint
Launch GLEE agent targeting `BtlejackingModule.kt`, `BtlejuiceModule.kt`, `HardwareManager.kt`, and both ViewModels. Verify port lifecycle (open/close/reconnect), command injection safety, log bounding, and proper cleanup on navigation.

---

## Phase 4: Bluesnarfing (12 findings)

Uses authenticated RFCOMM which defeats the entire purpose. OBEX framing is broken end to end.

### 4.1 BROKEN: Uses authenticated RFCOMM — defeats entire purpose of Bluesnarfing
**Fix:** Replace `createRfcommSocketToServiceRecord(OBEX_PUSH_UUID)` with `createInsecureRfcommSocketToServiceRecord(OBEX_PUSH_UUID)`. This uses unauthenticated RFCOMM, which is what real Bluesnarfing exploits. On failure, fall back to reflection-based `createRfcommSocket(channel)`.

### 4.2 BROKEN: OBEX Name header missing null terminator
**Fix:** When building the OBEX GET request Name header, append UTF-16BE null terminator (`0x00, 0x00`) after the filename bytes. Recalculate header length accordingly.

### 4.3 BROKEN: Hardcoded Connection ID not parsed from CONNECT response
**Fix:** Parse the OBEX CONNECT response. Extract the Connection ID header (tag `0xCB`, 4-byte value). Use the extracted ID in subsequent GET requests instead of hardcoding.

### 4.4 BROKEN: Naive byte search for Body header finds false matches
**Fix:** Implement proper OBEX header parsing. Read headers sequentially: 1-byte tag, then length (varies by tag type), then value. Don't scan raw bytes for `0x48`/`0x49` tags.

### 4.5 BROKEN: No multi-packet OBEX support
**Fix:** After receiving a `CONTINUE` (0x90) response code, send another GET request (with same headers minus Connection ID) to request the next packet. Concatenate Body payloads until `SUCCESS` (0xA0) response with End-of-Body (0x49) header.

### 4.6 BROKEN: No OBEX Disconnect sent
**Fix:** After file retrieval (or on error), send OBEX DISCONNECT request (opcode `0x81`, length 3) before closing the RFCOMM socket.

### 4.7 UNSOUND: CancellationException swallowed in catch-all
**Fix:** Apply pattern 0.1. Rethrow `CancellationException` in the main attack try-catch.

### 4.8 UNSOUND: stopAttack can't interrupt blocking RFCOMM socket read
**Fix:** Close the RFCOMM socket from `stopAttack()`. The blocking `read()` will throw `IOException`, which the catch block handles. Store socket reference as `@Volatile`.

### 4.9 UNSOUND: No upper bound on response length
**Fix:** Add a maximum response size constant (e.g., 10 MB). Track cumulative bytes read. If exceeded, close connection and report "Response too large."

### 4.10 INCOMPLETE: No MacValidator usage
**Fix:** Call `MacValidator.requireValid(mac)` at the top of `startAttack()`.

### 4.11 ROT: Comment rot — Body vs End-of-Body, FTP claim
**Fix:** Delete comments claiming FTP support. Fix Body/End-of-Body references to match actual OBEX header tag values used.

### 4.12 INCOMPLETE: Stale results not cleared between runs
**Fix:** Clear `_results` MutableStateFlow at the start of `startAttack()` before emitting new data.

### Re-audit checkpoint
Launch GLEE agent targeting `BluesnarfingModule.kt`, `BluesnarfingViewModel.kt`. Verify OBEX framing is correct (null terminators, Connection ID parsing, multi-packet support, Disconnect), unauthenticated RFCOMM is used, and socket lifecycle is clean.

---

## Phase 5: SMP Bypass (12 findings)

Does not implement CVE-2024-34722. Runs standard Just Works pairing and reports every BLE device as "vulnerable."

### 5.1 BROKEN: Doesn't implement CVE-2024-34722 — runs standard Just Works pairing
**Fix:** The Android API cannot perform raw SMP operations. Reframe the module honestly: rename to "SMP Pairing Auditor." Test what pairing method the target accepts (Just Works, Passkey, Numeric Comparison, OOB). Report the accepted method and whether it's considered secure. Remove claims of CVE exploitation.

### 5.2 BROKEN: False positive vulnerability on every BLE device without display
**Fix:** Remove the heuristic that marks "no display" as vulnerable. Instead, report the actual pairing method negotiated and let the user assess risk. Only flag as "weak" if Just Works pairing succeeds on a device that should require stronger auth.

### 5.3 BROKEN: Error text containing "Paired" triggers false positive
**Fix:** Check the actual pairing bond state via `device.bondState == BluetoothDevice.BOND_BONDED` instead of string-matching error messages.

### 5.4 BROKEN: Hardcoded LE Public address type fails on random addresses
**Fix:** Use `BluetoothDevice.ADDRESS_TYPE_RANDOM` when the address starts with a random-address prefix. Or use `device.type` to determine the correct transport and let the Android stack handle address type.

### 5.5 UNSOUND: Sequential stdout/stderr deadlock in RootExecutor
**Fix:** Read stdout and stderr concurrently using two separate coroutines or threads. Waiting for stdout to finish before reading stderr can deadlock if the stderr buffer fills first.

### 5.6 UNSOUND: LE connection never disconnected — leaks HCI slots
**Fix:** After pairing test completes, call `device.removeBond()` (via reflection if needed) and disconnect the GATT client. Track the `BluetoothGatt` instance and call `close()` in finally block.

### 5.7 UNSOUND: Not cancellable — no Job reference, no onCleared
**Fix:** Store the attack `Job`. Override `onCleared()` to cancel it. Add a stop button in the UI.

### 5.8 UNSOUND: btmgmt find blocks with no timeout
**Fix:** Add a timeout to the btmgmt command: `withTimeout(15_000) { ... }`. Or use `Process.waitFor(timeout, unit)` instead of blocking indefinitely.

### 5.9 INCOMPLETE: Null device name shows "Select Target" after selection
**Fix:** Display `device.address` as fallback when `device.name` is null. Show "Unknown Device (AA:BB:CC:DD:EE:FF)" format.

### 5.10 ROT: Docstring claims raw SMP operations
**Fix:** Rewrite docstring to accurately describe the module's actual behavior (BLE pairing method auditing via standard Android APIs).

### 5.11 INCOMPLETE: No tests
**Fix:** Add unit tests for pairing-method classification logic and address-type detection.

### 5.12 INCOMPLETE: No auto-unpair after test
**Fix:** After the pairing audit completes, call `device.removeBond()` via reflection to clean up the test bond. Log whether unpair succeeded.

### Re-audit checkpoint
Launch GLEE agent targeting `SmpBypassModule.kt`, `SmpBypassViewModel.kt`, `SmpBypassScreen.kt`. Verify honest framing (no false CVE claims), correct pairing-method detection, proper cleanup, and no false positives.

---

## Phase 6: Spoofing (11 findings)

MAC validation divergence between UI and backend, no original MAC restore, immortal MITM coroutines.

### 6.1 BROKEN: MAC format validation divergence
**File:** `SpoofingViewModel.kt`
**Fix:** Replace the ViewModel's inline regex with `MacValidator.isValid(mac)`. MacValidator accepts colon-separated only. UI `OutlinedTextField` gets a visual transform that auto-inserts colons.

### 6.2 BROKEN: RootExecutor error messages bypass startsWith("Error") check
**Fix:** Check RootExecutor result for both `startsWith("Error")` and exit code. RootExecutor should return a sealed result: `Success(output)` or `Failure(error, exitCode)`.

### 6.3 BROKEN: Dual macAddress state sources cause inconsistency
**Fix:** Remove the duplicate `macAddress` field. Use a single `MutableStateFlow<String>` as the source of truth for the current MAC address.

### 6.4 UNSOUND: No original MAC restore on cleanup
**Fix:** Read the original MAC address before spoofing (via `hciconfig hci0` output parsing). Store it. In `onCleared()` and in an explicit "Restore" button, execute `hcitool cmd ... $originalMac`.

### 6.5 UNSOUND: Immortal MITM collector coroutines
**Fix:** Store the collection `Job`. Cancel it in `stopMitm()` and `onCleared()`.

### 6.6 UNSOUND: MitmAttack fire-and-forget timing
**Fix:** Instead of fire-and-forget, await the MITM setup coroutine. Report setup success/failure to the UI before considering the attack "started."

### 6.7 UNSOUND: CSR-specific HCI vendor command — only works on one chipset
**Fix:** Document the limitation clearly in the UI. Add runtime detection: try the CSR vendor command first, if it fails try the Broadcom variant, then the generic `bdaddr` tool. Show which method worked or "Unsupported chipset."

### 6.8 UNSOUND: ViewModel leaks MutableStateFlow
**Fix:** Override `onCleared()`. Cancel all running coroutines. The StateFlows themselves don't leak but the coroutines collecting them do.

### 6.9 INCOMPLETE: No name spoofing despite UI implying it
**Fix:** Either implement BLE name spoofing (change `BluetoothAdapter.name` — straightforward API call) or remove the UI element that implies it's available.

### 6.10 INCOMPLETE: No tests
**Fix:** Add unit tests for MAC validation, MAC format normalization, and result parsing.

### 6.11 ROT: Comment rot in module docstrings
**Fix:** Delete or rewrite inaccurate docstrings.

### Re-audit checkpoint
Launch GLEE agent targeting `SpoofingModule.kt`, `SpoofingViewModel.kt`, `SpoofingScreen.kt`. Verify MAC validation is consistent, original MAC is restored, MITM coroutines are properly scoped, and chipset detection works.

---

## Phase 7: BLUFFS + BrakTooth (11 findings)

hcitool key_size is nonexistent, BrakTooth missing try/catch freezes UI, BluffsMode ignored in fallback.

### 7.1 BROKEN: hcitool key_size is nonexistent command
**Fix:** Remove the `hcitool key_size` code path. Replace with `hcitool enc` to check if encryption is active, then query key size via `/sys/kernel/debug/bluetooth/hci0/` if available (requires root). If key size cannot be determined, report "Key size check unavailable — requires kernel debug access."

### 7.2 BROKEN: hcitool fallback tests for KNOB not BLUFFS
**Fix:** Rewrite the fallback path to use BLUFFS-appropriate commands. BLUFFS (CVE-2023-24023) attacks session key derivation, not encryption key negotiation. The hcitool fallback should test session key entropy, not KNOB's key length.

### 7.3 BROKEN: BrakTooth missing try/catch freezes UI permanently
**Fix:** Wrap the BrakTooth serial command execution in try-catch. On any exception, set `_state` to Error, emit log message, and return. Never let an unhandled exception propagate to the UI coroutine.

### 7.4 BROKEN: BluffsMode ignored in hcitool fallback
**Fix:** Branch on `BluffsMode` to generate different hcitool command sequences. Each mode should test a different aspect of session key derivation.

### 7.5 UNSOUND: BrakTooth no MAC validation before serial injection
**Fix:** Apply pattern 0.4. `MacValidator.requireValid(mac)` before building the serial command.

### 7.6 UNSOUND: Wrong hardware type accepted (BtleJack vs ESP32)
**Fix:** Check `hardwareManager.deviceType` before sending BrakTooth commands. BrakTooth requires ESP32 firmware, not BtleJack. Show error if wrong hardware connected.

### 7.7 UNSOUND: Serial commands fire-and-forget with no ordering guarantee
**Fix:** Use a `Channel<SerialCommand>` to serialize commands. A single coroutine reads from the channel, sends the command, and waits for the response before processing the next.

### 7.8 UNSOUND: hcitool enc syntax likely wrong
**Fix:** Verify the correct `hcitool` encryption command syntax against the BlueZ source. The connection handle format may need adjustment. Add error handling for "invalid syntax" responses.

### 7.9 ROT: BluffsMode descriptions fabricated
**Fix:** Replace mode descriptions with accurate summaries from the BLUFFS paper (Antonioli, 2023). If mode behavior can't be accurately described, simplify to fewer modes that match real attack variants.

### 7.10 INCOMPLETE: BrakTooth missing ActionLogger/ActiveTaskManager
**Fix:** Apply pattern 0.6. Add `ActionLogger.log()` and `ActiveTaskManager.add()/remove()` calls.

### 7.11 UNSOUND: Key size threshold produces false positives
**Fix:** Use the correct threshold from the KNOB/BLUFFS papers. Key size <= 7 bytes is vulnerable (KNOB), not whatever arbitrary value is currently used. Document the threshold source.

### Re-audit checkpoint
Launch GLEE agent targeting `BluffsModule.kt`, `BrakToothModule.kt`, and both ViewModels/Screens. Verify command syntax is correct, modes produce distinct behavior, hardware type is validated, and serial commands are serialized.

---

## Phase 8: Bluebugging (10 findings)

RFCOMM socket leaked on HFP failure, AT response parser breaks on "OK" in contact names, readResponse spins on EOF.

### 8.1 BROKEN: RFCOMM socket leaked on HFP UUID failure
**Fix:** Wrap socket creation in try-finally. If `connect()` throws, call `socket.close()` in the finally block. Track the socket as a class field for cleanup.

### 8.2 BROKEN: AT response parser truncated on "OK" substring in contact names
**Fix:** Parse AT responses line by line. Only treat a standalone `OK\r\n` or `ERROR\r\n` on its own line as a terminator. Don't search for "OK" as a substring within the response body.

### 8.3 BROKEN: readResponse doesn't detect EOF — spins for 3 seconds
**Fix:** Check `inputStream.read()` return value. If -1 (EOF), break immediately and report disconnection instead of spinning for the full timeout.

### 8.4 UNSOUND: Mutable fields without @Volatile
**Fix:** Apply pattern 0.2. Mark `socket`, `inputStream`, `outputStream`, and `isConnected` as `@Volatile`.

### 8.5 UNSOUND: CancellationException swallowed in catch-all
**Fix:** Apply pattern 0.1. Rethrow `CancellationException`.

### 8.6 UNSOUND: No disconnect on flow cancellation
**Fix:** Use `callbackFlow` or `suspendCancellableCoroutine` with `invokeOnCancellation { socket?.close() }`. Ensure the RFCOMM socket is closed when the collecting coroutine is cancelled.

### 8.7 UNSOUND: No error handler disconnect
**Fix:** In every catch block that handles a communication error, call `disconnect()` to close the socket and reset state.

### 8.8 ROT: False "requires root" claim in docs
**Fix:** Remove the root requirement claim. Bluebugging via RFCOMM/AT commands works without root on Android.

### 8.9 ROT: Docs overstate call-control capability
**Fix:** Rewrite docs to accurately reflect which AT commands are actually implemented and tested. Remove claims about call interception if only basic AT commands are supported.

### 8.10 INCOMPLETE: No custom AT command input
**Fix:** Add an `OutlinedTextField` in the Bluebugging screen for custom AT command entry. Wire a "Send" button to `bluebuggingModule.sendCommand(customCommand)`. Display the raw response.

### Re-audit checkpoint
Launch GLEE agent targeting `BluebuggingModule.kt`, `BluebuggingViewModel.kt`, `BluebuggingScreen.kt`. Verify AT response parsing handles embedded "OK", socket lifecycle is clean, cancellation works, and custom AT command input functions.

---

## Phase 9: BlueSmack (10 findings)

Interface name field does nothing, onCleared doesn't kill l2ping, su process never destroyed.

### 9.1 BROKEN: Interface name field does nothing — always uses hardcoded hci0
**Fix:** Pass the user-entered interface name to the l2ping command: `l2ping -i $interface -s $size -c $count $mac`. Validate interface name against `[a-zA-Z0-9]+` regex to prevent injection.

### 9.2 BROKEN: onCleared doesn't kill orphaned l2ping process
**Fix:** In `onCleared()`, execute `killall l2ping` via RootExecutor to clean up any orphaned processes. Also store the Process reference and call `process.destroy()`.

### 9.3 BROKEN: executeL2ping blocks instead of streaming
**Fix:** Read l2ping stdout line by line in a coroutine, emitting each line to the UI via `_log.emit(line)`. Don't wait for the entire process to complete before showing output.

### 9.4 UNSOUND: su process never destroyed
**Fix:** Store the `Process` reference returned by `Runtime.exec("su")`. In `stopAttack()` and `onCleared()`, call `process.destroyForcibly()`.

### 9.5 UNSOUND: CancellationException swallowed
**Fix:** Apply pattern 0.1.

### 9.6 UNSOUND: No bounds on packet size/count
**Fix:** Clamp packet size to 1..65535 and count to 1..100000. Validate in the ViewModel before starting the attack. Show error in UI if out of bounds.

### 9.7 UNSOUND: No tests
**Fix:** Add unit tests for parameter validation (packet size bounds, count bounds, interface name validation, MAC validation).

### 9.8 ROT: Comment rot in module
**Fix:** Delete or rewrite inaccurate comments.

### 9.9 INCOMPLETE: No tests
**Fix:** Same as 9.7. Combined.

### 9.10 INCOMPLETE: Missing ActiveTaskManager integration
**Fix:** Apply pattern 0.6. Add ActiveTaskManager and ActionLogger calls.

### Re-audit checkpoint
Launch GLEE agent targeting `BlueSmackModule.kt`, `BlueSmackViewModel.kt`, `BlueSmackScreen.kt`. Verify interface name is used, l2ping process is properly managed, output streams in real-time, and parameter bounds are enforced.

---

## Phase 10: PerfektBlue (10 findings)

Core premise broken — RFCOMM to non-RFCOMM profiles, crash detector can't distinguish crash from clean disconnect, vCard nesting doesn't nest.

### 10.1 BROKEN: AVRCP fuzzing uses RFCOMM but AVRCP is L2CAP
**File:** `PerfektBlueModule.kt:224`
**Fix:** Remove AVRCP from RFCOMM-based fuzzing targets. Either implement L2CAP-based AVRCP fuzzing (requires root + raw L2CAP socket) or document it as "requires external hardware" and disable the option when no hardware is connected.

### 10.2 BROKEN: PBAP fuzz writes raw vCard bytes without OBEX framing
**File:** `PerfektBlueModule.kt:177`
**Fix:** Wrap vCard payloads in proper OBEX PUT packets: OBEX CONNECT first, then PUT with Connection ID, Type header ("x-bt/phonebook"), Name header, and Body header containing the vCard data.

### 10.3 BROKEN: FuzzResult.WRITE_ERROR defined but never returned
**File:** `PerfektBlueModule.kt:292-301`
**Fix:** Return `WRITE_ERROR` when `outputStream.write()` throws `IOException` (not `SocketException`, which indicates crash). Distinguish between write failure (remote rejected) and connection loss (potential crash).

### 10.4 UNSOUND: Write IOException classified as TARGET_CRASHED
**File:** `PerfektBlueModule.kt:237-240`
**Fix:** Only classify as `TARGET_CRASHED` if the connection was alive before the write and is dead after, AND the disconnection was unexpected (not a clean RFCOMM close). Use a post-write probe: attempt a second small write. If it fails, the target likely crashed. If the initial IOException is `Connection reset by peer`, it's more likely a rejection than a crash.

### 10.5 UNSOUND: vCard "deep nesting" is flat
**File:** `PerfektBlueModule.kt:58-66`
**Fix:** Build actual nested vCard: each `AGENT` property contains a full vCard, which contains another `AGENT`, recursively. Current code creates 50 sibling vCards at depth 1. Replace with a recursive builder up to depth 50.

### 10.6 UNSOUND: Thread.interrupt() cannot interrupt InputStream.read()
**File:** `PerfektBlueModule.kt:263`
**Fix:** Close the socket to interrupt the blocking read. Store socket reference, call `socket.close()` from `stopAttack()`. The `read()` will throw `IOException`.

### 10.7 UNSOUND: stopAttack() doesn't remove task from ActiveTaskManager
**File:** `PerfektBlueViewModel.kt:85-88`
**Fix:** Call `ActiveTaskManager.remove(taskId)` in `stopAttack()` and in the attack's `finally` block.

### 10.8 INCOMPLETE: stopAttack() exists but has no UI trigger
**File:** `PerfektBlueScreen.kt`
**Fix:** Add a "Stop" button visible when `isRunning == true`. Wire it to `viewModel.stopAttack()`.

### 10.9 ROT: Comment says "Oversized field name" but code creates oversized field value
**File:** `PerfektBlueModule.kt:55`
**Fix:** Fix comment to match code behavior, or fix code to match intended behavior.

### 10.10 INCOMPLETE: No tests
**Fix:** Add unit tests for vCard generation (verify nesting depth), OBEX framing, crash vs. rejection classification.

### Re-audit checkpoint
Launch GLEE agent targeting `PerfektBlueModule.kt`, `PerfektBlueViewModel.kt`, `PerfektBlueScreen.kt`. Verify AVRCP is not fuzzed over RFCOMM, PBAP uses OBEX framing, vCards are properly nested, crash detection distinguishes crashes from rejections, and stop button works.

---

## Phase 11: GATT Fuzzing (9 findings)

onCharacteristicRead only overrides API 33+ signature, write type prefers NO_RESPONSE making verdicts unreliable.

### 11.1 BROKEN: onCharacteristicRead only overrides API 33+ signature
**File:** `GattFuzzingModule.kt`
**Fix:** Add the deprecated `onCharacteristicRead(gatt, characteristic, status)` override for API 26-32. Read value from `characteristic.value` in the deprecated callback:
```kotlin
@Deprecated("Deprecated in API 33")
override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
        _log.tryEmit("  Read ${characteristic.uuid}: ${characteristic.value?.toHexString()}")
    }
}
```

### 11.2 BROKEN: Write type prefers NO_RESPONSE — fuzz verdicts unreliable
**Fix:** Prefer `WRITE_TYPE_DEFAULT` (with response) over `WRITE_TYPE_NO_RESPONSE` when both are supported. Only use NO_RESPONSE when it's the only write property available. This gives accurate OK/REJECTED verdicts.

### 11.3 BROKEN: Silent payload drops when writeCharacteristic returns false
**Fix:** Check the return value of `writeCharacteristic()`. If false, log "Write rejected by stack (queue full)" and retry after `delay(500)` up to 3 times. If still false, report as `STACK_REJECTED` distinct from `REJECTED` by the remote device.

### 11.4 UNSOUND: CompletableDeferred fields not @Volatile
**Fix:** Mark `connectionDeferred`, `discoveryDeferred`, `writeDeferred` as `@Volatile`.

### 11.5 UNSOUND: formatUuid truncates 32-bit UUIDs incorrectly
**Fix:** The standard BT UUID base is `xxxxxxxx-0000-1000-8000-00805f9b34fb`. For 16-bit UUIDs, extract `s.substring(4, 8)`. For 32-bit UUIDs, extract `s.substring(0, 8)`. Currently only handles 16-bit correctly.

### 11.6 UNSOUND: readCharacteristic not awaited — 200ms hope-based delay
**Fix:** Add a `readDeferred: CompletableDeferred<ByteArray?>` field. Set it before calling `readCharacteristic()`. Complete it from `onCharacteristicRead`. Await with `withTimeout(3000)` instead of a fixed delay.

### 11.7 INCOMPLETE: Connection status parameter ignored
**Fix:** Check the `status` parameter in `onConnectionStateChange`. If status is not `GATT_SUCCESS`, log the error status and don't proceed with service discovery.

### 11.8 INCOMPLETE: No tests
**Fix:** Add unit tests for formatUuid (16-bit and 32-bit UUIDs), payload generation, and write-type selection logic.

### 11.9 ROT: Comment rot in module
**Fix:** Delete or rewrite inaccurate comments.

### Re-audit checkpoint
Launch GLEE agent targeting `GattFuzzingModule.kt`, `GattFuzzingViewModel.kt`. Verify both API signatures work, write type selection is correct, payload drops are reported, reads are properly awaited, and UUID formatting handles all cases.

---

## Phase 12: BLE Spam (8 findings)

onStartFailure creates ghost-active state, close() never called, CancellationException swallowed.

### 12.1 BROKEN: onStartFailure creates ghost-active state
**Fix:** In `onStartFailure`, set `_isActive.value = false` and emit error to `_log`. Map error codes to human-readable messages (ADVERTISE_FAILED_DATA_TOO_LARGE, etc.).

### 12.2 BROKEN: close() exists but never called — scope leak
**Fix:** Call `close()` from the ViewModel's `onCleared()`. Also call it from `stopSpam()` if the module is being discarded.

### 12.3 UNSOUND: CancellationException swallowed in catch-all
**Fix:** Apply pattern 0.1.

### 12.4 UNSOUND: No error state surfaced to user
**Fix:** Add a `_error: MutableStateFlow<String?>` field. Set it from `onStartFailure` and catch blocks. Display in the UI as a `Snackbar` or inline error message. Clear on next `startSpam()`.

### 12.5 UNSOUND: ByteArray in data class breaks equals/hashCode
**Fix:** Replace `ByteArray` with `List<Byte>` in the data class, or override `equals()`/`hashCode()` to use `contentEquals()`/`contentHashCode()`.

### 12.6 INCOMPLETE: Tests don't verify cleanup
**Fix:** Add test that starts spam, stops it, and verifies advertising callbacks are stopped and scope is cancelled.

### 12.7 ROT: Unused imports
**Fix:** Remove unused imports.

### 12.8 ROT: Deprecated values() usage
**Fix:** Replace `Enum.values()` with `Enum.entries` (Kotlin 1.9+).

### Re-audit checkpoint
Launch GLEE agent targeting `BleSpamModule.kt`, `BleSpamViewModel.kt`, `BleSpamScreen.kt`. Verify advertising failure is reported, cleanup works, errors surface to user, and data class equality is correct.

---

## Phase 13: Attack Chaining (13 findings)

Connecting nodes and dragging them are both broken. No directionality enforcement, hardcoded pixel offsets, no execution cancellation, save has no caller.

### 13.1 BROKEN: Zero directionality validation — input-to-input accepted
**File:** `AttackChainingScreen.kt`, `ViewModel.kt`
**Fix:** Add a `role` enum (`INPUT`/`OUTPUT`) to `NodeConnector`. In `addConnection()`, validate that one connector is INPUT and the other is OUTPUT. Reject same-role connections with a toast message.

### 13.2 BROKEN: Connection lines use hardcoded pixel offsets that don't match dp layout
**File:** `AttackChainingScreen.kt:124-131`
**Fix:** Replace hardcoded pixel values with `with(LocalDensity.current) { x.dp.toPx() }`. Or compute connector positions from actual node layout using `onGloballyPositioned` modifier to capture connector coordinates.

### 13.3 BROKEN: indexOf returns -1 for reversed connections
**File:** `AttackChainingScreen.kt:124-126`
**Fix:** When computing line endpoints, look up the connector in both the source node's outputs and inputs lists. Use the correct list based on the connector's role (from 13.1).

### 13.4 UNSOUND: No concurrent execution guard
**File:** `AttackChainExecutor.kt:40-53`
**Fix:** Add an `isRunning: AtomicBoolean` field. Set to true at start of `execute()`, false in finally. Return early if already running. Or use a `Mutex`.

### 13.5 UNSOUND: No cancel mechanism for running chains
**File:** `ViewModel.kt:196-202`
**Fix:** Store the execution `Job`. Add `cancelExecution()` that cancels it. Add a "Stop" button in the UI visible when chain is running. Make each node's `execute()` check `coroutineContext.isActive` between steps.

### 13.6 UNSOUND: Duplicate connections accepted
**File:** `ViewModel.kt:128-138`
**Fix:** In `addConnection()`, check if the connection already exists: `connections.any { it.first == from && it.second == to }`. If so, return without adding.

### 13.7 UNSOUND: pointerInput(Unit) stale closure
**File:** `AttackChainingScreen.kt:362-366`
**Fix:** Use `rememberUpdatedState` for the callback values used inside `pointerInput`. Or change the key from `Unit` to the state values that the closure captures, forcing recomposition when they change.

### 13.8 INCOMPLETE: saveAttackChain() has zero callers — no Save button
**File:** `ViewModel.kt:172`
**Fix:** Add a "Save" button in the UI toolbar. Wire it to `viewModel.saveAttackChain(name)`. Add a "Load" button that shows saved chain names from `getAllAttackChainNames()`.

### 13.9 INCOMPLETE: No way to deselect a connector after first tap
**Fix:** Add a "Cancel Connection" button or tap-on-empty-canvas to clear the pending connection. Show a visual indicator (highlight) on the selected connector.

### 13.10 INCOMPLETE: Legacy AttackChainingCanvasModule is dead code
**File:** `AttackChainingCanvasModule.kt`
**Fix:** Delete the entire file. Remove any references to it.

### 13.11 ROT: ScanBleNode titled "Scan BLE Devices" but is passthrough
**File:** `AttackNode.kt:149`
**Fix:** Either implement actual BLE scanning in the node (inject `BluetoothScanner`, populate ExecutionContext with discovered devices) or rename to "Pass-through" and remove from the default palette.

### 13.12 ROT: KeystrokeInjectionNode "payload" input connector is decorative
**File:** `AttackNode.kt:209`
**Fix:** Make the executor read the payload from the incoming connection's ExecutionContext data. If no connection provides a payload, use the node's configured default.

### 13.13 UNSUPPORTED: Gson serialization of Compose Offset likely fails silently
**File:** `AttackChainRepository.kt:28`
**Fix:** Replace `Offset` with a plain `data class NodePosition(val x: Float, val y: Float)` for serialization. Convert to/from Compose `Offset` at the UI boundary. Gson handles plain data classes correctly.

### Re-audit checkpoint
Launch GLEE agent targeting `AttackChainingScreen.kt`, `AttackChainingViewModel.kt`, `AttackChainExecutor.kt`, `AttackNode.kt`, `AttackChainRepository.kt`. Verify connector directionality, line rendering, execution cancellation, save/load, and serialization.

---

## Phase 14: App Cohesiveness (15 findings)

BluetoothScanner.destroy() never called, dashboard undercounts DUAL devices, half the screens lack shared infrastructure.

### 14.1 BROKEN: BluetoothScanner.destroy() never called
**File:** `BluetoothScanner.kt`
**Fix:** Call `destroy()` from `MainActivity.onDestroy()` or the Application class's cleanup. If BluetoothScanner is a singleton, add a `Lifecycle` observer that calls `destroy()` when the app goes to `DESTROYED`.

### 14.2 BROKEN: Dashboard device counts drop DUAL devices
**File:** `DashboardViewModel.kt:59-60`
**Fix:** Change the count logic:
```kotlin
val bleCount = devices.count { it.protocol == Protocol.BLE || it.protocol == Protocol.DUAL }
val classicCount = devices.count { it.protocol == Protocol.CLASSIC || it.protocol == Protocol.DUAL }
```
Or add a third "Dual" count.

### 14.3 BROKEN: MAC validation regex disagrees with MacValidator
**File:** `SpoofingViewModel.kt:96`
**Fix:** Already addressed in Phase 6.1. Use `MacValidator.isValid()` everywhere.

### 14.4 BROKEN: DeviceManagementViewModel creates second BluetoothScanner
**File:** `DeviceManagementViewModel.kt:42-50`
**Fix:** Share the singleton `BluetoothScanner` instance. Inject it via the ViewModel factory instead of creating a new one. Call `destroy()` only from the Application lifecycle, not from any individual ViewModel.

### 14.5 UNSOUND: AttackChainingCanvasModule uses Job() instead of SupervisorJob()
**File:** `AttackChainingCanvasModule.kt:39`
**Fix:** Already addressed by Phase 13.10 (delete the file). If kept, change to `SupervisorJob()`.

### 14.6 UNSOUND: SettingsViewModel rolls its own root check
**File:** `SettingsViewModel.kt:51-63`
**Fix:** Replace with `RootExecutor.isRootAvailable()`. Add this method to RootExecutor if it doesn't exist: execute `su -c id` and check for `uid=0`.

### 14.7 UNSOUND: DatabaseUpdater creates its own HttpClient(CIO)
**File:** `DatabaseUpdater.kt:30`
**Fix:** Accept `HttpClient` as a constructor parameter. Use the app-wide `HttpClient(Android)` instance. Add a `close()` method in case a local client is used.

### 14.8 UNSOUND: BtlejuiceViewModel extends ViewModel() with Application parameter
**File:** `BtlejuiceViewModel.kt:23`
**Fix:** Change to `class BtlejuiceViewModel(application: Application) : AndroidViewModel(application)`. Use `getApplication<Application>()` where needed.

### 14.9 INCOMPLETE: ActiveTaskManager used by only 3 of 14 attack ViewModels
**Fix:** Apply pattern 0.6 to all remaining ViewModels: BlueSmack, Bluebugging, Bluesnarfing, BLUFFS, BrakTooth, GATT Fuzzing, GATT Relay, BLE Spam, SMP Bypass, BtleJack, BtleJuice.

### 14.10 INCOMPLETE: ResultActions composable used in only 3 of 14+ attack screens
**Fix:** Add `ResultActions` (copy, share, export) to all attack result screens. This composable provides clipboard copy and `Intent.ACTION_SEND` sharing.

### 14.11 INCOMPLETE: ActionLogger missing from 6 attack ViewModels
**Fix:** Apply pattern 0.6 to: BtleJack, BtleJuice, BrakTooth, GATT Fuzzing, GATT Relay, BLE Spam.

### 14.12 ROT: Unused imports DeviceWithLocation and Location
**File:** `DashboardViewModel.kt:11-12`
**Fix:** Delete unused imports.

### 14.13 ROT: osmdroid declared but never used
**File:** `libs.versions.toml:25,62`
**Fix:** Remove osmdroid version and library entries from the version catalog.

### 14.14 ROT: Duplicate Material3 dependency
**File:** `libs.versions.toml:54-55`
**Fix:** Remove the explicit `material3 = "1.4.0"` line. Use only the BOM-managed version.

### 14.15 ROT: MagiskViewModel factory entry is dead code
**File:** `MainActivity.kt:206-207`
**Fix:** Delete the MagiskViewModel factory entry. If MagiskViewModel.kt exists and is unused, delete it too.

### Re-audit checkpoint
Launch GLEE cohesiveness agent targeting the entire app architecture. Verify all ViewModels use ActiveTaskManager/ActionLogger, BluetoothScanner lifecycle is managed, dashboard counts are correct, no dead code remains, and dependency catalog is clean.

---

## Execution Order & Dependencies

```
Phase 0  (Cross-cutting patterns) ──────────────────────────────┐
                                                                 │
  ├─► Phase 1  (GATT Relay, 16 fixes)         ──► re-audit ─┐  │
  ├─► Phase 2  (Keystroke Injection, 12 fixes) ──► re-audit ─┤  │
  ├─► Phase 3  (BtleJack + BtleJuice, 14 fixes)─► re-audit ─┤  │
  ├─► Phase 4  (Bluesnarfing, 12 fixes)        ──► re-audit ─┤  │
  ├─► Phase 5  (SMP Bypass, 12 fixes)          ──► re-audit ─┤  │
  ├─► Phase 6  (Spoofing, 11 fixes)            ──► re-audit ─┤  │
  ├─► Phase 7  (BLUFFS + BrakTooth, 11 fixes)  ──► re-audit ─┤  │
  ├─► Phase 8  (Bluebugging, 10 fixes)         ──► re-audit ─┤  │
  ├─► Phase 9  (BlueSmack, 10 fixes)           ──► re-audit ─┤  │
  ├─► Phase 10 (PerfektBlue, 10 fixes)         ──► re-audit ─┤  │
  ├─► Phase 11 (GATT Fuzzing, 9 fixes)         ──► re-audit ─┤  │
  └─► Phase 12 (BLE Spam, 8 fixes)             ──► re-audit ─┤  │
                                                               │  │
Phase 13 (Attack Chaining, 13 fixes)           ──► re-audit ──┤  │
                                                               │  │
Phase 14 (App Cohesiveness, 15 fixes)          ──► re-audit ──┘  │
  (depends on all module phases completing first)                 │
                                                                 │
Phase 0 total changes cascade through all phases ────────────────┘
```

**Parallelism:** Phases 1-12 are independent of each other (different modules, different files). They can run as concurrent agents after Phase 0 completes. Phase 13 (Attack Chaining) is independent. Phase 14 (App Cohesiveness) should run last because it verifies cross-cutting patterns applied across all modules.

**Re-audit pattern:** After each phase, a fresh GLEE agent is launched targeting only the files modified in that phase. The agent's mandate is adversarial: assume every fix is wrong until proven otherwise. If the re-audit finds regressions or incomplete fixes, they feed back into the same phase before proceeding.

---

## Scope Estimate

- **Files changed:** ~45-55 across all phases
- **Lines added/modified:** ~4,000-5,000
- **New test files:** ~10-12
- **Deleted files:** 1-2 (dead code modules)
- **GLEE re-audit agents:** 15 (one per module phase + one cohesiveness)

---

## Post-Remediation

After all phases complete and all re-audits pass:
1. Full-app GLEE audit (single comprehensive agent)
2. Version bump to major=2
3. Final commit and PR
