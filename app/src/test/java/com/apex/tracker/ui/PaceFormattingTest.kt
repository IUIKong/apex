package com.apex.tracker.ui

import com.apex.tracker.ui.state.UiFormatters
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaceFormattingTest {

    @Test
    fun testPaceZeroClampsToDash() {
        assertThat(UiFormatters.formatPace(0.0)).isEqualTo("--:--")
    }

    @Test
    fun testPaceNegativeClampsToDash() {
        assertThat(UiFormatters.formatPace(-10.5)).isEqualTo("--:--")
    }

    @Test
    fun testPaceNanAndInfiniteClampsToDash() {
        assertThat(UiFormatters.formatPace(Double.NaN)).isEqualTo("--:--")
        assertThat(UiFormatters.formatPace(Double.POSITIVE_INFINITY)).isEqualTo("--:--")
        assertThat(UiFormatters.formatPace(Double.NEGATIVE_INFINITY)).isEqualTo("--:--")
    }

    @Test
    fun testPaceExtremelySlowClampsToDash() {
        // >= 3600 seconds (1 hour per km)
        assertThat(UiFormatters.formatPace(3600.0)).isEqualTo("--:--")
        assertThat(UiFormatters.formatPace(7200.0)).isEqualTo("--:--")
    }

    @Test
    fun testNormalPaceFormatting() {
        // 5:00 min/km = 300 sec
        assertThat(UiFormatters.formatPace(300.0)).isEqualTo("05:00")

        // 4:32 min/km = 272 sec
        assertThat(UiFormatters.formatPace(272.0)).isEqualTo("04:32")

        // 3:45 min/km = 225 sec
        assertThat(UiFormatters.formatPace(225.0)).isEqualTo("03:45")

        // Sub 1-minute: 59 sec
        assertThat(UiFormatters.formatPace(59.0)).isEqualTo("00:59")

        // Fast boundary: 1 sec
        assertThat(UiFormatters.formatPace(1.0)).isEqualTo("00:01")

        // High boundary: 3599 sec
        assertThat(UiFormatters.formatPace(3599.0)).isEqualTo("59:59")
    }

    @Test
    fun testDurationFormatting() {
        assertThat(UiFormatters.formatDuration(0L)).isEqualTo("00:00")
        assertThat(UiFormatters.formatDuration(59L)).isEqualTo("00:59")
        assertThat(UiFormatters.formatDuration(65L)).isEqualTo("01:05")
        assertThat(UiFormatters.formatDuration(1335L)).isEqualTo("22:15")
        assertThat(UiFormatters.formatDuration(3600L)).isEqualTo("01:00:00")
        assertThat(UiFormatters.formatDuration(3665L)).isEqualTo("01:01:05")
        assertThat(UiFormatters.formatDuration(-10L)).isEqualTo("00:00")
    }

    @Test
    fun testDistanceFormatting() {
        assertThat(UiFormatters.formatDistanceKm(0.0)).isEqualTo("0.00")
        assertThat(UiFormatters.formatDistanceKm(4520.0)).isEqualTo("4.52")
        assertThat(UiFormatters.formatDistanceKm(10000.0)).isEqualTo("10.00")
        assertThat(UiFormatters.formatDistanceKm(-50.0)).isEqualTo("0.00")
        assertThat(UiFormatters.formatDistanceKm(Double.NaN)).isEqualTo("0.00")
    }

    @Test
    fun testElevationGainFormatting() {
        assertThat(UiFormatters.formatElevationGain(0.0)).isEqualTo("+0 m")
        assertThat(UiFormatters.formatElevationGain(42.3)).isEqualTo("+42 m")
        assertThat(UiFormatters.formatElevationGain(124.8)).isEqualTo("+125 m")
        assertThat(UiFormatters.formatElevationGain(-5.0)).isEqualTo("+0 m")
    }
}
