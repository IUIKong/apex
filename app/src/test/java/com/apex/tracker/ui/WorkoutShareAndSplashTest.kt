package com.apex.tracker.ui

import com.apex.tracker.ui.state.WorkoutSummaryUiState
import com.apex.tracker.ui.summary.WorkoutShareHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutShareAndSplashTest {

    @Test
    fun testShareCardDimensions() {
        assertThat(WorkoutShareHelper.CARD_WIDTH).isEqualTo(1080)
        assertThat(WorkoutShareHelper.CARD_HEIGHT).isEqualTo(1350)
        // 4:5 aspect ratio standard for social stories and messaging previews
        assertThat(WorkoutShareHelper.CARD_WIDTH * 5).isEqualTo(WorkoutShareHelper.CARD_HEIGHT * 4)
    }

    @Test
    fun testShareFileNameSanitization() {
        val safeName = WorkoutShareHelper.getShareFileName("activity:2026-10-09/morning#run")
        assertThat(safeName).startsWith("apex_")
        assertThat(safeName).endsWith(".png")
        assertThat(safeName).doesNotContain(":")
        assertThat(safeName).doesNotContain("/")
        assertThat(safeName).doesNotContain("#")
    }

    @Test
    fun testShareMessageFormatting() {
        val summary = WorkoutSummaryUiState(
            totalDistanceMeters = 5420.0,
            movingTimeSeconds = 1800L
        )
        val message = WorkoutShareHelper.getShareMessage(summary)
        assertThat(message).contains("5.42 km")
        assertThat(message).contains("Apex")
    }
}
