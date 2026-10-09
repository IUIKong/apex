package com.apex.tracker.ui

import com.apex.tracker.ui.state.DeltaCategory
import com.apex.tracker.ui.state.UiFormatters
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitDeltaFormattingTest {

    @Test
    fun testDeadbandWithinPlusMinusTwoSecondsIsEven() {
        val zero = UiFormatters.formatPaceDelta(0.0)
        assertThat(zero.category).isEqualTo(DeltaCategory.EVEN)
        assertThat(zero.formattedText).isEqualTo("● EVEN")

        val plusOne = UiFormatters.formatPaceDelta(1.5)
        assertThat(plusOne.category).isEqualTo(DeltaCategory.EVEN)
        assertThat(plusOne.formattedText).isEqualTo("● EVEN")

        val minusOne = UiFormatters.formatPaceDelta(-1.8)
        assertThat(minusOne.category).isEqualTo(DeltaCategory.EVEN)
        assertThat(minusOne.formattedText).isEqualTo("● EVEN")

        val exactBoundPos = UiFormatters.formatPaceDelta(2.0)
        assertThat(exactBoundPos.category).isEqualTo(DeltaCategory.EVEN)

        val exactBoundNeg = UiFormatters.formatPaceDelta(-2.0)
        assertThat(exactBoundNeg.category).isEqualTo(DeltaCategory.EVEN)
    }

    @Test
    fun testAheadFasterThanAverageDelta() {
        val delta15 = UiFormatters.formatPaceDelta(-15.0)
        assertThat(delta15.category).isEqualTo(DeltaCategory.AHEAD)
        assertThat(delta15.formattedText).isEqualTo("▲ -0:15")

        val delta18 = UiFormatters.formatPaceDelta(-18.4)
        assertThat(delta18.category).isEqualTo(DeltaCategory.AHEAD)
        assertThat(delta18.formattedText).isEqualTo("▲ -0:18")

        // Over 1 minute ahead
        val delta75 = UiFormatters.formatPaceDelta(-75.0)
        assertThat(delta75.category).isEqualTo(DeltaCategory.AHEAD)
        assertThat(delta75.formattedText).isEqualTo("▲ -1:15")
    }

    @Test
    fun testBehindSlowerThanAverageDelta() {
        val delta22 = UiFormatters.formatPaceDelta(22.0)
        assertThat(delta22.category).isEqualTo(DeltaCategory.BEHIND)
        assertThat(delta22.formattedText).isEqualTo("▼ +0:22")

        val delta5 = UiFormatters.formatPaceDelta(5.2)
        assertThat(delta5.category).isEqualTo(DeltaCategory.BEHIND)
        assertThat(delta5.formattedText).isEqualTo("▼ +0:05")

        // Over 2 minutes behind
        val delta125 = UiFormatters.formatPaceDelta(125.0)
        assertThat(delta125.category).isEqualTo(DeltaCategory.BEHIND)
        assertThat(delta125.formattedText).isEqualTo("▼ +2:05")
    }

    @Test
    fun testNanAndInfinitySafeFallback() {
        val nan = UiFormatters.formatPaceDelta(Double.NaN)
        assertThat(nan.category).isEqualTo(DeltaCategory.EVEN)
        assertThat(nan.formattedText).isEqualTo("● EVEN")

        val inf = UiFormatters.formatPaceDelta(Double.POSITIVE_INFINITY)
        assertThat(inf.category).isEqualTo(DeltaCategory.EVEN)
        assertThat(inf.formattedText).isEqualTo("● EVEN")
    }
}
