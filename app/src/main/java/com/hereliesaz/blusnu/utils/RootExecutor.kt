package com.hereliesaz.blusnu.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.InputStream

object RootExecutor {
    suspend fun execute(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                val `is`: InputStream = process.inputStream
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
                os.close()
                val reader = `is`.bufferedReader()
                val output = reader.readText()
                process.waitFor()
                output
            } catch (e: Exception) {
                e.message ?: "Unknown error"
            }
        }
    }
}
