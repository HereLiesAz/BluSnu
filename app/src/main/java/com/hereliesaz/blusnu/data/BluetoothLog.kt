package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Log levels for Bluetooth logging.
 */
enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR
}

/**
 * Data class representing a structured log entry for Bluetooth events.
 */
data class BluetoothLogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

/**
 * A dedicated logger for Bluetooth-related events.
 *
 * This logger emits events to a SharedFlow which can be observed by the
 * "Bluetooth Log" screen in the UI. It maintains a small replay buffer
 * so new subscribers can see recent history.
 */
class BluetoothLog {

    // SharedFlow with replay=100 ensures the last 100 logs are available to new observers (like screen rotation).
    private val _logs = MutableSharedFlow<BluetoothLogEntry>(replay = 100)
    val logs = _logs.asSharedFlow()

    /**
     * Emits a new log entry.
     *
     * @param message The log text.
     * @param level The severity level.
     */
    suspend fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _logs.emit(BluetoothLogEntry(System.currentTimeMillis(), level, message))
    }
}
