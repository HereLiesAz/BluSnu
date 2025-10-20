package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow

data class DevicePosition(val distance: Float, val angle: Float)

/**
 * A placeholder for the device geolocation module.
 *
 * In a real implementation, this class would use advanced signal processing
 * techniques (like Kalman filters or trilateration) to provide a more
 * accurate and stable position estimate.
 */
class GeolocationModule {

    private val _devices = MutableStateFlow<Map<TargetDevice, DevicePosition>>(emptyMap())
    val devices = _devices.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun updateDevicePosition(device: TargetDevice, rssi: Int, txPower: Int) {
        scope.launch {
            val distance = calculateDistance(rssi, txPower)
            // In a real application, the angle would be determined by the hardware,
            // possibly using an antenna array. For this placeholder, we generate a
            // stable but random-looking angle based on the device's MAC address.
            val angle = getStableRandomAngle(device.macAddress)
            val currentDevices = _devices.value.toMutableMap()
            currentDevices[device] = DevicePosition(distance, angle)
            _devices.value = currentDevices
            Log.d("GeolocationModule", "Updated position for ${device.macAddress}: distance=$distance, angle=$angle")
        }
    }

    private fun getStableRandomAngle(macAddress: String): Float {
        return (macAddress.hashCode() % 360).toFloat()
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Float {
        // A simple implementation of the log-distance path loss model.
        return 10.0.pow(((txPower - rssi) / (10 * 2)).toDouble()).toFloat()
    }
}
