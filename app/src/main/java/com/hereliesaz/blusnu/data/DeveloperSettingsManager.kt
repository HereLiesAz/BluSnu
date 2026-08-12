package com.hereliesaz.blusnu.data

import android.content.SharedPreferences
import android.util.Log

/**
 * Manages Android developer settings on behalf of the user for the duration of a session.
 *
 * Each setting the user enables is recorded (original value + key) in [SharedPreferences].
 * On app exit (or on the next startup if the previous session was killed) every setting
 * is restored to the value it held before we touched it, then the record is cleared.
 *
 * All reads and writes go through `su -c settings …` because the relevant settings live
 * in the Secure and Global namespaces, which require WRITE_SECURE_SETTINGS or root.
 */
class DeveloperSettingsManager(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "DevSettingsManager"
        private const val PREF_PREFIX = "dev_orig_"

        /** Settings this manager knows how to control. */
        val ALL: List<ManagedDevSetting> = listOf(
            ManagedDevSetting(
                key = "bluetooth_btsnoop_enable",
                namespace = "secure",
                displayName = "Bluetooth HCI Snoop Log",
                description = "Captures all Bluetooth HCI traffic to /data/misc/bluetooth/logs/. " +
                    "Pull the file via ADB and open it in Wireshark to inspect every packet " +
                    "exchanged during an assessment.",
                enableValue = "1",
                defaultDisableValue = "0"
            ),
            ManagedDevSetting(
                key = "stay_on_while_plugged_in",
                namespace = "global",
                displayName = "Stay Awake (while charging)",
                description = "Keeps the screen on while the device is plugged in — prevents " +
                    "the display from sleeping mid-attack during long sessions.",
                enableValue = "3",       // bitmask: AC(1) | USB(2)
                defaultDisableValue = "0"
            ),
            ManagedDevSetting(
                key = "bluetooth_a2dp_offload_disabled",
                namespace = "global",
                displayName = "Disable A2DP Hardware Offload",
                description = "Routes Bluetooth audio entirely through the software stack. " +
                    "Required for BlueSpy and HSP/HFP eavesdropping attacks that need full " +
                    "visibility of the audio data path.",
                enableValue = "1",
                defaultDisableValue = "0"
            )
        )
    }

    /** Returns true if [setting] was enabled by this manager in a previous or current session. */
    fun isEnabledByUs(setting: ManagedDevSetting): Boolean =
        prefs.contains("$PREF_PREFIX${setting.key}")

    /**
     * Reads the current raw value for [setting] via root shell.
     * Returns null if root is unavailable or the key doesn't exist.
     */
    fun getCurrentValue(setting: ManagedDevSetting): String? = try {
        val proc = ProcessBuilder("su", "-c",
            "settings get ${setting.namespace} ${setting.key}")
            .redirectErrorStream(true)
            .start()
        val result = proc.inputStream.bufferedReader().readLine()?.trim()
        proc.waitFor()
        if (result == "null" || result.isNullOrBlank()) null else result
    } catch (e: Exception) {
        Log.w(TAG, "Could not read ${setting.key}: ${e.message}")
        null
    }

    /**
     * Enables [setting] for this session.
     *
     * 1. Reads and stores the current value so it can be restored later.
     * 2. Applies [ManagedDevSetting.enableValue] via `su settings put`.
     *
     * @return true if the write succeeded.
     */
    fun enableForSession(setting: ManagedDevSetting): Boolean {
        val currentValue = getCurrentValue(setting) ?: setting.defaultDisableValue
        // Already at the desired value and not tracked by us → user had it on; leave it alone.
        if (currentValue == setting.enableValue && !isEnabledByUs(setting)) {
            Log.d(TAG, "${setting.key} already enabled by user — not tracking")
            return true
        }
        return try {
            val proc = ProcessBuilder("su", "-c",
                "settings put ${setting.namespace} ${setting.key} ${setting.enableValue}")
                .redirectErrorStream(true)
                .start()
            val success = proc.waitFor() == 0
            if (success) {
                // Only track if we actually changed the value.
                if (currentValue != setting.enableValue) {
                    prefs.edit()
                        .putString("$PREF_PREFIX${setting.key}", currentValue)
                        .apply()
                    Log.d(TAG, "Enabled ${setting.key}; original value was '$currentValue'")
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable ${setting.key}: ${e.message}")
            false
        }
    }

    /**
     * Restores a single [setting] to its pre-session value and clears the stored record.
     */
    fun restore(setting: ManagedDevSetting) {
        val originalValue = prefs.getString("$PREF_PREFIX${setting.key}", null) ?: return
        try {
            val proc = ProcessBuilder("su", "-c",
                "settings put ${setting.namespace} ${setting.key} $originalValue")
                .redirectErrorStream(true)
                .start()
            proc.waitFor()
            Log.d(TAG, "Restored ${setting.key} to '$originalValue'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore ${setting.key}: ${e.message}")
        } finally {
            prefs.edit().remove("$PREF_PREFIX${setting.key}").apply()
        }
    }

    /**
     * Restores all settings that were enabled by this manager (session cleanup or crash recovery).
     * Safe to call on startup — it is a no-op when nothing was previously changed.
     */
    fun restoreAll() {
        val changed = ALL.filter { isEnabledByUs(it) }
        if (changed.isEmpty()) return
        Log.d(TAG, "Restoring ${changed.size} developer setting(s)")
        changed.forEach { restore(it) }
    }
}

/**
 * Describes a single Android developer setting that [DeveloperSettingsManager] can manage.
 */
data class ManagedDevSetting(
    val key: String,
    val namespace: String,
    val displayName: String,
    val description: String,
    val enableValue: String,
    val defaultDisableValue: String
)
