package com.hereliesaz.blusnu.utils

import com.hereliesaz.blusnu.ui.geolocation.Location
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility for calculating Trilateration.
 *
 * Trilateration is the process of determining absolute or relative locations of points
 * by measurement of distances, using the geometry of circles, spheres or triangles.
 *
 * This implementation solves for the intersection of three spheres (Earth surface approximation),
 * given three known points and three distances (derived from RSSI).
 * This allows for precise location fixing without GPS if enough reference beacons are known.
 */
object Trilateration {

    /**
     * Calculates the location of a target based on three reference points and distances.
     *
     * @param p1 Location of Observer 1.
     * @param r1 Distance from Observer 1 to Target.
     * @param p2 Location of Observer 2.
     * @param r2 Distance from Observer 2 to Target.
     * @param p3 Location of Observer 3.
     * @param r3 Distance from Observer 3 to Target.
     * @return The calculated Location of the target, or null if calculation fails (singular matrix).
     */
    fun calculate(p1: Location, r1: Double, p2: Location, r2: Double, p3: Location, r3: Double): Location? {
        // Based on standard trilateration algorithm: https://en.wikipedia.org/wiki/Trilateration
        // First, convert spherical Geo-coordinates (Lat/Lon) to 3D Cartesian (X,Y,Z approximation or 2D plane).
        // Here we use a simplified projection assuming Earth radius R = 6371km.
        val (x1, y1) = latLngToCartesian(p1)
        val (x2, y2) = latLngToCartesian(p2)
        val (x3, y3) = latLngToCartesian(p3)

        // Set up the linear equation system A*x + B*y = C
        val a = -2 * x1 + 2 * x2
        val b = -2 * y1 + 2 * y2
        val c = r1 * r1 - r2 * r2 - x1 * x1 + x2 * x2 - y1 * y1 + y2 * y2
        val d = -2 * x2 + 2 * x3
        val e = -2 * y2 + 2 * y3
        val f = r2 * r2 - r3 * r3 - x2 * x2 + x3 * x3 - y2 * y2 + y3 * y3

        // Solve for X and Y using Cramer's rule or substitution.
        val denominator = a * e - b * d
        if (denominator == 0.0) {
            // Points are collinear or calculation is unstable.
            return null
        }

        val x = (c * e - f * b) / denominator
        val y = (a * f - d * c) / denominator

        // Convert the resulting Cartesian point back to Lat/Lon.
        return cartesianToLatLng(x, y)
    }

    /**
     * Converts a Geo Location to an approximate 2D Cartesian coordinate (X, Y).
     */
    private fun latLngToCartesian(point: Location): Pair<Double, Double> {
        val lat = Math.toRadians(point.latitude)
        val lon = Math.toRadians(point.longitude)
        val x = 6371 * cos(lat) * cos(lon)
        val y = 6371 * cos(lat) * sin(lon)
        return Pair(x, y)
    }

    /**
     * Converts an approximate 2D Cartesian coordinate (X, Y) back to Geo Location.
     */
    private fun cartesianToLatLng(x: Double, y: Double): Location {
        val lon = Math.atan2(y, x)
        val lat = Math.acos(sqrt(x * x + y * y) / 6371)
        return Location(Math.toDegrees(lat), Math.toDegrees(lon))
    }
}
