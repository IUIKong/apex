package com.apex.tracker.ui

import com.apex.tracker.ui.components.ElevationChartMath
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ElevationChartMathTest {

    @Test
    fun testComputeBoundsFlatTerrainEnforcesMinimumSpan() {
        val flatAltitudes = listOf(150.0, 150.0, 150.0)
        val bounds = ElevationChartMath.computeBounds(flatAltitudes, minSpanMeters = 2.0)

        assertThat(bounds.minAltitude).isEqualTo(150.0)
        assertThat(bounds.maxAltitude).isEqualTo(152.0)
        assertThat(bounds.span).isEqualTo(2.0)
    }

    @Test
    fun testComputeBoundsNormalSpan() {
        val altitudes = listOf(120.0, 185.0, 95.0, 140.0)
        val bounds = ElevationChartMath.computeBounds(altitudes, minSpanMeters = 2.0)

        assertThat(bounds.minAltitude).isEqualTo(95.0)
        assertThat(bounds.maxAltitude).isEqualTo(185.0)
        assertThat(bounds.span).isEqualTo(90.0)
    }

    @Test
    fun testComputeBoundsEmptyListFallback() {
        val bounds = ElevationChartMath.computeBounds(emptyList(), minSpanMeters = 3.0)
        assertThat(bounds.span).isEqualTo(3.0)
    }

    @Test
    fun testAltitudeToYMapping() {
        val altitudes = listOf(100.0, 200.0)
        val bounds = ElevationChartMath.computeBounds(altitudes, minSpanMeters = 2.0)
        val height = 100f
        val vPadding = 10f

        // Min altitude -> bottom of usable area
        val yMin = ElevationChartMath.altitudeToY(100.0, bounds, height, vPadding)
        assertThat(yMin).isWithin(0.01f).of(90f)

        // Max altitude -> top of usable area
        val yMax = ElevationChartMath.altitudeToY(200.0, bounds, height, vPadding)
        assertThat(yMax).isWithin(0.01f).of(10f)

        // Mid altitude (150.0) -> center
        val yMid = ElevationChartMath.altitudeToY(150.0, bounds, height, vPadding)
        assertThat(yMid).isWithin(0.01f).of(50f)
    }

    @Test
    fun testGradePercentCalculation() {
        // 10m gain over 100m = +10.0%
        val grade10 = ElevationChartMath.computeGradePercent(10.0, 100.0)
        assertThat(grade10).isWithin(0.01).of(10.0)

        // -5m drop over 100m = -5.0%
        val gradeDown = ElevationChartMath.computeGradePercent(-5.0, 100.0)
        assertThat(gradeDown).isWithin(0.01).of(-5.0)

        // Flat or zero distance
        assertThat(ElevationChartMath.computeGradePercent(5.0, 0.5)).isEqualTo(0.0)

        // Extreme clamp (+80m over 50m) -> clamped to +40%
        val steep = ElevationChartMath.computeGradePercent(80.0, 50.0)
        assertThat(steep).isEqualTo(40.0)
    }
}
