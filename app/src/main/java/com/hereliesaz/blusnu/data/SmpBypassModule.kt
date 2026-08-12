package com.hereliesaz.blusnu.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * SMP Pairing Auditor module.
 *
 * Audits a BLE target's pairing posture by triggering pairing via btmgmt with
 * NoInputNoOutput capability and inspecting which pairing method the device
 * negotiates (Just Works, Passkey Entry, Numeric Comparison, or OOB).
 *
 * When root is available, btmgmt is used to attempt NoInputNoOutput-capability
 * pairing, which surfaces whether the target accepts unauthenticated Just Works
 * pairing. The bond state is checked via the Android API for ground-truth
 * confirmation rather than relying on string matching of command output.
 *
 * This module does NOT perform raw SMP packet manipulation and does NOT exploit
 * CVE-2024-34722. It is an auditing tool that tests pairing method negotiation
 * through standard system interfaces.
 *
 * Root is required because btmgmt and hcitool are privileged binaries.
 * All privileged operations are executed through [RootExecutor].
 */
class SmpBypassModule {

    companion object {
        private const val TAG = "SmpBypassModule"

        /** Timeout for btmgmt find (LE discovery), in milliseconds. */
        private const val BTMGMT_FIND_TIMEOUT_MS = 15_000L

        /** Timeout for GATT connection, in milliseconds. */
        private const val GATT_CONNECT_TIMEOUT_MS = 15_000L

        /** LE Public address type for btmgmt -t flag. */
        const val ADDRESS_TYPE_PUBLIC = 1

        /** LE Random address type for btmgmt -t flag. */
        const val ADDRESS_TYPE_RANDOM = 2
    }

    /**
     * Classifies a BLE address as public or random based on the two most-significant
     * bits of the first octet.
     *
     * BLE random addresses use specific MSB patterns in the first octet:
     * - 11xxxxxx = Static Random Address
     * - 01xxxxxx = Resolvable Private Address
     * - 00xxxxxx with non-zero lower bits in a random context = Non-Resolvable Private
     *
     * For btmgmt's `-t` flag: 1 = LE Public, 2 = LE Random.
     *
     * @return [ADDRESS_TYPE_PUBLIC] (1) or [ADDRESS_TYPE_RANDOM] (2).
     */
    fun detectAddressType(mac: String): Int {
        val firstOctet = mac.split(":").firstOrNull()
            ?.toIntOrNull(16) ?: return ADDRESS_TYPE_PUBLIC
        // Check the two most-significant bits of the first octet.
        // If either bit 7 or bit 6 is set, the address is random.
        val msBits = (firstOctet shr 6) and 0x03
        return if (msBits != 0) ADDRESS_TYPE_RANDOM else ADDRESS_TYPE_PUBLIC
    }

    /**
     * Classifies the negotiated pairing method from btmgmt output and actual bond state.
     *
     * Uses [BluetoothDevice.BOND_BONDED] as the ground truth for whether pairing
     * succeeded, rather than string-matching error text that may contain "Paired" as
     * a substring.
     *
     * @param btmgmtOutput Raw text output from the btmgmt pair command.
     * @param bondState The device's bond state from [BluetoothDevice.getBondState].
     * @return A [PairingMethodResult] describing the outcome.
     */
    fun classifyPairingMethod(
        btmgmtOutput: String,
        bondState: Int
    ): PairingMethodResult {
        val upper = btmgmtOutput.uppercase()
        val bonded = bondState == BluetoothDevice.BOND_BONDED

        return when {
            upper.contains("PASSKEY") || upper.contains("PIN") ->
                PairingMethodResult(
                    method = PairingMethod.PASSKEY_ENTRY,
                    vulnerable = false,
                    bonded = bonded,
                    detail = "Target requires Passkey Entry -- authenticated pairing."
                )
            upper.contains("NUMERIC COMPARISON") || upper.contains("CONFIRM") ->
                PairingMethodResult(
                    method = PairingMethod.NUMERIC_COMPARISON,
                    vulnerable = false,
                    bonded = bonded,
                    detail = "Target uses Numeric Comparison -- MITM-protected pairing."
                )
            upper.contains("OOB") ->
                PairingMethodResult(
                    method = PairingMethod.OOB,
                    vulnerable = false,
                    bonded = bonded,
                    detail = "Target uses Out-of-Band pairing."
                )
            bonded ->
                PairingMethodResult(
                    method = PairingMethod.JUST_WORKS,
                    vulnerable = true,
                    bonded = true,
                    detail = "Target accepted Just Works (unauthenticated) pairing. " +
                            "No user confirmation required -- weak against MITM."
                )
            upper.contains("REJECTED") || upper.contains("DENIED") ->
                PairingMethodResult(
                    method = PairingMethod.REJECTED,
                    vulnerable = false,
                    bonded = false,
                    detail = "Target rejected NoInputNoOutput pairing. " +
                            "The device enforces authenticated pairing."
                )
            upper.contains("TIMEOUT") || upper.contains("TIMED OUT") ->
                PairingMethodResult(
                    method = PairingMethod.UNKNOWN,
                    vulnerable = false,
                    bonded = false,
                    detail = "Pairing attempt timed out. Target may be out of range."
                )
            upper.contains("ALREADY PAIRED") ->
                PairingMethodResult(
                    method = PairingMethod.UNKNOWN,
                    vulnerable = false,
                    bonded = true,
                    detail = "Target is already paired. Unpair first to re-test."
                )
            btmgmtOutput.startsWith("Error") ->
                PairingMethodResult(
                    method = PairingMethod.UNKNOWN,
                    vulnerable = false,
                    bonded = bonded,
                    detail = "Pairing command failed: $btmgmtOutput"
                )
            else ->
                PairingMethodResult(
                    method = PairingMethod.UNKNOWN,
                    vulnerable = false,
                    bonded = bonded,
                    detail = "Pairing result inconclusive. Manual analysis recommended."
                )
        }
    }

