package com.hereliesaz.blusnu.data

import kotlin.math.*

/**
 * Utility object for performing geometric calculations for Cooperative Triangulation.
 *
 * This object contains the math logic to find the intersection points of two circles
 * on the Earth's surface, representing the possible locations of a target based on
 * distance estimates from two different observers (Tandem Mode).
 */
object CooperativeTriangulation {

    /**
     * Simple data class for Latitude/Longitude.
     */
    data class GeoLocation(val latitude: Double, val longitude: Double)

    /**
     * Calculates the intersection points of two circles on Earth surface.
     *
     * Uses a simplified planar approximation (Euclidean geometry with scaling)
     * which is sufficiently accurate for short distances (< 1km) typical of Bluetooth range.
     *
     * @param loc1 Location of observer 1.
     * @param dist1 Radius (distance to target) from observer 1 in meters.
     * @param loc2 Location of observer 2.
     * @param dist2 Radius (distance to target) from observer 2 in meters.
     * @return A list of 0, 1, or 2 GeoLocation points representing intersections.
     */
    fun calculateIntersections(
        loc1: GeoLocation, dist1: Double,
        loc2: GeoLocation, dist2: Double
    ): List<GeoLocation> {
        // Approximate meters per degree of latitude (constant approx 111km).
        val metersPerLat = 111132.0
        // Approximate meters per degree of longitude (varies by latitude).
        val metersPerLon = 111132.0 * cos(Math.toRadians(loc1.latitude))

        // Convert GeoLocations to a local Cartesian coordinate system (meters).
        // Let P1 be at (0,0).
        // Let P2 be at (x2, y2) relative to P1.
        val y2 = (loc2.latitude - loc1.latitude) * metersPerLat
        val x2 = (loc2.longitude - loc1.longitude) * metersPerLon

        // Distance between P1 and P2 centers.
        val d = sqrt(x2.pow(2) + y2.pow(2))

        // Check for circle relationships:
        if (d > dist1 + dist2) return emptyList() // Circles are separate, no intersection.
        if (d < abs(dist1 - dist2)) return emptyList() // One circle contained within other.
        if (d == 0.0) return emptyList() // Coincident centers (impossible if locs differ).

        // Calculate intersection geometry (Standard circle-circle intersection).
        // 'a' is the distance from P1 to the chord line connecting the two intersection points.
        val a = (dist1.pow(2) - dist2.pow(2) + d.pow(2)) / (2 * d)

        // 'h' is the distance from the chord line to the intersection points (height).
        val h = sqrt(max(0.0, dist1.pow(2) - a.pow(2)))

        // Point P3 (x3, y3) is the intersection of the line connecting centers and the chord line.
        val x3 = x2 * a / d
        val y3 = y2 * a / d

        // Calculate the two intersection points (x4, y4).
        val x4_1 = x3 + h * y2 / d
        val y4_1 = y3 - h * x2 / d

        val x4_2 = x3 - h * y2 / d
        val y4_2 = y3 + h * x2 / d

        // Convert back to Global Latitude/Longitude.
        fun toGeo(x: Double, y: Double): GeoLocation {
             val lat = loc1.latitude + y / metersPerLat
             val lon = loc1.longitude + x / metersPerLon
             return GeoLocation(lat, lon)
        }

        return listOf(toGeo(x4_1, y4_1), toGeo(x4_2, y4_2))
    }
}
