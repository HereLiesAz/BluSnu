package com.hereliesaz.blusnu.utils

import com.hereliesaz.blusnu.data.Location
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class Observation(
    val x: Double,
    val y: Double,
    val distance: Double,
    val weight: Double
)

object Trilateration {

    /**
     * Estimates a device location using trilateration from 3 reference points with distances.
     * Uses local tangent plane approximation (accurate for BT range < 1km).
     */
    fun calculate(p1: Location, r1: Double, p2: Location, r2: Double, p3: Location, r3: Double): Location? {
        val (x1, y1) = Pair(0.0, 0.0)
        val (x2, y2) = toLocalMeters(p1, p2)
        val (x3, y3) = toLocalMeters(p1, p3)

        val a = -2 * x1 + 2 * x2
        val b = -2 * y1 + 2 * y2
        val c = r1 * r1 - r2 * r2 - x1 * x1 + x2 * x2 - y1 * y1 + y2 * y2
        val d = -2 * x2 + 2 * x3
        val e = -2 * y2 + 2 * y3
        val f = r2 * r2 - r3 * r3 - x2 * x2 + x3 * x3 - y2 * y2 + y3 * y3

        val denominator = a * e - b * d
        if (abs(denominator) < 1e-10) {
            return null
        }

        val x = (c * e - f * b) / denominator
        val y = (a * f - d * c) / denominator

        return fromLocalMeters(p1, x, y)
    }

    /**
     * Weighted least-squares estimation using Gauss-Newton iteration.
     * Uses all observations (not just 3) for a more robust estimate.
     *
     * @param observations List of (x, y, distance, weight) in local meter coordinates.
     * @param maxIterations Maximum Gauss-Newton iterations.
     * @param tolerance Convergence threshold in meters.
     * @return Estimated (x, y) in local meters, or null if cannot converge.
     */
    fun weightedLeastSquares(
        observations: List<Observation>,
        maxIterations: Int = 50,
        tolerance: Double = 0.01
    ): Pair<Double, Double>? {
        if (observations.size < 3) return null

        // Initial guess: weighted centroid
        var totalWeight = 0.0
        var cx = 0.0
        var cy = 0.0
        for (obs in observations) {
            cx += obs.x * obs.weight
            cy += obs.y * obs.weight
            totalWeight += obs.weight
        }
        if (totalWeight == 0.0) return null
        var ex = cx / totalWeight
        var ey = cy / totalWeight

        for (iter in 0 until maxIterations) {
            // Build J^T W J and J^T W r
            var jtWj00 = 0.0; var jtWj01 = 0.0; var jtWj11 = 0.0
            var jtWr0 = 0.0; var jtWr1 = 0.0

            for (obs in observations) {
                val dx = ex - obs.x
                val dy = ey - obs.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 1e-6) continue

                // Jacobian row: [dx/dist, dy/dist]
                val j0 = dx / dist
                val j1 = dy / dist
                // Residual: predicted distance - observed distance
                val r = dist - obs.distance
                val w = obs.weight

                jtWj00 += w * j0 * j0
                jtWj01 += w * j0 * j1
                jtWj11 += w * j1 * j1
                jtWr0 += w * j0 * r
                jtWr1 += w * j1 * r
            }

            // Solve 2x2 system: [jtWj] * delta = -[jtWr]
            val det = jtWj00 * jtWj11 - jtWj01 * jtWj01
            if (abs(det) < 1e-12) return null

            val deltaX = -(jtWj11 * jtWr0 - jtWj01 * jtWr1) / det
            val deltaY = -(jtWj00 * jtWr1 - jtWj01 * jtWr0) / det

            ex += deltaX
            ey += deltaY

            if (sqrt(deltaX * deltaX + deltaY * deltaY) < tolerance) {
                return Pair(ex, ey)
            }
        }

        // Return best estimate even if not fully converged
        return Pair(ex, ey)
    }

    /**
     * Convenience: run WLS on geo-coordinate observations and return a Location.
     */
    fun weightedLeastSquaresGeo(
        origin: Location,
        geoObservations: List<Triple<Location, Double, Double>> // (position, distance, weight)
    ): Location? {
        val observations = geoObservations.map { (pos, dist, w) ->
            val (x, y) = toLocalMeters(origin, pos)
            Observation(x, y, dist, w)
        }
        val result = weightedLeastSquares(observations) ?: return null
        return fromLocalMeters(origin, result.first, result.second)
    }

    fun toLocalMeters(origin: Location, point: Location): Pair<Double, Double> {
        val latRad = Math.toRadians(origin.latitude)
        val x = (point.longitude - origin.longitude) * cos(latRad) * 111320.0
        val y = (point.latitude - origin.latitude) * 110540.0
        return Pair(x, y)
    }

    fun fromLocalMeters(origin: Location, x: Double, y: Double): Location {
        val latRad = Math.toRadians(origin.latitude)
        val lat = origin.latitude + y / 110540.0
        val lon = origin.longitude + x / (111320.0 * cos(latRad))
        return Location(lat, lon)
    }
}