    /**
     * Checks if root access is available via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Starts the SMP pairing audit against a target device.
     *
     * The audit flow:
     * 1. Verify root access (required for btmgmt/hcitool)
     * 2. Detect address type (public vs random)
     * 3. Use btmgmt to attempt pairing with NoInputNoOutput capability
     * 4. Check device.bondState for ground-truth pairing result
     * 5. Classify the negotiated pairing method
     * 6. Auto-unpair if pairing succeeded (cleanup)
     * 7. Close any GATT connection
     *
     * @param targetDevice The device to audit.
     * @param context Android context for bond state checks and GATT (nullable for root-only mode).
     * @return A Flow emitting progress logs.
     */
    fun startAttack(targetDevice: TargetDevice, context: Context? = null): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        emit("Initializing SMP Pairing Audit...")
        emit("Target: ${targetDevice.name ?: "Unknown Device ($mac)"}")

        // Root is strictly required
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("btmgmt/hcitool access requires root. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Detect address type (5.4: not hardcoded)
        val addressType = detectAddressType(mac)
        val addressTypeLabel = if (addressType == ADDRESS_TYPE_RANDOM) "LE Random" else "LE Public"
        emit("Detected address type: $addressTypeLabel")

        // Check available tools
        val btmgmtAvailable = checkBinary("btmgmt")
        val hcitoolAvailable = checkBinary("hcitool")

        if (!btmgmtAvailable && !hcitoolAvailable) {
            emit("ERROR: Neither btmgmt nor hcitool found.")
            emit("Install BlueZ management utilities to use SMP Pairing Auditor.")
            return@flow
        }

        // Open a GATT connection to warm up the link (tracked for cleanup: 5.6)
        var gatt: BluetoothGatt? = null
        try {
            if (context != null) {
                gatt = connectGatt(context, mac)
                if (gatt != null) {
                    emit("GATT connection established for pairing context.")
                } else {
                    emit("GATT connection not established (non-fatal, continuing with btmgmt).")
                }
            }

            if (btmgmtAvailable) {
                emit("btmgmt binary found. Using btmgmt approach.")
                executeBtmgmtApproach(mac, addressType, context)
            } else {
                emit("btmgmt not found. Falling back to hcitool LE approach.")
                executeHcitoolApproach(mac, addressType, context)
            }
        } finally {
            // 5.6: Always close the GATT connection to avoid leaking HCI slots
            gatt?.let {
                try {
                    it.disconnect()
                    it.close()
                    emit("GATT connection closed.")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing GATT: ${e.message}")
                }
            }
        }
    }

