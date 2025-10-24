package com.hereliesaz.blusnu.utils

import com.hereliesaz.blusnu.ui.geolocation.Location
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Trilateration {
    fun calculate(p1: Location, r1: Double, p2: Location, r2: Double, p3: Location, r3: Double): Location? {
        // Based on https://en.wikipedia.org/wiki/Trilateration
        val (x1, y1) = latLngToCartesian(p1)
        val (x2, y2) = latLngToCartesian(p2)
        val (x3, y3) = latLngToCartesian(p3)

        val a = -2 * x1 + 2 * x2
        val b = -2 * y1 + 2 * y2
        val c = r1 * r1 - r2 * r2 - x1 * x1 + x2 * x2 - y1 * y1 + y2 * y2
        val d = -2 * x2 + 2 * x3
        val e = -2 * y2 + 2 * y3
        val f = r2 * r2 - r3 * r3 - x2 * x2 + x3 * x3 - y2 * y2 + y3 * y3

        val denominator = a * e - b * d
        if (denominator == 0.0) {
            return null
        }

        val x = (c * e - f * b) / denominator
        val y = (a * f - d * c) / denominator

        return cartesianToLatLng(x, y)
    }

    private fun latLngToCartesian(point: Location): Pair<Double, Double> {
        val lat = Math.toRadians(point.latitude)
        val lon = Math.toRadians(point.longitude)
        val x = 6371 * cos(lat) * cos(lon)
        val y = 6371 * cos(lat) * sin(lon)
        return Pair(x, y)
    }

    private fun cartesianToLatLng(x: Double, y: Double): Location {
        val lon = Math.atan2(y, x)
        val lat = Math.acos(sqrt(x * x + y * y) / 6371)
        return Location(Math.toDegrees(lat), Math.toDegrees(lon))
    }
}
