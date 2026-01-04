package com.hereliesaz.blusnu.data

import kotlin.math.*

object CooperativeTriangulation {

    data class GeoLocation(val latitude: Double, val longitude: Double)

    /**
     * Calculates the intersection points of two circles on Earth surface.
     * Uses a simplified planar approximation for short distances (< 1km).
     */
    fun calculateIntersections(
        loc1: GeoLocation, dist1: Double,
        loc2: GeoLocation, dist2: Double
    ): List<GeoLocation> {
        // Approximate meters per degree
        val metersPerLat = 111132.0
        val metersPerLon = 111132.0 * cos(Math.toRadians(loc1.latitude))

        // P1 is at (0,0)
        // P2 relative to P1
        val y2 = (loc2.latitude - loc1.latitude) * metersPerLat
        val x2 = (loc2.longitude - loc1.longitude) * metersPerLon

        val d = sqrt(x2.pow(2) + y2.pow(2))

        // Checks for containment or separation
        if (d > dist1 + dist2) return emptyList() // Separate
        if (d < abs(dist1 - dist2)) return emptyList() // Contained
        if (d == 0.0) return emptyList() // Coincident

        val a = (dist1.pow(2) - dist2.pow(2) + d.pow(2)) / (2 * d)
        val h = sqrt(max(0.0, dist1.pow(2) - a.pow(2)))

        val x3 = x2 * a / d
        val y3 = y2 * a / d

        val x4_1 = x3 + h * y2 / d
        val y4_1 = y3 - h * x2 / d

        val x4_2 = x3 - h * y2 / d
        val y4_2 = y3 + h * x2 / d

        // Convert back to Lat/Lon
        fun toGeo(x: Double, y: Double): GeoLocation {
             val lat = loc1.latitude + y / metersPerLat
             val lon = loc1.longitude + x / metersPerLon
             return GeoLocation(lat, lon)
        }

        return listOf(toGeo(x4_1, y4_1), toGeo(x4_2, y4_2))
    }
}