    /**
     * Opens a GATT connection to the target device over BLE transport.
     *
     * @return The [BluetoothGatt] instance or null on failure.
     */
    private suspend fun connectGatt(context: Context, mac: String): BluetoothGatt? {
        return withContext(Dispatchers.IO) {
            try {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = manager.adapter ?: return@withContext null
                val device = adapter.getRemoteDevice(mac)

                val connectionDeferred = CompletableDeferred<Boolean>()
                var gattRef: BluetoothGatt? = null

                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                    ) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> connectionDeferred.complete(true)
                            BluetoothProfile.STATE_DISCONNECTED -> connectionDeferred.complete(false)
                        }
                    }
                }

                // Use TRANSPORT_LE to force BLE connection
                gattRef = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

                val connected = try {
                    withTimeout(GATT_CONNECT_TIMEOUT_MS) { connectionDeferred.await() }
                } catch (_: Exception) {
                    false
                }

                if (connected) gattRef else {
                    gattRef?.close()
                    null
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException connecting GATT: ${e.message}")
                null
            } catch (e: Exception) {
                Log.w(TAG, "Error connecting GATT: ${e.message}")
                null
            }
        }
    }

    /**
     * Uses btmgmt to attempt LE pairing with NoInputNoOutput capability and reports
     * the actual negotiated pairing method.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeBtmgmtApproach(
        mac: String,
        addressType: Int,
        context: Context?
    ) {
        // Step 1: LE discovery with timeout (5.8)
        emit("Starting LE discovery via btmgmt (${BTMGMT_FIND_TIMEOUT_MS / 1000}s timeout)...")
        try {
            val findResult = withTimeout(BTMGMT_FIND_TIMEOUT_MS) {
                RootExecutor.execute("btmgmt find -l")
            }
            if (findResult.startsWith("Error")) {
                emit("LE discovery failed: $findResult")
                emit("Continuing with direct pairing attempt anyway...")
            } else {
                val targetFound = findResult.contains(mac, ignoreCase = true)
                if (targetFound) {
                    emit("Target $mac found in LE discovery results.")
                } else {
                    emit("Target $mac not seen in LE discovery. Attempting pairing anyway...")
                }
                for (line in findResult.lines()) {
                    if (line.isNotBlank()) {
                        emit("  discovery: $line")
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            emit("LE discovery timed out after ${BTMGMT_FIND_TIMEOUT_MS}ms. Proceeding to pairing...")
        }

        // Step 2: Attempt pairing with NoInputNoOutput capability
        // -c 3 = NoInputNoOutput
        // -t $addressType = detected address type (5.4)
        emit("Attempting pairing with NoInputNoOutput capability...")
        emit("Executing: btmgmt pair -c 3 -t $addressType $mac")
        val pairResult = RootExecutor.execute("btmgmt pair -c 3 -t $addressType $mac")

        for (line in pairResult.lines()) {
            if (line.isNotBlank()) {
                emit("  pair: $line")
            }
        }

        // Step 3: Check actual bond state (5.3)
        val bondState = getBondState(context, mac)
        val bondStateLabel = when (bondState) {
            BluetoothDevice.BOND_BONDED -> "BONDED"
            BluetoothDevice.BOND_BONDING -> "BONDING"
            else -> "NONE"
        }
        emit("Bond state: $bondStateLabel")

        // Step 4: Classify the pairing method (5.1, 5.2)
        val result = classifyPairingMethod(pairResult, bondState)
        emit("")
        emit("=== Pairing Audit Result ===")
        emit("Method: ${result.method.displayName}")
        emit("Bonded: ${result.bonded}")
        if (result.vulnerable) {
            emit("WEAK PAIRING: ${result.detail}")
            Log.w(TAG, "Weak pairing detected on $mac: ${result.method}")
        } else {
            emit("Assessment: ${result.detail}")
        }

        // Step 5: Auto-unpair after test (5.12)
        if (result.bonded) {
            emit("")
            autoUnpair(mac, context)
        }
    }

    /**
     * Uses hcitool for LE connection establishment as a fallback approach.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeHcitoolApproach(
        mac: String,
        addressType: Int,
        context: Context?
    ) {
        emit("Creating LE connection to $mac...")
        emit("Executing: hcitool lecc $mac")
        val leccResult = RootExecutor.execute("hcitool lecc $mac")

        if (leccResult.startsWith("Error")) {
            emit("LE connection failed: $leccResult")
            emit("The target may not support BLE, be out of range, or not advertising.")
            return
        }

        emit("LE connection result: $leccResult")

        val handleMatch = Regex("handle\\s+(\\d+|0x[0-9a-fA-F]+)", RegexOption.IGNORE_CASE)
            .find(leccResult)
        val handle = handleMatch?.groupValues?.get(1)

        if (handle != null) {
            emit("Connection handle: $handle")

            val btmgmtCheck = checkBinary("btmgmt")
            if (btmgmtCheck) {
                emit("Attempting pairing over established LE link...")
                val pairResult = RootExecutor.execute("btmgmt pair -c 3 -t $addressType $mac")
                for (line in pairResult.lines()) {
                    if (line.isNotBlank()) {
                        emit("  pair: $line")
                    }
                }
                val bondState = getBondState(context, mac)
                val result = classifyPairingMethod(pairResult, bondState)
                emit("")
                emit("=== Pairing Audit Result ===")
                emit("Method: ${result.method.displayName}")
                emit("Bonded: ${result.bonded}")
                if (result.vulnerable) {
                    emit("WEAK PAIRING: ${result.detail}")
                } else {
                    emit("Assessment: ${result.detail}")
                }
                if (result.bonded) {
                    emit("")
                    autoUnpair(mac, context)
                }
            } else {
                emit("LE connection established but btmgmt not available for pairing test.")
                emit("Connection handle $handle is active. Manual analysis required.")
            }

            // Disconnect the hcitool LE connection
            emit("Disconnecting LE connection...")
            RootExecutor.execute("hcitool ledc $handle")
        } else {
            emit("Could not extract connection handle from output.")
            emit("Raw output: $leccResult")
        }
    }

    /**
     * Retrieves the bond state of a device using the Android API.
     * Falls back to [BluetoothDevice.BOND_NONE] if context is unavailable.
     */
    private fun getBondState(context: Context?, mac: String): Int {
        if (context == null) return BluetoothDevice.BOND_NONE
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = manager.adapter?.getRemoteDevice(mac)
            device?.bondState ?: BluetoothDevice.BOND_NONE
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException checking bond state: ${e.message}")
            BluetoothDevice.BOND_NONE
        } catch (e: Exception) {
            Log.w(TAG, "Error checking bond state: ${e.message}")
            BluetoothDevice.BOND_NONE
        }
    }

    /**
     * Auto-unpairs the target device after the audit completes.
     *
     * Tries the Android reflection approach first (calling the hidden removeBond()
     * method), then falls back to btmgmt unpair.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.autoUnpair(
        mac: String,
        context: Context?
    ) {
        emit("Auto-unpairing test bond...")

        // Try via reflection on the hidden BluetoothDevice.removeBond() method
        if (context != null) {
            try {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = manager.adapter?.getRemoteDevice(mac)
                if (device != null) {
                    val removeBondMethod = device.javaClass.getMethod("removeBond")
                    val result = removeBondMethod.invoke(device) as Boolean
                    if (result) {
                        emit("Auto-unpair succeeded (via reflection).")
                        return
                    } else {
                        emit("Auto-unpair returned false. Trying btmgmt fallback...")
                    }
                }
            } catch (e: Exception) {
                emit("Reflection unpair failed: ${e.message}. Trying btmgmt fallback...")
            }
        }

        // Fallback: btmgmt unpair
        val unpairResult = RootExecutor.execute("btmgmt unpair $mac")
        if (unpairResult.startsWith("Error")) {
            emit("Auto-unpair via btmgmt failed: $unpairResult")
            Log.w(TAG, "Failed to unpair $mac: $unpairResult")
        } else {
            emit("Auto-unpair succeeded (via btmgmt).")
        }
    }

    /**
     * Checks if a binary is available in PATH via root shell.
     */
    private suspend fun checkBinary(name: String): Boolean {
        val result = RootExecutor.execute("which $name")
        return !result.startsWith("Error") && result.isNotBlank()
    }
}

/**
 * Pairing methods that can be negotiated during BLE SMP.
 */
enum class PairingMethod(val displayName: String) {
    JUST_WORKS("Just Works (Unauthenticated)"),
    PASSKEY_ENTRY("Passkey Entry"),
    NUMERIC_COMPARISON("Numeric Comparison"),
    OOB("Out-of-Band"),
    REJECTED("Pairing Rejected"),
    UNKNOWN("Unknown / Inconclusive")
}

/**
 * Result of a pairing method audit.
 *
 * @property method The identified pairing method.
 * @property vulnerable Whether the pairing is considered weak (Just Works accepted by a
 *   device that should support stronger auth).
 * @property bonded Whether the device actually bonded per [BluetoothDevice.getBondState].
 * @property detail Human-readable explanation.
 */
data class PairingMethodResult(
    val method: PairingMethod,
    val vulnerable: Boolean,
    val bonded: Boolean,
    val detail: String
)
