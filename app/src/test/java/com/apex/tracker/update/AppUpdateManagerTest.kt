package com.apex.tracker.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun isNewerVersion_greaterPatch_returnsTrue() {
        assertThat(AppUpdateManager.isNewerVersion("v1.0.1", "1.0.0")).isTrue()
        assertThat(AppUpdateManager.isNewerVersion("1.0.1", "1.0.0")).isTrue()
    }

    @Test
    fun isNewerVersion_greaterMinor_returnsTrue() {
        assertThat(AppUpdateManager.isNewerVersion("v1.1.0", "1.0.9")).isTrue()
        assertThat(AppUpdateManager.isNewerVersion("1.2.0", "1.1.5")).isTrue()
    }

    @Test
    fun isNewerVersion_greaterMajor_returnsTrue() {
        assertThat(AppUpdateManager.isNewerVersion("v2.0.0", "1.9.9")).isTrue()
        assertThat(AppUpdateManager.isNewerVersion("3.0.0", "2.9.9")).isTrue()
    }

    @Test
    fun isNewerVersion_sameVersion_returnsFalse() {
        assertThat(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.0")).isFalse()
        assertThat(AppUpdateManager.isNewerVersion("1.0.0", "1.0.0")).isFalse()
        assertThat(AppUpdateManager.isNewerVersion("v1.2.3", "v1.2.3")).isFalse()
    }

    @Test
    fun isNewerVersion_lowerVersion_returnsFalse() {
        assertThat(AppUpdateManager.isNewerVersion("v0.9.9", "1.0.0")).isFalse()
        assertThat(AppUpdateManager.isNewerVersion("1.0.0", "1.0.1")).isFalse()
        assertThat(AppUpdateManager.isNewerVersion("1.1.0", "1.2.0")).isFalse()
    }

    @Test
    fun isNewerVersion_multiPartSemver_handlesGracefully() {
        assertThat(AppUpdateManager.isNewerVersion("v1.0.1.1", "1.0.1")).isTrue()
        assertThat(AppUpdateManager.isNewerVersion("1.0.1", "1.0.1.1")).isFalse()
    }

    @Test
    fun isNewerVersion_blankStrings_returnsFalse() {
        assertThat(AppUpdateManager.isNewerVersion("", "1.0.0")).isFalse()
        assertThat(AppUpdateManager.isNewerVersion("   ", "1.0.0")).isFalse()
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
}

