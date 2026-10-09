package com.apex.tracker.core.math

import kotlin.math.*

/**
 * Double-precision Haversine Geodesic Distance Engine.
 *
 * Computes great-circle distance on the WGS84 mean earth radius (6,371,000.0 m)
 * with strict numerical safeguards against floating-point cancellation for
 * high-frequency small-step updates (1Hz, delta < 1m) and antipodal coordinates.
 */
object Haversine {

    const val EARTH_MEAN_RADIUS = 6371000.0 // Mean earth radius in meters

    /**
     * Calculates the great-circle geodesic distance between two points in meters.
     *
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Geodesic distance in meters (guaranteed non-negative, finite, non-NaN)
     */
    fun distance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN() ||
            lat1.isInfinite() || lon1.isInfinite() || lat2.isInfinite() || lon2.isInfinite()
        ) {
            return 0.0
        }
        if (lat1 == lat2 && lon1 == lon2) return 0.0

        // Strict boundary clamping of latitudes to [-90.0, 90.0] to prevent cosine inversion
        val safeLat1 = lat1.coerceIn(-90.0, 90.0)
        val safeLat2 = lat2.coerceIn(-90.0, 90.0)

        // Identity check at the poles: both points at the North Pole or South Pole
        if ((safeLat1 == 90.0 && safeLat2 == 90.0) || (safeLat1 == -90.0 && safeLat2 == -90.0)) {
            return 0.0
        }

        val phi1 = Math.toRadians(safeLat1)
        val phi2 = Math.toRadians(safeLat2)
        val deltaPhi = Math.toRadians(safeLat2 - safeLat1)
        var deltaLambda = Math.toRadians(lon2 - lon1)

        // Strict wrap-around handling across antimeridian [-PI, PI]
        while (deltaLambda > PI) deltaLambda -= 2.0 * PI
        while (deltaLambda < -PI) deltaLambda += 2.0 * PI

        val sinDphi = sin(deltaPhi / 2.0)
        val sinDlam = sin(deltaLambda / 2.0)

        // a = sin^2(dPhi/2) + cos(phi1) * cos(phi2) * sin^2(dLam/2)
        val aRaw = sinDphi * sinDphi + cos(phi1) * cos(phi2) * sinDlam * sinDlam

        // Strict numerical clamping to [0.0, 1.0] to prevent cancellation-induced negative values
        // or roundoff beyond 1.0 near antipodal endpoints
        val aClamped = aRaw.coerceIn(0.0, 1.0)
        val c = 2.0 * atan2(sqrt(aClamped), sqrt(max(0.0, 1.0 - aClamped)))

        val dist = EARTH_MEAN_RADIUS * c
        return if (dist.isNaN() || dist < 0.0 || dist.isInfinite()) 0.0 else dist
    }
}
