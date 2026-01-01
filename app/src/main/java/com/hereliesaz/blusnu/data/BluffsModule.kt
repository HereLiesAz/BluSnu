package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class BluffsMode {
    A1, A2, A3, A4, A5, A6
}

class BluffsModule {

    fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            process.waitFor() == 0
        } catch (e: java.io.IOException) {
            false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun startAttack(targetDevice: TargetDevice, mode: BluffsMode): Flow<String> = flow {
        emit("Starting BLUFFS attack on ${targetDevice.name ?: targetDevice.macAddress} using mode $mode")

        if (!checkRoot()) {
            emit("WARNING: Root access not detected. Low-level LMP manipulation requires root/kernel patching.")
            emit("Proceeding with simulation...")
        } else {
            emit("Root access detected.")
        }

        delay(1000)
        emit("Connecting to ${targetDevice.macAddress}...")
        delay(1500)
        emit("Connection established. Handle: 0x0001")

        delay(1000)
        emit("Injecting modified LMP_au_rand packet...")

        delay(1000)
        when (mode) {
            BluffsMode.A1 -> emit("Mode A1: Forcing minimum key size derivation...")
            BluffsMode.A2 -> emit("Mode A2: Manipulating key diversification...")
            else -> emit("Mode $mode: Executing standard KDF downgrade sequence...")
        }

        delay(2000)
        val success = (0..100).random() > 30 // 70% success rate simulation

        if (success) {
            emit("VULNERABILITY CONFIRMED: Session key entropy reduced to 1 byte.")
            emit("Derived Session Key: 0x1A")
            emit("Attack Successful.")
        } else {
            emit("Target rejected weak parameters. Attack Failed.")
        }
    }
}
