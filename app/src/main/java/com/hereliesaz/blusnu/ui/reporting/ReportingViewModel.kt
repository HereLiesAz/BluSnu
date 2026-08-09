package com.hereliesaz.blusnu.ui.reporting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportLogEntry(
    val timestamp: String,
    val message: String
)

class ReportingViewModel(application: Application) : AndroidViewModel(application) {

    private val _logs = MutableStateFlow<List<ReportLogEntry>>(emptyList())
    val logs: StateFlow<List<ReportLogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun addLog(message: String) {
        val entry = ReportLogEntry(
            timestamp = dateFormat.format(Date()),
            message = message
        )
        _logs.value = _logs.value + entry
    }

    fun generateMarkdownReport(): String {
        val sb = StringBuilder()
        sb.appendLine("# BluSnu Report")
        sb.appendLine()
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("## Action Log")
        sb.appendLine()
        if (_logs.value.isEmpty()) {
            sb.appendLine("No actions logged.")
        } else {
            sb.appendLine("| Timestamp | Action |")
            sb.appendLine("|-----------|--------|")
            _logs.value.forEach { entry ->
                sb.appendLine("| ${entry.timestamp} | ${entry.message} |")
            }
        }
        sb.appendLine()
        return sb.toString()
    }

    /**
     * Generates a JSON representation of the report (metadata + action log).
     *
     * PDF export is intentionally out of scope (future work); Markdown and JSON
     * are the two supported export formats.
     */
    fun generateJsonReport(): String {
        val root = JSONObject()
        root.put("title", "BluSnu Report")
        root.put("generated", dateFormat.format(Date()))

        val logsArray = JSONArray()
        _logs.value.forEach { entry ->
            val logObject = JSONObject()
            logObject.put("timestamp", entry.timestamp)
            logObject.put("message", entry.message)
            logsArray.put(logObject)
        }
        root.put("actionLog", logsArray)

        return root.toString(2)
    }
}
