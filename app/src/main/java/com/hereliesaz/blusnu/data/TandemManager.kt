package com.hereliesaz.blusnu.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket

data class TandemData(
    val deviceName: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double
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
                    registerService(localPort)
                }

                // Listen for connections
                while (_isTandemActive.value) {
                    try {
                        val client = serverSocket?.accept()
                        // Simulate receiving data handshake
                        // In a real app, we'd read input stream.
                        // Here we just simulate data arrival for UI feedback.
                        simulateIncomingData()
                        client?.close()
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                _isTandemActive.value = false
            }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "BluSnu_Tandem_${(1000..9999).random()}"
            serviceType = "_blusnu._tcp"
            setPort(port)
        }

        nsdManager = context?.getSystemService(Context.NSD_SERVICE) as? NsdManager

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

    private suspend fun simulateIncomingData() {
        // Simulate a packet from the other device
        _tandemData.emit(
            TandemData(
                deviceName = "Tandem Unit 1",
                rssi = (-90..-50).random(),
                latitude = 0.0,
                longitude = 0.0
            )
        )
    }

    fun stopSession() {
        scope.launch {
            _isTandemActive.value = false
            try {
                if (nsdManager != null && registrationListener != null) {
                    nsdManager?.unregisterService(registrationListener)
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
        }
    }
}
