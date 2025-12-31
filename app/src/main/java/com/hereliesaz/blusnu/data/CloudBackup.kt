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

class CloudBackup(private val context: Context, private val client: HttpClient) {

    suspend fun backupDatabase(url: String): Boolean {
        return try {
            val dbFile = context.getDatabasePath("blusnu_database")
            if (!dbFile.exists()) {
                println("Database file not found at ${dbFile.absolutePath}")
                return false
            }

            println("Starting database backup to $url...")

            client.post(url) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
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
