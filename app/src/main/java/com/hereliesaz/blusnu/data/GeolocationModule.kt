package com.hereliesaz.blusnu.data

import kotlin.math.abs
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

    /**
     * Clears the Kalman filter state for a device so its RSSI tracking restarts cleanly.
     *
     * The next [smoothRssi] call for this MAC will create a fresh filter that seeds itself
     * from the first new measurement (no startup transient). Call this when a device is
     * re-selected/re-tracked or when its readings should not be smoothed against stale history.
     *
     * @param macAddress The MAC address whose filter state should be removed.
     */
    fun resetFilter(macAddress: String) {
        kalmanFilters.remove(macAddress)
    }

    /**
     * Measures how well-distributed observation points are using convex hull area.
     * Returns 0.0 if collinear, larger values = better geometry for trilateration.
     * Points should be in local meter coordinates.
     */
    fun spatialSpread(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0

        // Convex hull via Graham scan
        val sorted = points.sortedWith(compareBy({ it.second }, { it.first }))
        val lower = mutableListOf<Pair<Double, Double>>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }
        val upper = mutableListOf<Pair<Double, Double>>()
        for (p in sorted.reversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        val hull = lower + upper
        if (hull.size < 3) return 0.0

        // Shoelace area
        var area = 0.0
        for (i in hull.indices) {
            val j = (i + 1) % hull.size
            area += hull[i].first * hull[j].second
            area -= hull[j].first * hull[i].second
        }
        return abs(area) / 2.0
    }

    private fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        return (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
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
    // Whether the filter has seen its first measurement yet. Until it has, the estimate is
    // seeded directly from the first measurement to avoid a large startup transient (e.g. a
    // 0.0 seed would drag the first output for a -60 dBm reading toward ~-20).
    private var isInitialized = false

    /**
     * Filters a new RSSI measurement to update the estimate.
     *
     * @param measurement The new noisy measurement.
     * @return The updated, smoothed estimate.
     */
    fun filter(measurement: Double): Double {
        // On the very first measurement, seed the estimate from the measurement itself and
        // return it directly. This eliminates the startup transient that a fixed 0.0 seed causes.
        if (!isInitialized) {
            currentEstimate = measurement
            lastEstimate = measurement
            isInitialized = true
            return currentEstimate
        }

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
