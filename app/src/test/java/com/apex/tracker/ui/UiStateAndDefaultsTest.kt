package com.apex.tracker.ui

import com.apex.tracker.ui.state.DiagnosticsUiState
import com.apex.tracker.ui.state.LiveHudUiState
import com.apex.tracker.ui.state.SplitDto
import com.apex.tracker.ui.state.TrackPointDto
import com.apex.tracker.ui.state.WorkoutSummaryUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiStateAndDefaultsTest {

    @Test
    fun testLiveHudUiStateDefaultValues() {
        val state = LiveHudUiState()

        assertThat(state.isRecording).isFalse()
        assertThat(state.isPaused).isFalse()
        assertThat(state.isControlsLocked).isFalse()
        assertThat(state.isDarkTheme).isFalse()
        assertThat(state.motionState).isEqualTo("READY")
        assertThat(state.activityType).isEqualTo("RUNNING")
        assertThat(state.batteryProfile).isEqualTo("MAX_ACCURACY")
        assertThat(state.currentPaceSecPerKm).isEqualTo(0.0)
        assertThat(state.averagePaceSecPerKm).isEqualTo(0.0)
        assertThat(state.paceDeltaSec).isEqualTo(0.0)
        assertThat(state.acceptedDistanceMeters).isEqualTo(0.0)
        assertThat(state.rawDistanceMeters).isEqualTo(0.0)
        assertThat(state.filteredDistanceMeters).isEqualTo(0.0)
        assertThat(state.movingTimeSeconds).isEqualTo(0L)
        assertThat(state.elapsedTimeSeconds).isEqualTo(0L)
        assertThat(state.elevationGainMeters).isEqualTo(0.0)
        assertThat(state.trackPoints).isEmpty()
        assertThat(state.splits).isEmpty()
        assertThat(state.hasInterruptedSession).isFalse()
        assertThat(state.isLocationServicesEnabled).isTrue()
        assertThat(state.isLocationPermissionGranted).isTrue()
        assertThat(state.showLocationDisabledPrompt).isFalse()
    }

    @Test
    fun testWorkoutSummaryUiStateDefaultValues() {
        val summary = WorkoutSummaryUiState()

        assertThat(summary.activityId).isEmpty()
        assertThat(summary.title).isEqualTo("WORKOUT SUMMARY")
        assertThat(summary.activityType).isEqualTo("RUNNING")
        assertThat(summary.totalDistanceMeters).isEqualTo(0.0)
        assertThat(summary.movingTimeSeconds).isEqualTo(0L)
        assertThat(summary.avgPaceSecPerKm).isEqualTo(0.0)
        assertThat(summary.elevationGainMeters).isEqualTo(0.0)
        assertThat(summary.trackPoints).isEmpty()
        assertThat(summary.splits).isEmpty()
        assertThat(summary.status).isEqualTo("COMPLETED")
    }

    @Test
    fun testDiagnosticsUiStateDefaultValues() {
        val diag = DiagnosticsUiState()

        assertThat(diag.acceptedDistanceMeters).isEqualTo(0.0)
        assertThat(diag.filteredDistanceMeters).isEqualTo(0.0)
        assertThat(diag.rawDistanceMeters).isEqualTo(0.0)
        assertThat(diag.noiseSuppressedMeters).isEqualTo(0.0)
        assertThat(diag.positionVarEast).isEqualTo(0.0)
        assertThat(diag.positionVarNorth).isEqualTo(0.0)
        assertThat(diag.velocityVarEast).isEqualTo(0.0)
        assertThat(diag.velocityVarNorth).isEqualTo(0.0)
        assertThat(diag.lastMahalanobisD2).isEqualTo(0.0)
        assertThat(diag.outliersRejectedCount).isEqualTo(0)
        assertThat(diag.isDeadbandActive).isFalse()
        assertThat(diag.lockQuality).isEqualTo("SEARCHING (NO FIX)")
        assertThat(diag.gpsSatellites).isEqualTo(0)
        assertThat(diag.glonassSatellites).isEqualTo(0)
        assertThat(diag.galileoSatellites).isEqualTo(0)
        assertThat(diag.beidouSatellites).isEqualTo(0)
        assertThat(diag.hasBarometer).isFalse()
        assertThat(diag.atmosphericPressureHpa).isEqualTo(0.0)
        assertThat(diag.accelDampFactor).isEqualTo(0.985)
        assertThat(diag.gnssFrequencyHz).isEqualTo(0)
        assertThat(diag.imuFrequencyHz).isEqualTo(0)
        assertThat(diag.batteryLevelPercent).isEqualTo(-1)
        assertThat(diag.hasAccelerometer).isFalse()
        assertThat(diag.hasGyroscope).isFalse()
        assertThat(diag.hdop).isEqualTo(0f)
        assertThat(diag.vdop).isEqualTo(0f)
    }

    @Test
    fun testTrackPointDtoDefaults() {
        val pt = TrackPointDto(
            latitude = 37.7749,
            longitude = -122.4194
        )

        assertThat(pt.latitude).isEqualTo(37.7749)
        assertThat(pt.longitude).isEqualTo(-122.4194)
        assertThat(pt.altitude).isEqualTo(0.0)
        assertThat(pt.speedMps).isEqualTo(0f)
        assertThat(pt.bearing).isEqualTo(0f)
        assertThat(pt.rawLatitude).isEqualTo(37.7749)
        assertThat(pt.rawLongitude).isEqualTo(-122.4194)
        assertThat(pt.isOutlier).isFalse()
    }

    @Test
    fun testSplitDtoDefaults() {
        val split = SplitDto(
            splitIndex = 1
        )

        assertThat(split.splitIndex).isEqualTo(1)
        assertThat(split.distanceMeters).isEqualTo(1000.0)
        assertThat(split.durationSeconds).isEqualTo(0L)
        assertThat(split.averagePaceSecondsPerKm).isEqualTo(0.0)
        assertThat(split.elevationGainMeters).isEqualTo(0.0)
        assertThat(split.deltaSecondsVsAvg).isEqualTo(0.0)
    }
}
