package com.apex.tracker.core.math

import kotlin.math.*

/**
 * Local Tangent Plane (ENU) Geodetic Projection for WGS84.
 * Converts geodetic latitude/longitude to local East-North-Up tangent plane coordinates
 * and provides inverse transformation back to geodetic coordinates.
 */
object EnuProjection {

    // WGS84 Ellipsoid constants
    const val WGS84_A = 6378137.0 // Semi-major axis in meters
    const val WGS84_F = 1.0 / 298.257223563 // Ellipsoid flattening
    val WGS84_E2 = 2.0 * WGS84_F - WGS84_F * WGS84_F // First eccentricity squared
    const val EARTH_MEAN_RADIUS = 6371000.0 // Mean radius for Haversine

    /**
     * Prime vertical radius of curvature N(phi).
     */
    fun primeVerticalRadius(latRad: Double): Double {
        if (latRad.isNaN() || latRad.isInfinite()) return WGS84_A
        val sinLat = sin(latRad)
        val denom = max(1e-12, 1.0 - WGS84_E2 * sinLat * sinLat)
        return WGS84_A / sqrt(denom)
    }

    /**
     * Meridional radius of curvature M(phi).
     */
    fun meridionalRadius(latRad: Double): Double {
        if (latRad.isNaN() || latRad.isInfinite()) return WGS84_A
        val sinLat = sin(latRad)
        val denom = max(1e-12, 1.0 - WGS84_E2 * sinLat * sinLat)
        return WGS84_A * (1.0 - WGS84_E2) / (denom * sqrt(denom))
    }

    /**
     * Forward projection: Geodetic (lat, lon in degrees) to local tangent plane (East, North in meters)
     * relative to origin (originLat, originLon in degrees).
     */
    fun forward(
        lat: Double,
        lon: Double,
        originLat: Double,
        originLon: Double
    ): Pair<Double, Double> {
        if (lat.isNaN() || lon.isNaN() || originLat.isNaN() || originLon.isNaN() ||
            lat.isInfinite() || lon.isInfinite() || originLat.isInfinite() || originLon.isInfinite()
        ) {
            return Pair(0.0, 0.0)
        }
        val safeLat = lat.coerceIn(-90.0, 90.0)
        val safeOriginLat = originLat.coerceIn(-90.0, 90.0)

        val phi0 = Math.toRadians(safeOriginLat)
        val phi = Math.toRadians(safeLat)
        val lambda0 = Math.toRadians(originLon)
        val lambda = Math.toRadians(lon)

        val nPhi0 = primeVerticalRadius(phi0)
        val mPhi0 = meridionalRadius(phi0)

        var deltaLambda = lambda - lambda0
        while (deltaLambda > PI) deltaLambda -= 2.0 * PI
        while (deltaLambda < -PI) deltaLambda += 2.0 * PI

        val pe = deltaLambda * nPhi0 * cos(phi0)
        val pn = (phi - phi0) * mPhi0

        return Pair(
            if (pe.isNaN() || pe.isInfinite()) 0.0 else pe,
            if (pn.isNaN() || pn.isInfinite()) 0.0 else pn
        )
    }

