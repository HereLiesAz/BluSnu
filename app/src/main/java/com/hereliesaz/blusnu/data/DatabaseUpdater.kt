package com.hereliesaz.blusnu.data

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.File
import java.security.MessageDigest

/**
 * Utility to manage updates to the internal vulnerability database.
 *
 * Fetches the latest vulnerability data from a remote URL, compares it against
 * the local copy using SHA-256 hashes, and persists updates to internal storage.
 *
 * @property context Application context.
 */
class DatabaseUpdater(private val context: Context) {

    companion object {
        /** Remote URL to fetch the latest vulnerability database from. */
        const val UPDATE_URL =
            "https://raw.githubusercontent.com/HereLiesAz/BluSnu/main/app/src/main/assets/vulnerabilities.json"

        private const val LOCAL_FILENAME = "vulnerabilities.json"
    }

    private val httpClient = HttpClient(CIO)

    /**
     * Checks for updates by comparing the local vulnerability database against
     * the remote source.
     *
     * 1. Reads the current local data (from internal storage if previously updated,
     *    otherwise from bundled assets).
     * 2. Fetches the remote data via HTTP GET.
     * 3. Compares SHA-256 hashes.
     * 4. If different, writes the new content to internal storage.
     *
     * @return Status message string describing the outcome.
     */
    suspend fun checkForUpdates(): String {
        // Read the current local data
        val localData = try {
            getVulnerabilityData()
        } catch (e: Exception) {
            return "Error reading local database: ${e.message}"
        }

        // Fetch remote data
        val remoteData = try {
            val response: HttpResponse = httpClient.get(UPDATE_URL)
            response.bodyAsText()
        } catch (e: Exception) {
            return "Error fetching remote database: ${e.message}"
        }

        // Compare hashes
        val localHash = sha256(localData)
        val remoteHash = sha256(remoteData)

        return if (localHash != remoteHash) {
            // Write updated data to internal storage
            try {
                val file = File(context.filesDir, LOCAL_FILENAME)
                file.writeText(remoteData)
                "Database updated successfully. ${remoteData.length} bytes written."
            } catch (e: Exception) {
                "Error writing updated database: ${e.message}"
            }
        } else {
            "No new updates available."
        }
    }

    /**
     * Returns the most recent vulnerability data as a string.
     *
     * Checks internal storage first (for previously downloaded updates),
     * then falls back to the bundled asset.
     *
     * @return The vulnerability JSON data as a string.
     */
    fun getVulnerabilityData(): String {
        // Check internal storage first (updated data takes precedence)
        val internalFile = File(context.filesDir, LOCAL_FILENAME)
        if (internalFile.exists() && internalFile.length() > 0) {
            return internalFile.readText()
        }

        // Fall back to bundled asset
        return context.assets.open(LOCAL_FILENAME).bufferedReader().use { it.readText() }
    }

    /**
     * Computes the SHA-256 hash of a string and returns it as a hex string.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
