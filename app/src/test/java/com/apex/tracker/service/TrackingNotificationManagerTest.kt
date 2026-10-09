package com.apex.tracker.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackingNotificationManagerTest {

    @Test
    fun testNotificationConstants() {
        assertThat(TrackingNotificationManager.CHANNEL_ID).isEqualTo("apex_tracker_recording_channel")
        assertThat(TrackingNotificationManager.CHANNEL_NAME).isEqualTo("Apex Workout Recording")
        assertThat(TrackingNotificationManager.CHANNEL_WARNING_ID).isEqualTo("apex_tracker_warning_channel")
        assertThat(TrackingNotificationManager.CHANNEL_WARNING_NAME).isEqualTo("Apex Alerts")
        assertThat(TrackingNotificationManager.NOTIFICATION_ID).isEqualTo(1001)

        assertThat(TrackingNotificationManager.ACTION_PAUSE).isEqualTo("com.apex.tracker.action.PAUSE")
        assertThat(TrackingNotificationManager.ACTION_RESUME).isEqualTo("com.apex.tracker.action.RESUME")
        assertThat(TrackingNotificationManager.ACTION_STOP).isEqualTo("com.apex.tracker.action.STOP")
    }

    @Test
    fun testDistanceFormatting() {
        assertThat(TrackingNotificationManager.formatDistance(0.0)).isEqualTo("0.00 km")
        assertThat(TrackingNotificationManager.formatDistance(500.0)).isEqualTo("0.50 km")
        assertThat(TrackingNotificationManager.formatDistance(1254.0)).isEqualTo("1.25 km")
        assertThat(TrackingNotificationManager.formatDistance(10_000.0)).isEqualTo("10.00 km")
        assertThat(TrackingNotificationManager.formatDistance(42_195.0)).isEqualTo("42.20 km")
    }

    @Test
    fun testPaceFormatting() {
        // Stationary or invalid paces return placeholder
        assertThat(TrackingNotificationManager.formatPace(0.0)).isEqualTo("--:-- /km")
        assertThat(TrackingNotificationManager.formatPace(-10.0)).isEqualTo("--:-- /km")
        assertThat(TrackingNotificationManager.formatPace(Double.NaN)).isEqualTo("--:-- /km")
        assertThat(TrackingNotificationManager.formatPace(Double.POSITIVE_INFINITY)).isEqualTo("--:-- /km")
        assertThat(TrackingNotificationManager.formatPace(4000.0)).isEqualTo("--:-- /km")

        // Valid paces
        assertThat(TrackingNotificationManager.formatPace(275.0)).isEqualTo("4:35 /km") // 4m 35s
        assertThat(TrackingNotificationManager.formatPace(300.0)).isEqualTo("5:00 /km") // 5m 00s
        assertThat(TrackingNotificationManager.formatPace(365.0)).isEqualTo("6:05 /km") // 6m 05s
        assertThat(TrackingNotificationManager.formatPace(65.0)).isEqualTo("1:05 /km")  // 1m 05s
    }

    @Test
    fun testElapsedTimeFormatting() {
        // Under 1 hour (MM:SS)
        assertThat(TrackingNotificationManager.formatElapsedTime(0L)).isEqualTo("00:00")
        assertThat(TrackingNotificationManager.formatElapsedTime(9L)).isEqualTo("00:09")
        assertThat(TrackingNotificationManager.formatElapsedTime(59L)).isEqualTo("00:59")
        assertThat(TrackingNotificationManager.formatElapsedTime(60L)).isEqualTo("01:00")
        assertThat(TrackingNotificationManager.formatElapsedTime(125L)).isEqualTo("02:05")
        assertThat(TrackingNotificationManager.formatElapsedTime(3599L)).isEqualTo("59:59")

        // 1 hour and above (H:MM:SS)
        assertThat(TrackingNotificationManager.formatElapsedTime(3600L)).isEqualTo("1:00:00")
        assertThat(TrackingNotificationManager.formatElapsedTime(3665L)).isEqualTo("1:01:05")
        assertThat(TrackingNotificationManager.formatElapsedTime(7325L)).isEqualTo("2:02:05")
        assertThat(TrackingNotificationManager.formatElapsedTime(36_000L)).isEqualTo("10:00:00")
    }
}
