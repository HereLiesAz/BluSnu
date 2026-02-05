package com.hereliesaz.blusnu.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data class representing the response from the MAC Lookup API.
 *
 * @property success API success status.
 * @property found Whether the MAC address was found in the database.
 * @property company The name of the vendor/manufacturer (if found).
 */
@Serializable
data class MacLookupResponse(
    val success: Boolean,
    val found: Boolean,
    val company: String? = null
)

/**
 * Client for performing MAC Address OUI lookups via an external API.
 *
 * This allows the app to identify the manufacturer of a device (e.g., "Apple, Inc.", "Samsung Electronics")
 * based on the first 3 bytes of its MAC address.
 *
 * @property client The Ktor HttpClient used for network requests.
 */
class MacLookupClient(private val client: HttpClient) {

    // Json configuration to ignore extra fields in the API response.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Resolves a vendor name from a MAC address.
     *
     * @param macAddress The MAC address to lookup.
     * @return The vendor name, or null if lookup failed or not found.
     */
    suspend fun getVendor(macAddress: String): String? {
        val response: MacLookupResponse = try {
            // Perform GET request to maclookup.app API.
            val httpResponse = client.get("https://api.maclookup.app/v2/macs/$macAddress")
            // Deserialize response body.
            json.decodeFromString(httpResponse.body())
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        // Return company name if successful.
        return if (response.success && response.found) {
            response.company
        } else {
            null
        }
    }
}
