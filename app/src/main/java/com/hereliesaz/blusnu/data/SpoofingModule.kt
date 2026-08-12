package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor

/**
 * Module responsible for Identity Spoofing (MAC Address Cloning).
 *
 * MAC Spoofing changes the Bluetooth adapter's BD_ADDR to impersonate another device.
 * This can bypass address-based whitelists or trick a central into connecting.
 *
 * Root is required because changing the BD_ADDR involves vendor-specific HCI commands
 * or the bdaddr utility, neither of which is accessible from standard Android APIs.
 * All privileged operations are executed through [RootExecutor].
 */
class SpoofingModule {

    companion object {
        private const val TAG = "SpoofingModule"
    }

    /**
     * Attempts to change the Bluetooth adapter's MAC address (BD_ADDR).
     *
     * The process:
     * 1. Validate the MAC address format
     * 2. Bring hci0 down
     * 3. Use bdaddr to write the new address (with vendor-specific HCI fallback)
     * 4. Reset and bring hci0 back up
     * 5. Verify the change took effect
     *
     * @param macAddress The target MAC address to spoof (e.g. "00:11:22:33:44:55").
     * @return true if the adapter reports the new address after the operation, false otherwise.
     */
    suspend fun spoofMacAddress(macAddress: String): Boolean {
        if (!MacValidator.isValid(macAddress)) {
            Log.e(TAG, "Invalid MAC address format: $macAddress (expected XX:XX:XX:XX:XX:XX)")
            return false
        }

        // Check root availability
        if (!RootExecutor.isRootAvailable()) {
            Log.w(TAG, "Cannot spoof MAC to $macAddress: root is not available on this device.")
            return false
        }

        Log.i(TAG, "Attempting to spoof BD_ADDR to $macAddress")

        // Step 1: Bring the adapter down
        val downResult = RootExecutor.execute("hciconfig hci0 down")
        if (downResult.startsWith("Error")) {
            Log.e(TAG, "Failed to bring hci0 down: $downResult")
            // Try to recover
            RootExecutor.execute("hciconfig hci0 up")
            return false
        }

        // Step 2: Write the new BD_ADDR using bdaddr
        val bdaddrResult = RootExecutor.execute("bdaddr -i hci0 $macAddress")
        val usedBdaddr: Boolean

        if (bdaddrResult.startsWith("Error") || bdaddrResult.contains("not found")) {
            Log.w(TAG, "bdaddr failed or not found, trying vendor-specific HCI command fallback")
            usedBdaddr = false

            // Fallback: Use vendor-specific HCI command (0x3F 0x001) with reversed MAC bytes
            // The MAC bytes are reversed for the HCI command payload
            val macBytes = macAddress.split(":").reversed().joinToString(" ") { "0x$it" }
            val hciResult = RootExecutor.execute("hcitool cmd 0x3F 0x001 $macBytes")
            if (hciResult.startsWith("Error")) {
                Log.e(TAG, "Vendor-specific HCI command also failed: $hciResult")
                RootExecutor.execute("hciconfig hci0 up")
                return false
            }
        } else {
            usedBdaddr = true
            Log.d(TAG, "bdaddr result: $bdaddrResult")
        }

        // Step 3: Reset the adapter
        val resetResult = RootExecutor.execute("hciconfig hci0 reset")
        if (resetResult.startsWith("Error")) {
            Log.w(TAG, "hciconfig reset returned error (may still work): $resetResult")
        }

        // Step 4: Bring the adapter back up
        val upResult = RootExecutor.execute("hciconfig hci0 up")
        if (upResult.startsWith("Error")) {
            Log.e(TAG, "Failed to bring hci0 back up: $upResult")
            return false
        }

        // Step 5: Verify the address change
        val verifyResult = RootExecutor.execute("hciconfig hci0")
        val normalizedTarget = macAddress.uppercase()
        val spoofSuccessful = verifyResult.uppercase().contains(normalizedTarget)

        if (spoofSuccessful) {
            Log.i(TAG, "MAC address successfully spoofed to $macAddress (method: ${if (usedBdaddr) "bdaddr" else "vendor HCI"})")
        } else {
            Log.w(TAG, "MAC spoof verification failed. hciconfig output: $verifyResult")
        }

        return spoofSuccessful
    }
}
