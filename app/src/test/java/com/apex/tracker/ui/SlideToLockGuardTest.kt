package com.apex.tracker.ui

import androidx.compose.ui.unit.dp
import com.apex.tracker.ui.components.SlideToLockController
import com.apex.tracker.ui.theme.ApexDimens
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SlideToLockGuardTest {

    @Test
    fun testActionAllowedWhenUnlocked() {
        val isLocked = false
        val allowed = SlideToLockController.isActionAllowed(isLocked)
        assertThat(allowed).isTrue()
    }

    @Test
    fun testActionBlockedWhenLocked() {
        val isLocked = true
        val allowed = SlideToLockController.isActionAllowed(isLocked)
        assertThat(allowed).isFalse()
    }

    @Test
    fun testLockStateTransitions() {
        var lockedState = false
        assertThat(SlideToLockController.isActionAllowed(lockedState)).isTrue()

        // User slides to lock
        lockedState = true
        assertThat(SlideToLockController.isActionAllowed(lockedState)).isFalse()

        // User attempts pause/resume/finish actions while locked - should all be denied
        val attempts = listOf("PAUSE", "RESUME", "FINISH", "RESET")
        for (action in attempts) {
            val canExecute = SlideToLockController.isActionAllowed(lockedState)
            assertThat(canExecute).isFalse()
        }

        // User slides to unlock
        lockedState = false
        assertThat(SlideToLockController.isActionAllowed(lockedState)).isTrue()
    }
}
