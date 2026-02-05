package com.hereliesaz.blusnu.data

import kotlin.math.pow

/**
 * A module responsible for geolocation-related calculations and signal processing.
 *
 * This class handles the conversion of RSSI (Received Signal Strength Indicator)
 * into estimated distance and provides filtering mechanisms to smooth out noisy
 * RSSI data.
 */
class GeolocationModule {
    // Map to store a separate Kalman filter instance for each tracked device (MAC address).
    private val kalmanFilters = mutableMapOf<String, KalmanFilter>()

    /**
     * Estimates the distance to a Bluetooth device using the log-distance path loss model.
     *
     * Formula: Distance = 10 ^ ((TxPower - RSSI) / (10 * N))
     *
     * @param rssi The Received Signal Strength Indicator in dBm.
     * @param txPower The calibrated RSSI at a reference distance of 1 meter.
     *                Default -59.0 dBm is a common average for BLE devices.
     * @param pathLossExponent The path loss exponent (N), which models environmental attenuation.
     *                         2.0 is typical for free space; indoors is usually 1.6 to 3.0.
     * @return The estimated distance in meters.
     */
    fun calculateDistance(rssi: Double, txPower: Double = -59.0, pathLossExponent: Double = 2.0): Double {
        return 10.0.pow((txPower - rssi) / (10 * pathLossExponent))
    }

    /**
     * Smooths an RSSI value using a Kalman filter.
     *
     * Raw RSSI values jump around significantly due to multipath fading and interference.
     * A Kalman filter provides a better estimate of the "true" signal strength over time.
     *
     * @param macAddress The MAC address of the device (to retrieve its specific filter state).
     * @param rssi The new raw RSSI measurement.
     * @return The smoothed RSSI value.
     */
    fun smoothRssi(macAddress: String, rssi: Double): Double {
        // Get existing filter or create a new one for this device.
        val kalmanFilter = kalmanFilters.getOrPut(macAddress) { KalmanFilter() }
        return kalmanFilter.filter(rssi)
    }
}

/**
 * A simple 1D Kalman filter implementation for smoothing RSSI values.
 *
 * @property processNoise The process noise (Q), representing the uncertainty in the system model (how much the true RSSI changes).
 * @property measurementNoise The measurement noise (R), representing the uncertainty in the measurement (sensor noise).
 *                            Lower values make the filter more responsive (sensitive) to changes; higher values make it smoother (laggy).
 */
class KalmanFilter(
    private val processNoise: Double = 0.125,
    private val measurementNoise: Double = 2.0 // Configured for responsiveness.
) {
    // Initial error estimate (covariance).
    private var errorEstimate = 1.0
    // The current estimated value (state).
    private var currentEstimate = 0.0
    // The previous estimated value.
    private var lastEstimate = 0.0

    /**
     * Filters a new RSSI measurement to update the estimate.
     *
     * @param measurement The new noisy measurement.
     * @return The updated, smoothed estimate.
     */
    fun filter(measurement: Double): Double {
        // Prediction step: Assume state hasn't changed (static model).
        lastEstimate = currentEstimate

        // Update step: Calculate Kalman Gain.
        // K = P / (P + R)
        val kalmanGain = errorEstimate / (errorEstimate + measurementNoise)

        // Update current estimate using the gain and the innovation (measurement - prediction).
        currentEstimate = lastEstimate + kalmanGain * (measurement - lastEstimate)

        // Update error estimate (covariance).
        // P = (1 - K) * P + |last - current| * Q
        errorEstimate = (1.0 - kalmanGain) * errorEstimate + kotlin.math.abs(lastEstimate - currentEstimate) * processNoise

        return currentEstimate
    }
}
