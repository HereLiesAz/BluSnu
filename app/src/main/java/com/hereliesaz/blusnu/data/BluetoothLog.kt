package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay
import android.annotation.SuppressLint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class BluetoothLog(
    private val bluetoothScanner: BluetoothScanner
) {

    private val _logs = MutableSharedFlow<String>()
    val logs = _logs.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        bluetoothScanner.startClassicDiscovery()
    }

    fun stop() {
        bluetoothScanner.stopClassicDiscovery()
    }

    suspend fun log(message: String) {
        _logs.emit(message)
    }
}
