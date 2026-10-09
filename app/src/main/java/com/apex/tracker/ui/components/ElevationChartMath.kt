package com.apex.tracker.ui.components

import androidx.compose.runtime.Immutable
import kotlin.math.max

@Immutable
data class ElevationBounds(
    val minAltitude: Double,
    val maxAltitude: Double,
    val span: Double
)

object ElevationChartMath {

    /**
     * Computes the min/max altitude bounds from a list of altitudes.
     * Enforces a minimum span (e.g. 2.0m) to prevent division by zero on flat terrain.
     */
    fun computeBounds(altitudes: List<Double>, minSpanMeters: Double = 2.0): ElevationBounds {
        if (altitudes.isEmpty()) {
            return ElevationBounds(minAltitude = 0.0, maxAltitude = minSpanMeters, span = minSpanMeters)
        }

        var minAlt = Double.MAX_VALUE
        var maxAlt = -Double.MAX_VALUE

        for (alt in altitudes) {
            if (alt < minAlt) minAlt = alt
            if (alt > maxAlt) maxAlt = alt
        }

        val actualSpan = maxAlt - minAlt
        val span = if (actualSpan < minSpanMeters) minSpanMeters else actualSpan
        val adjustedMax = if (actualSpan < minSpanMeters) minAlt + minSpanMeters else maxAlt

        return ElevationBounds(
            minAltitude = minAlt,
            maxAltitude = adjustedMax,
            span = span
        )
    }

    /**
     * Maps an altitude value to a Y coordinate on the canvas.
     */
    fun altitudeToY(
        altitude: Double,
        bounds: ElevationBounds,
        canvasHeight: Float,
        verticalPadding: Float = 12f
    ): Float {
        val usableHeight = max(canvasHeight - 2f * verticalPadding, 1f)
        val normalized = ((altitude - bounds.minAltitude) / bounds.span).coerceIn(0.0, 1.0)
        // Inverted: higher altitude is smaller Y (top of canvas)
        return canvasHeight - verticalPadding - (normalized * usableHeight).toFloat()
    }

    /**
     * Calculates grade percentage from recent track points or altitude delta over distance delta.
     */
    fun computeGradePercent(deltaElevationMeters: Double, deltaDistanceMeters: Double): Double {
        if (deltaDistanceMeters <= 1.0) return 0.0
        val grade = (deltaElevationMeters / deltaDistanceMeters) * 100.0
        return grade.coerceIn(-40.0, 40.0)
    }
}
