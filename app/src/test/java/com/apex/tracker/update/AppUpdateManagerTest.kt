package com.apex.tracker.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun parsePublishedAtToEpochMs_validIsoString_returnsEpochMs() {
        val iso = "2026-10-10T16:00:00Z"
        val epochMs = AppUpdateManager.parsePublishedAtToEpochMs(iso)
        assertThat(epochMs).isGreaterThan(0L)
        // 2026-10-10T16:00:00Z = 1791648000000L
        assertThat(epochMs).isEqualTo(1791648000000L)
    }

    @Test
    fun parsePublishedAtToEpochMs_invalidOrBlank_returnsZero() {
        assertThat(AppUpdateManager.parsePublishedAtToEpochMs("")).isEqualTo(0L)
        assertThat(AppUpdateManager.parsePublishedAtToEpochMs("   ")).isEqualTo(0L)
        assertThat(AppUpdateManager.parsePublishedAtToEpochMs("not-a-date")).isEqualTo(0L)
    }

    @Test
    fun isNewerRelease_newerTimestamp_returnsTrue() {
        val releaseIso = "2026-10-10T16:00:00Z" // 1791648000000L
        val olderBuildTime = 1791647000000L
        assertThat(AppUpdateManager.isNewerRelease(releaseIso, olderBuildTime)).isTrue()
    }

    @Test
    fun isNewerRelease_matchingReleaseTag_returnsFalse() {
        val releaseIso = "2026-10-10T16:00:00Z"
        val olderBuildTime = 1791647000000L
        assertThat(
            AppUpdateManager.isNewerRelease(
                publishedAtIso = releaseIso,
                currentBuildTimeMillis = olderBuildTime,
                releaseTag = "v1",
                currentReleaseTag = "v1"
            )
        ).isFalse()

        // Case insensitivity
        assertThat(
            AppUpdateManager.isNewerRelease(
                publishedAtIso = releaseIso,
                currentBuildTimeMillis = olderBuildTime,
                releaseTag = "V1",
                currentReleaseTag = "v1"
            )
        ).isFalse()

        // Different tag updates properly
        assertThat(
            AppUpdateManager.isNewerRelease(
                publishedAtIso = releaseIso,
                currentBuildTimeMillis = olderBuildTime,
                releaseTag = "v2",
                currentReleaseTag = "v1"
            )
        ).isTrue()
    }

    @Test
    fun isNewerRelease_olderOrEqualTimestamp_returnsFalse() {
        val releaseIso = "2026-10-10T16:00:00Z" // 1791648000000L
        val newerBuildTime = 1791649000000L
        val equalBuildTime = 1791648000000L
        assertThat(AppUpdateManager.isNewerRelease(releaseIso, newerBuildTime)).isFalse()
        assertThat(AppUpdateManager.isNewerRelease(releaseIso, equalBuildTime)).isFalse()
    }

    @Test
    fun isNewerRelease_releaseIdComparison_takesPrecedence() {
        val releaseIso = "2026-10-10T10:00:00Z"
        val buildTime = 1791649000000L // Build time is newer, but release ID is higher
        assertThat(
            AppUpdateManager.isNewerRelease(
                publishedAtIso = releaseIso,
                currentBuildTimeMillis = buildTime,
                releaseId = 200L,
                installedReleaseId = 100L
            )
        ).isTrue()

        assertThat(
            AppUpdateManager.isNewerRelease(
                publishedAtIso = releaseIso,
                currentBuildTimeMillis = buildTime,
                releaseId = 100L,
                installedReleaseId = 200L
            )
        ).isFalse()
    }

    @Test
    fun isNewerRelease_blankOrZeroTimestamp_returnsFalse() {
        assertThat(AppUpdateManager.isNewerRelease("", 1791648000000L)).isFalse()
        assertThat(AppUpdateManager.isNewerRelease("invalid", 1791648000000L)).isFalse()
    }

    @Test
    fun updateStatus_reset_returnsToIdle() {
        val manager = AppUpdateManager()
        assertThat(manager.status.value).isEqualTo(UpdateStatus.Idle)
        manager.resetStatus()
        assertThat(manager.status.value).isEqualTo(UpdateStatus.Idle)
    }

    @Test
    fun formatReleaseNotes_stripsHashesFromHeaders() {
        val raw = "## WHAT'S CHANGED IN V1.1\n### PERFORMANCE & AUDIO"
        val formatted = formatReleaseNotes(
            rawText = raw,
            primaryColor = androidx.compose.ui.graphics.Color.White,
            accentColor = androidx.compose.ui.graphics.Color.Cyan,
            subtleColor = androidx.compose.ui.graphics.Color.Gray
        )
        val text = formatted.text
        assertThat(text).doesNotContain("##")
        assertThat(text).doesNotContain("###")
        assertThat(text).contains("WHAT'S CHANGED IN V1.1")
        assertThat(text).contains("PERFORMANCE & AUDIO")
    }

    @Test
    fun formatReleaseNotes_formatsBulletsAndBoldAndHashes() {
        val raw = """
            ## Release v1.1
            - **Theme Toggle**: Added Settings switch
            - **Commit**: `f57eb79`
            - **SHA256**: `9B305E83470B1D5300E2086F8275D124ACD45BA788EE21EE9D1F2A5432B47387`
        """.trimIndent()

        val formatted = formatReleaseNotes(
            rawText = raw,
            primaryColor = androidx.compose.ui.graphics.Color.White,
            accentColor = androidx.compose.ui.graphics.Color.Cyan,
            subtleColor = androidx.compose.ui.graphics.Color.Gray
        )
        val text = formatted.text

        // Verify markdown symbols are cleaned
        assertThat(text).doesNotContain("##")
        assertThat(text).doesNotContain("**")
        assertThat(text).doesNotContain("`")

        // Verify bullets and contents are cleanly present
        assertThat(text).contains("• Theme Toggle: Added Settings switch")
        assertThat(text).contains("• Commit: f57eb79")
        assertThat(text).contains("• SHA256: 9B305E83470B1D5300E2086F8275D124ACD45BA788EE21EE9D1F2A5432B47387")
    }

    @Test
    fun parsePublishedAtToEpochMs_timezoneOffsetsAndVariations() {
        // UTC with Z
        val utcZ = AppUpdateManager.parsePublishedAtToEpochMs("2026-10-10T16:00:00Z")
        assertThat(utcZ).isEqualTo(1791648000000L)

        // +00:00 offset
        val utcOffset = AppUpdateManager.parsePublishedAtToEpochMs("2026-10-10T16:00:00+00:00")
        assertThat(utcOffset).isEqualTo(1791648000000L)

        // +05:30 offset (Indian Standard Time: 21:30 is 16:00 UTC)
        val ist = AppUpdateManager.parsePublishedAtToEpochMs("2026-10-10T21:30:00+05:30")
        assertThat(ist).isEqualTo(1791648000000L)

        // Space separated date
        val spaceSeparated = AppUpdateManager.parsePublishedAtToEpochMs("2026-10-10 16:00:00Z")
        assertThat(spaceSeparated).isEqualTo(1791648000000L)

        // Fractional seconds
        val fractional = AppUpdateManager.parsePublishedAtToEpochMs("2026-10-10T16:00:00.000Z")
        assertThat(fractional).isEqualTo(1791648000000L)
    }
}