    /**
     * Inverse projection: Local tangent plane (East, North in meters) back to geodetic
     * (lat, lon in degrees) relative to origin (originLat, originLon in degrees).
     */
    fun inverse(
        pe: Double,
        pn: Double,
        originLat: Double,
        originLon: Double
    ): Pair<Double, Double> {
        val safeOriginLat = if (originLat.isNaN() || originLat.isInfinite()) 0.0 else originLat.coerceIn(-90.0, 90.0)
        val safeOriginLon = if (originLon.isNaN() || originLon.isInfinite()) 0.0 else originLon
        if (pe.isNaN() || pn.isNaN() || pe.isInfinite() || pn.isInfinite()) {
            return Pair(safeOriginLat, safeOriginLon)
        }

        val phi0 = Math.toRadians(safeOriginLat)
        val lambda0 = Math.toRadians(safeOriginLon)

        val nPhi0 = primeVerticalRadius(phi0)
        val mPhi0 = meridionalRadius(phi0)

        val cosPhi0 = cos(phi0)
        val safeCosPhi0 = if (cosPhi0 < 1e-12) 1e-12 else cosPhi0

        val phi = phi0 + pn / mPhi0
        val lambda = lambda0 + pe / (nPhi0 * safeCosPhi0)

        var degLon = Math.toDegrees(lambda)
        if (degLon.isNaN() || degLon.isInfinite()) {
            degLon = safeOriginLon
        } else {
            while (degLon > 180.0) degLon -= 360.0
            while (degLon <= -180.0) degLon += 360.0
        }

        val degLat = Math.toDegrees(phi).coerceIn(-90.0, 90.0)
        val safeDegLat = if (degLat.isNaN() || degLat.isInfinite()) safeOriginLat else degLat
        return Pair(safeDegLat, degLon)
    }

    /**
     * Great circle distance via Haversine formula in meters.
     */
    fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return Haversine.distance(lat1, lon1, lat2, lon2)
    }

    /**
     * High-precision WGS-84 Ellipsoidal Geodesic distance (Andoyer-Lambert formulation).
     * Calculates geodesic distance on the WGS-84 oblate ellipsoid in meters.
     */
    fun ellipsoidalDistance(
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

        val safeLat1 = lat1.coerceIn(-90.0, 90.0)
        val safeLat2 = lat2.coerceIn(-90.0, 90.0)
        if ((safeLat1 == 90.0 && safeLat2 == 90.0) || (safeLat1 == -90.0 && safeLat2 == -90.0)) {
            return 0.0
        }

        val phi1 = Math.toRadians(safeLat1)
        val phi2 = Math.toRadians(safeLat2)
        var dLam = Math.toRadians(lon2 - lon1)
        while (dLam > PI) dLam -= 2.0 * PI
        while (dLam < -PI) dLam += 2.0 * PI

        // Reduced latitudes: tan(beta) = (1 - f) * tan(phi)
        val beta1 = atan((1.0 - WGS84_F) * tan(phi1))
        val beta2 = atan((1.0 - WGS84_F) * tan(phi2))

        val sinB1 = sin(beta1)
        val cosB1 = cos(beta1)
        val sinB2 = sin(beta2)
        val cosB2 = cos(beta2)

        val cosSigma = (sinB1 * sinB2 + cosB1 * cosB2 * cos(dLam)).coerceIn(-1.0, 1.0)
        val sigma = acos(cosSigma)
        if (sigma < 1e-15) return 0.0

        val sinSigma = sin(sigma)
        val p = (beta1 + beta2) / 2.0
        val q = (beta2 - beta1) / 2.0

        val sinP = sin(p)
        val cosP = cos(p)
        val sinQ = sin(q)
        val cosQ = cos(q)

        val sinHalfSigma = sin(sigma / 2.0)
        val cosHalfSigma = cos(sigma / 2.0)

        val termX = if (cosHalfSigma > 1e-12) {
            (sigma - sinSigma) * (sinP * sinP * cosQ * cosQ) / (cosHalfSigma * cosHalfSigma)
        } else {
            0.0
        }

        val termY = if (sinHalfSigma > 1e-12) {
            (sigma + sinSigma) * (cosP * cosP * sinQ * sinQ) / (sinHalfSigma * sinHalfSigma)
        } else {
            val ratio = if (sigma > 1e-15) (beta2 - beta1) / sigma else 0.0
            2.0 * sigma * cosP * cosP * ratio * ratio
        }

        val dist = WGS84_A * (sigma - 0.5 * WGS84_F * (termX + termY))
        return if (dist.isNaN() || dist < 0.0 || dist.isInfinite()) 0.0 else dist
    }
}
