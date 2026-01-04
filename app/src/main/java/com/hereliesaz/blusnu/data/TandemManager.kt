package com.hereliesaz.blusnu.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

data class TandemData(
    val deviceName: String,
    val targetMac: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val distance: Double // Calculated distance from sender to target
)

class TandemManager(private val context: Context? = null) {
    private val _isTandemActive = MutableStateFlow(false)
    val isTandemActive = _isTandemActive.asStateFlow()

    private val _tandemData = MutableSharedFlow<TandemData>()
    val tandemData = _tandemData.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val peers = mutableListOf<Socket>()
    private val gson = Gson()

    private val SERVICE_TYPE = "_blusnu._tcp"
    private val SERVICE_NAME_PREFIX = "BluSnu_Tandem_"

    fun startSession() {
        if (_isTandemActive.value) return

        scope.launch {
            try {
                // Bind to port 0 (ephemeral)
                serverSocket = ServerSocket(0)
                val localPort = serverSocket?.localPort ?: return@launch

                _isTandemActive.value = true

                // Register NSD Service if context is available
                if (context != null) {
                    nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                    registerService(localPort)
                    startDiscovery()
                }

                // Listen for connections
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

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX${(1000..9999).random()}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                // Registered
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Failed
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                // Unregistered
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Failed
            }
        }

        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                // Note: service.serviceType might contain additional dots or sync/TCP info.
                // We check if it contains our base type.
                if (service.serviceType.contains("_blusnu") && service.serviceName.startsWith(SERVICE_NAME_PREFIX)) {
                    // It's a different device (hopefully, need to check if it's us)
                    // We can resolve it.
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (serviceInfo.port == serverSocket?.localPort) return // It's us
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

    private fun connectToPeer(host: InetAddress, port: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Check if already connected to this IP?
                // For simplicity, just try connect.
                val socket = Socket(host, port)
                handleNewConnection(socket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleNewConnection(socket: Socket) {
        synchronized(peers) {
            peers.add(socket)
        }

        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (_isTandemActive.value && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    try {
                        val data = gson.fromJson(line, TandemData::class.java)
                        _tandemData.emit(data)
                    } catch (e: Exception) {
                        // Malformed data
                    }
                }
            } catch (e: Exception) {
                // Connection lost
            } finally {
                synchronized(peers) {
                    peers.remove(socket)
                }
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }

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
                        // Error writing
                    }
                }
            }
        }
    }

    fun stopSession() {
        scope.launch {
            _isTandemActive.value = false
            try {
                if (nsdManager != null) {
                    registrationListener?.let { nsdManager?.unregisterService(it) }
                    discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
                }
            } catch (e: Exception) {
                // Ignore
            }
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                // Ignore
            }
            serverSocket = null

            synchronized(peers) {
                peers.forEach { try { it.close() } catch (e: Exception) {} }
                peers.clear()
            }
        }
    }
}
