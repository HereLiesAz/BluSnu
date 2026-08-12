package com.hereliesaz.blusnu.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Data class representing the payload exchanged between Tandem peers.
 *
 * @property deviceName Name of the peer device.
 * @property targetMac The MAC address of the target being tracked.
 * @property rssi The RSSI of the target as seen by the peer.
 * @property latitude The peer's GPS latitude.
 * @property longitude The peer's GPS longitude.
 * @property distance The estimated distance from the peer to the target (meters).
 */
data class TandemData(
    val deviceName: String,
    val targetMac: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val distance: Double // Calculated distance from sender to target
)

/**
 * Manager class for "Tandem Mode" (Cooperative Triangulation).
 *
 * This class allows two instances of Blu Snu running on different devices on the same
 * local network (WiFi) to discover each other and exchange target tracking data.
 * It uses Android's Network Service Discovery (NSD / mDNS) to find peers and
 * standard TCP sockets for communication.
 *
 * @param context The application context, required for NSD.
 */
class TandemManager(private val context: Context? = null) {
    // Tracks if the Tandem session is active.
    private val _isTandemActive = MutableStateFlow(false)
    val isTandemActive = _isTandemActive.asStateFlow()

    // Stream of incoming data from peers.
    private val _tandemData = MutableSharedFlow<TandemData>()
    val tandemData = _tandemData.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Server socket to accept incoming connections.
    private var serverSocket: ServerSocket? = null

    // Android NSD Manager for service discovery.
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // List of connected peer sockets.
    private val peers = mutableListOf<Socket>()
    private val gson = Gson()

    // Service type identifier for mDNS (must include protocol).
    private val SERVICE_TYPE = "_blusnu._tcp"
    // Prefix to identify our specific service instances.
    private val SERVICE_NAME_PREFIX = "BluSnu_Tandem_"

    /**
     * Starts the Tandem session: opens a server port, registers the service via NSD,
     * and starts discovery for other peers.
     */
    fun startSession() {
        if (_isTandemActive.value) return

        scope.launch {
            try {
                // Bind to port 0 to let the OS assign an available ephemeral port.
                serverSocket = ServerSocket(0)
                val localPort = serverSocket?.localPort ?: return@launch

                _isTandemActive.value = true

                // Register NSD Service if context is available.
                if (context != null) {
                    nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                    registerService(localPort)
                    startDiscovery()
                }

                // Main loop: Listen for incoming connections from peers.
                while (_isTandemActive.value) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            handleNewConnection(client)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isTandemActive.value = false
            }
        }
    }

    /**
     * Registers the local service on the network via NSD.
     */
    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            // Append random number to name to avoid collisions.
            serviceName = "$SERVICE_NAME_PREFIX${(1000..9999).random()}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                // Service is now visible on the network.
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Registration failed.
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                // Service removed.
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Unregistration failed.
            }
        }

        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    /**
     * Starts discovering other Blu Snu instances on the network.
     */
    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                // Check if the found service matches our app's signature.
                // Note: service.serviceType usually has a dot at the end or extra info.
                if (service.serviceType.contains("_blusnu") && service.serviceName.startsWith(SERVICE_NAME_PREFIX)) {
                    // We found a service, now we need to resolve its IP and Port.
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            // Avoid connecting to ourselves.
                            if (serviceInfo.port == serverSocket?.localPort) return

                            // Connect to the peer.
                            connectToPeer(serviceInfo.host, serviceInfo.port)
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /**
     * Establishes a TCP connection to a resolved peer.
     */
    private fun connectToPeer(host: InetAddress, port: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Create socket connection.
                val socket = Socket(host, port)
                handleNewConnection(socket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Handles a newly established socket connection (incoming or outgoing).
     * Starts a listener loop to read data from this peer.
     */
    private fun handleNewConnection(socket: Socket) {
        synchronized(peers) {
            peers.add(socket)
        }

        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                // Keep reading as long as the session is active and socket is open.
                while (_isTandemActive.value && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    try {
                        // deserialize JSON to TandemData.
                        val data = gson.fromJson(line, TandemData::class.java)
                        _tandemData.emit(data)
                    } catch (e: Exception) {
                        // Ignore malformed JSON.
                    }
                }
            } catch (e: Exception) {
                // Connection lost.
            } finally {
                // Cleanup when loop exits.
                synchronized(peers) {
                    peers.remove(socket)
                }
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }

    /**
     * Broadcasts the local device's tracking data to all connected peers.
     *
     * @param data The TandemData object containing local position and target info.
     */
    fun broadcastData(data: TandemData) {
        if (!_isTandemActive.value) return
        val json = gson.toJson(data)

        synchronized(peers) {
            peers.forEach { socket ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println(json)
                    } catch (e: Exception) {
                        // Error writing to specific peer.
                    }
                }
            }
        }
    }

    /**
     * Stops the session, closes sockets, and unregisters NSD service.
     */
    fun stopSession() {
        _isTandemActive.value = false
        try {
            if (nsdManager != null) {
                registrationListener?.let { nsdManager?.unregisterService(it) }
                discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            }
        } catch (_: Exception) {}
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        synchronized(peers) {
            peers.forEach { try { it.close() } catch (_: Exception) {} }
            peers.clear()
        }
    }

    fun close() {
        stopSession()
        scope.cancel()
    }
}
