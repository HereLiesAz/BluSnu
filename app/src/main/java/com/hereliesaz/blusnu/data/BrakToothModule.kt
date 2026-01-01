package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BrakToothModule {

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var isConnected = false

    suspend fun connectToEsp32() {
        log("Attempting to connect to ESP32 dongle via USB-OTG...")
        _connectionStatus.value = "Connecting..."
        delay(1500) // Simulate connection delay

        // Simulation: 50% chance of finding the dongle
        val success = Math.random() < 0.8 // high success for demo
        if (success) {
            isConnected = true
            _connectionStatus.value = "Connected (ESP32 - BrakTooth Fuzzer)"
            log("Connected to ESP32 firmware v1.0")
        } else {
            isConnected = false
            _connectionStatus.value = "Connection Failed"
            log("Error: No supported ESP32 device found.")
        }
    }

    suspend fun runCrashTest(targetMac: String) {
        if (!isConnected) {
            log("Error: Must connect to ESP32 hardware first.")
            return
        }

        log("Targeting $targetMac for BrakTooth crash test...")
        delay(500)

        // Simulate sending commands to ESP32
        log("Sending LMP_feature_req flood...")
        delay(1000)
        log("Sending oversized LMP packet...")
        delay(1000)

        log("Monitoring target responsiveness...")
        delay(2000)

        // Simulation result
        val crashed = Math.random() < 0.3
        if (crashed) {
            log("CRITICAL: Target stopped responding (Potential Crash/DoS).")
        } else {
            log("Target appears stable.")
        }
    }

    private fun log(message: String) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(message)
        _logs.value = currentLogs
    }
}
