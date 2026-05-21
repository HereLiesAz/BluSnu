package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.geolocation.Location
import kotlin.math.abs
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
 * A simple Kalman filter for smoothing RSSI values.
 *
 * @property processNoise The process noise, which represents the uncertainty in the model.
 * @property measurementNoise The measurement noise, which represents the uncertainty in the measurement.
 */
class KalmanFilter(
    private val processNoise: Double = 0.125,
    private val measurementNoise: Double = 4.0
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
