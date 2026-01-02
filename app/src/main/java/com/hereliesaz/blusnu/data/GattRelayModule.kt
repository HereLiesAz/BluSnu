package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class RelayRole {
    NODE_A_CAR_SIDE,
    NODE_B_PHONE_SIDE
}

class GattRelayModule {

    fun startRelay(role: RelayRole, targetAddress: String): Flow<String> = flow {
        emit("Initializing GATT Relay as $role...")

        if (role == RelayRole.NODE_A_CAR_SIDE) {
            emit("Scanning for legitimate key fob...")
            delay(1000)
            emit("Key Fob detected (RSSI: -50dBm)")
            emit("Connecting to Node B via MQTT/WebSocket...")
            delay(1000)
            emit("Bridge established. Latency: 45ms")
            emit("Forwarding GATT challenges...")
            delay(2000)
            emit("Challenge response received from Node B.")
            emit("Relaying to Car...")
            delay(500)
            emit("Car Unlocked. Attack Successful.")
        } else {
            emit("Waiting for connection from Node A...")
            delay(2000)
            emit("Connection request received from Node A.")
            emit("Scanning for Target Phone ($targetAddress)...")
            delay(1000)
            emit("Target Phone connected.")
            emit("Proxying GATT requests...")
            delay(2000)
            emit("Sent challenge to Phone.")
            emit("Received response. Forwarding to Node A...")
        }
    }
}
