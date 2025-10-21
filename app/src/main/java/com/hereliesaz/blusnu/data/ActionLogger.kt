package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val timestamp: String, val message: String)

object ActionLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val newEntry = LogEntry(timestamp, message)
        _logs.value = _logs.value + newEntry
    }

    fun getLogs(): List<LogEntry> {
        return _logs.value
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
