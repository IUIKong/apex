package com.apex.tracker.service

import com.apex.tracker.core.model.ActivityType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackingForegroundServiceTest {

    @Test
    fun testServiceActionConstants() {
        assertThat(TrackingForegroundService.ACTION_START).isEqualTo("com.apex.tracker.action.START")
        assertThat(TrackingForegroundService.ACTION_PAUSE).isEqualTo("com.apex.tracker.action.PAUSE")
        assertThat(TrackingForegroundService.ACTION_RESUME).isEqualTo("com.apex.tracker.action.RESUME")
        assertThat(TrackingForegroundService.ACTION_STOP).isEqualTo("com.apex.tracker.action.STOP")
        assertThat(TrackingForegroundService.EXTRA_ACTIVITY_TYPE).isEqualTo("extra_activity_type")
    }

    @Test
    fun testInitialServiceStateFlows() {
        assertThat(TrackingForegroundService.fusedState.value).isNull()
        assertThat(TrackingForegroundService.isRecording.value).isFalse()
        assertThat(TrackingForegroundService.isPaused.value).isFalse()

        val hw = TrackingForegroundService.hardwareDiagnostics.value
        assertThat(hw.satelliteCount).isEqualTo(0)
        assertThat(hw.gpsSatellites).isEqualTo(0)
        assertThat(hw.lockQuality).isEqualTo("SEARCHING (NO FIX)")
        assertThat(hw.hasBarometer).isFalse()
        assertThat(hw.hdop).isEqualTo(0f)
        assertThat(hw.vdop).isEqualTo(0f)
    }

    @Test
    fun testActivityTypeEnumParsing() {
        val types = ActivityType.entries
        assertThat(types).contains(ActivityType.RUNNING)
        assertThat(types).contains(ActivityType.CYCLING)
        assertThat(types).contains(ActivityType.WALKING)
        assertThat(types).contains(ActivityType.HIKING)

        val running = ActivityType.valueOf("RUNNING")
        assertThat(running).isEqualTo(ActivityType.RUNNING)
    }
}
