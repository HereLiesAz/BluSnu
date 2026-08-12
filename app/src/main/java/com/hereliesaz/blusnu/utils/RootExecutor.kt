package com.hereliesaz.blusnu.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    private const val TAG = "RootExecutor"

    /**
     * Executes a command as root.
     *
     * Captures stdout, stderr, and the process exit code. Previously only stdout was read, so a
     * failing command (non-zero exit) that wrote its diagnostics to stderr looked like an empty
     * success. Now stderr is appended to the returned text and a non-zero exit code is both logged
     * and annotated in the output, so callers can tell success from failure.
     *
     * @param command The shell command string to execute (e.g., "hcitool scan").
     * @return The combined stdout (and stderr, if any) of the command as a String. On a non-zero
     *         exit code the returned text is prefixed with an error marker including the code. If
     *         the root shell cannot be started, returns the exception message or "Unknown error".
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
                // Get error stream to read stderr FROM the shell.
                val errStream: InputStream = process.errorStream

                // Write the command followed by newline.
                os.writeBytes("$command\n")
                // Exit the shell after the command completes.
                os.writeBytes("exit\n")

                // Flush and close the output stream to signal command end.
                os.flush()
                os.close()

                // Read stdout and stderr concurrently to avoid deadlock. If stderr
                // fills its OS pipe buffer (~64 KB) before stdout is fully consumed,
                // the child blocks on its stderr write and never closes stdout,
                // causing the sequential reader to hang forever.
                val stdoutDeferred = async { `is`.bufferedReader().readText() }
                val stderrDeferred = async { errStream.bufferedReader().readText() }
                val stdout = stdoutDeferred.await()
                val stderr = stderrDeferred.await()

                // Wait for the process to terminate and capture the exit code.
                val exitCode = process.waitFor()

                // Combine stdout and stderr into the reported output.
                val combined = buildString {
                    append(stdout)
                    if (stderr.isNotBlank()) {
                        if (isNotEmpty() && !endsWith("\n")) append("\n")
                        append(stderr)
                    }
                }

                if (exitCode != 0) {
                    // Log the failure so it is diagnosable, and make it visible to callers.
                    Log.w(TAG, "Command exited with code $exitCode: $command\n$combined")
                    "Error (exit code $exitCode): ${combined.ifBlank { "no output" }}"
                } else {
                    combined
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., Root denied, command not found).
                Log.e(TAG, "Failed to execute root command: $command", e)
                e.message ?: "Unknown error"
            }
        }
    }

    /**
     * Best-effort check for whether a root shell is actually available on this device.
     *
     * Runs `id` in a root shell and looks for `uid=0`. Returns false if `su` is missing, root
     * access is denied, or the command otherwise fails. This performs no privileged changes.
     *
     * @return true only if a root shell reports uid 0.
     */
    suspend fun isRootAvailable(): Boolean {
        val result = execute("id")
        return result.contains("uid=0")
    }
}
