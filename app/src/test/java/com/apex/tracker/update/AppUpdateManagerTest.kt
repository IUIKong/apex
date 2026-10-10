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
}
