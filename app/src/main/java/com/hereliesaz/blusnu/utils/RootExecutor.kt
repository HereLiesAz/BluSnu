package com.hereliesaz.blusnu.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.InputStream

/**
 * Utility for executing shell commands with Superuser (Root) privileges.
 *
 * This singleton provides a suspend function to execute commands in a root shell
 * and capture the output. It is essential for modules that require direct access
 * to the Bluetooth hardware (e.g., `hcitool`, `l2ping`, `btmgmt`) or system files.
 */
object RootExecutor {

    /**
     * Executes a command as root.
     *
     * @param command The shell command string to execute (e.g., "hcitool scan").
     * @return The standard output (stdout) of the command as a String.
     *         If execution fails, returns the error message or "Unknown error".
     */
    suspend fun execute(command: String): String {
        // Run on the IO dispatcher to avoid blocking the main thread.
        return withContext(Dispatchers.IO) {
            try {
                // Request a root shell.
                val process = Runtime.getRuntime().exec("su")

                // Get output stream to write commands TO the shell.
                val os = DataOutputStream(process.outputStream)
                // Get input stream to read output FROM the shell.
                val `is`: InputStream = process.inputStream

                // Write the command followed by newline.
                os.writeBytes("$command\n")
                // Exit the shell after the command completes.
                os.writeBytes("exit\n")

                // Flush and close the output stream to signal command end.
                os.flush()
                os.close()

                // Read the output buffer.
                val reader = `is`.bufferedReader()
                val output = reader.readText()

                // Wait for the process to terminate.
                process.waitFor()

                // Return the captured output.
                output
            } catch (e: Exception) {
                // Handle exceptions (e.g., Root denied, command not found).
                e.message ?: "Unknown error"
            }
        }
    }
}
