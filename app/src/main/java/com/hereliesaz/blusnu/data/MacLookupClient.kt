package com.hereliesaz.blusnu.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MacLookupResponse(
    val success: Boolean,
    val found: Boolean,
    val company: String? = null
)

class MacLookupClient(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getVendor(macAddress: String): String? {
        val response: MacLookupResponse = try {
            val httpResponse = client.get("https://api.maclookup.app/v2/macs/$macAddress")
            json.decodeFromString(httpResponse.body())
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        return if (response.success && response.found) {
            response.company
        } else {
            null
        }
    }
}
