package com.hereliesaz.blusnu.data

import kotlin.math.pow

/**
 * A module responsible for geolocation-related calculations.
 */
class GeolocationModule {
    private val kalmanFilters = mutableMapOf<String, KalmanFilter>()

    /**
     * Estimates the distance to a Bluetooth device using the log-distance path loss model.
     *
     * @param rssi The Received Signal Strength Indicator in dBm.
     * @param txPower The average RSSI at a reference distance of 1 meter.
     * @param pathLossExponent The path loss exponent, which varies depending on the environment.
     *                         (e.g., 2.0 for free space, 1.6-1.8 for indoors).
     * @return The estimated distance in meters.
     */
    fun calculateDistance(rssi: Double, txPower: Double = -59.0, pathLossExponent: Double = 2.0): Double {
        return 10.0.pow((txPower - rssi) / (10 * pathLossExponent))
    }

    /**
     * Smooths an RSSI value using a Kalman filter.
     *
     * @param macAddress The MAC address of the device.
     * @param rssi The new RSSI measurement.
     * @return The smoothed RSSI value.
     */
    fun smoothRssi(macAddress: String, rssi: Double): Double {
        val kalmanFilter = kalmanFilters.getOrPut(macAddress) { KalmanFilter() }
        return kalmanFilter.filter(rssi)
    }
}

/**
 * A simple Kalman filter for smoothing RSSI values.
 *
 * @property processNoise The process noise, which represents the uncertainty in the model.
 * @property measurementNoise The measurement noise, which represents the uncertainty in the measurement.
 */
class KalmanFilter(
    private val processNoise: Double = 0.125,
    private val measurementNoise: Double = 2.0 // Decreased from 4.0 to make it more sensitive to changes
) {
    private var errorEstimate = 1.0
    private var currentEstimate = 0.0
    private var lastEstimate = 0.0

    /**
     * Filters a new RSSI measurement.
     *
     * @param measurement The new RSSI measurement.
     * @return The smoothed RSSI value.
     */
    fun filter(measurement: Double): Double {
        lastEstimate = currentEstimate
        val kalmanGain = errorEstimate / (errorEstimate + measurementNoise)
        currentEstimate = lastEstimate + kalmanGain * (measurement - lastEstimate)
        errorEstimate = (1.0 - kalmanGain) * errorEstimate + kotlin.math.abs(lastEstimate - currentEstimate) * processNoise
        return currentEstimate
    }
}
