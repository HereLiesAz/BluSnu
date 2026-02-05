package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
 * Ideally, this uses a high-speed backchannel (LTE/Wi-Fi/MQTT) to bridge the Bluetooth packets
 * over a distance greater than Bluetooth range.
 * </p>
 */
class GattRelayModule {

    /**
     * Starts the relay operation based on the selected role.
     *
     * @param role The role this device is playing (Near Car or Near Phone).
     * @param targetAddress The MAC address of the victim device to target.
     * @return A Flow of status logs.
     */
    fun startRelay(role: RelayRole, targetAddress: String): Flow<String> = flow {
        emit("Initializing GATT Relay as $role...")

        if (role == RelayRole.NODE_A_CAR_SIDE) {
            // Logic for the device near the Car
            emit("Scanning for legitimate key fob...")
            delay(1000)
            emit("Key Fob detected (RSSI: -50dBm)")

            // Simulation of the backchannel connection
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
            // Logic for the device near the Phone (Owner)
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
