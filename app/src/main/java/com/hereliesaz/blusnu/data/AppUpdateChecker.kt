package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.BuildConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.headers
import io.ktor.client.statement.*

/**
 * Checks GitHub Releases for a newer version of Blu Snu.
 *
 * Returns the latest release tag if it is strictly newer than the running build,
 * or null if the app is up to date or the check fails. All network errors are
 * swallowed — a failed check should never interrupt the user.
 */
class AppUpdateChecker(private val httpClient: HttpClient) {

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/HereLiesAz/BluSnu/releases/latest"
    }

    /**
     * Returns the latest version string (e.g. "2.32.1.60") if it is newer than
     * [BuildConfig.VERSION_NAME], or null if the app is current or the check fails.
     */
    suspend fun getLatestVersionIfNewer(): String? = try {
        val body = httpClient.get(RELEASES_URL) {
            headers {
                append("Accept", "application/vnd.github+json")
                append("X-GitHub-Api-Version", "2022-11-28")
            }
        }.bodyAsText()

        val tagName = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
            .find(body)?.groupValues?.get(1) ?: return null

        val latest = tagName.trimStart('v')
        if (isNewer(latest, BuildConfig.VERSION_NAME)) latest else null
    } catch (_: Exception) {
        null
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
