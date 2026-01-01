package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR
}

data class BluetoothLogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

class BluetoothLog {

    private val _logs = MutableSharedFlow<BluetoothLogEntry>(replay = 100)
    val logs = _logs.asSharedFlow()

    suspend fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _logs.emit(BluetoothLogEntry(System.currentTimeMillis(), level, message))
    }
}
