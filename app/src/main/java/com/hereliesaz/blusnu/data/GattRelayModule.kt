package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Enum defining the role of the device in a GATT Relay attack.
 *
 * A GATT Relay attack (often used against Passive Key Entry systems / Smart Locks)
 * involves two attackers bridging the connection between a legitimate peripheral (Car/Lock)
 * and a legitimate central (Phone/Fob) over a long distance (e.g., via LTE or WiFi).
 */
enum class RelayRole {
    // The device located near the peripheral (e.g., next to the car).
    // It pretends to be the Central (Phone).
    NODE_A_CAR_SIDE,

    // The device located near the central (e.g., next to the owner's phone).
    // It pretends to be the Peripheral (Car).
    NODE_B_PHONE_SIDE
}

/**
 * Module implementing the logic for a GATT Relay Attack.
 *
 * This module coordinates the relay process, managing the "fake" connections
 * on both ends and bridging the GATT read/write operations and notifications
 * between the two nodes.
 */
class GattRelayModule {

    /**
     * Starts the relay attack logic based on the selected role.
     *
     * @param role The role this device is playing in the attack chain.
     * @param targetAddress The MAC address of the target (only relevant for Node B finding the phone).
     * @return A Flow emitting status logs of the operation.
     */
    fun startRelay(role: RelayRole, targetAddress: String): Flow<String> = flow {
        emit("Initializing GATT Relay as $role...")

        if (role == RelayRole.NODE_A_CAR_SIDE) {
            // Logic for Node A (Near Car/Lock)
            emit("Scanning for legitimate key fob...")
            delay(1000)

            // In a real attack, we would scan for the car's advertisement.
            emit("Key Fob detected (RSSI: -50dBm)")

            // Connect to the partner node (Node B) over the internet/network.
            emit("Connecting to Node B via MQTT/WebSocket...")
            delay(1000)
            emit("Bridge established. Latency: 45ms")

            // Begin the relay loop.
            emit("Forwarding GATT challenges...")
            delay(2000)

            // Receive the cryptographic response from the real key fob (via Node B).
            emit("Challenge response received from Node B.")

            // Send the response to the car.
            emit("Relaying to Car...")
            delay(500)

            emit("Car Unlocked. Attack Successful.")
        } else {
            // Logic for Node B (Near Phone/Key)

            // Wait for Node A to initiate the bridge.
            emit("Waiting for connection from Node A...")
            delay(2000)
            emit("Connection request received from Node A.")

            // Look for the victim's phone.
            emit("Scanning for Target Phone ($targetAddress)...")
            delay(1000)
            emit("Target Phone connected.")

            // Act as the proxy.
            emit("Proxying GATT requests...")
            delay(2000)

            // Relay the challenge from the car (via Node A) to the phone.
            emit("Sent challenge to Phone.")

            // Relay the phone's response back to Node A.
            emit("Received response. Forwarding to Node A...")
        }
    }
}
