package com.hereliesaz.blusnu.ui.report

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.LogEntry
import com.hereliesaz.blusnu.data.TargetDevice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ViewModel for generating reports and handling data IO.
 */
class ReportViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository? = null // Passed via factory in MainActivity
) : AndroidViewModel(application) {

    // Subscribe to global action logs.
    val logs: StateFlow<List<LogEntry>> = ActionLogger.logs

    /**
     * Formats the current log history into a Markdown string.
     */
    fun generateMarkdownReport(): String {
        val builder = StringBuilder()
        builder.append("# BluSnu Security Report\n\n")
        builder.append("## Logged Actions\n\n")
        logs.value.forEach { log ->
            builder.append("- **${log.timestamp}**: ${log.message}\n")
        }
        return builder.toString()
    }

    /**
     * Exports the entire device database to a JSON file.
     * Uses ContentResolver to write to the user-selected Uri.
     */
    fun exportData(uri: Uri) {
        if (deviceRepository == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val devices = deviceRepository.getAllDevicesOneShot()
            val json = Gson().toJson(devices)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
        }
    }

    /**
     * Imports devices from a JSON file.
     */
    fun importData(uri: Uri) {
        if (deviceRepository == null) return
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                val listType = object : TypeToken<List<TargetDevice>>() {}.type
                val devices: List<TargetDevice> = Gson().fromJson(json, listType)
                devices.forEach { device ->
                    deviceRepository.insert(device)
                }
            }
        }
    }
}
