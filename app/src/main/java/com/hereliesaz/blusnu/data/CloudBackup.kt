package com.hereliesaz.blusnu.data

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.File

/**
 * Utility class for backing up the local application database to a cloud endpoint.
 *
 * This functionality is crucial for preserving assessment data across devices
 * or reinstallations.
 *
 * @property context Application context to access the database file.
 * @property client Ktor HttpClient for the upload request.
 */
class CloudBackup(private val context: Context, private val client: HttpClient) {

    /**
     * Uploads the local Room database file to a specified URL.
     *
     * @param url The endpoint URL to upload to.
     * @return true if upload succeeded, false otherwise.
     */
    suspend fun backupDatabase(url: String): Boolean {
        return try {
            // Locate the internal database file.
            val dbFile = context.getDatabasePath("blusnu_database")

            if (!dbFile.exists()) {
                println("Database file not found at ${dbFile.absolutePath}")
                return false
            }

            println("Starting database backup to $url...")

            // Construct and execute a Multipart POST request.
            client.post(url) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            // Append the file part.
                            append("database", dbFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"${dbFile.name}\"")
                            })
                        }
                    )
                )
            }
            println("Database backup successful.")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            println("Database backup failed: ${e.message}")
            false
        }
    }
}
