package com.hereliesaz.blusnu.data

import kotlin.math.pow

/**
 * A module responsible for geolocation-related calculations.
 */
class GeolocationModule {

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
}
