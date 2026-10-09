package com.apex.tracker.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WakeLockManagerTest {

    @Test
    fun testWakeLockTimeoutConstantIsTwelveHours() {
        // 12 hours * 60 min * 60 sec * 1000 ms = 43,200,000 ms
        val expectedTimeoutMs = 12L * 60L * 60L * 1000L
        assertThat(WakeLockManager.WAKE_LOCK_TIMEOUT_MS).isEqualTo(expectedTimeoutMs)
        assertThat(WakeLockManager.WAKE_LOCK_TIMEOUT_MS).isEqualTo(43_200_000L)
    }
}
