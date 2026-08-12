package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data class representing a single log entry in the application's action log.
 *
 * @property timestamp The formatted string representing the time the log occurred.
 * @property message The content of the log message detailing the action or event.
 */
data class LogEntry(val timestamp: String, val message: String)

/**
 * Singleton object responsible for logging user actions and system events.
 *
 * This logger maintains an in-memory list of logs that are exposed as a StateFlow,
 * allowing the UI to reactively update and display the log history in real-time.
 * It is primarily used for the "Report" feature to generate audit trails of the
 * security assessment.
 */
object ActionLogger {
    // MutableStateFlow to hold the list of logs. Private to prevent external modification.
    // Initialized with an empty list.
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())

    // Public read-only view of the logs as a StateFlow.
    // Observers (like UI components) can collect this flow to receive updates.
    val logs = _logs.asStateFlow()

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Adds a new message to the log.
     *
     * This method captures the current time, formats it, creates a new LogEntry,
     * and appends it to the current list of logs.
     *
     * @param message The message string to log.
     */
    fun log(message: String) {
        val timestamp = LocalDateTime.now().format(dateFormat)
        val newEntry = LogEntry(timestamp, message)

        _logs.update { it + newEntry }
    }

    /**
     * Retrieves the current snapshot of all logs.
     *
     * @return A List of LogEntry objects representing the history of actions.
     */
    fun getLogs(): List<LogEntry> {
        // Return the current value held by the StateFlow.
        return _logs.value
    }

    /**
     * Clears all recorded logs.
     *
     * This is useful when starting a new session or clearing the history
     * after generating a report.
     */
    fun clearLogs() {
        // Reset the MutableStateFlow's value to an empty list.
        // This clears the history and notifies observers that the log is empty.
        _logs.value = emptyList()
    }
}
