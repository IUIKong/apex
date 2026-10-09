package com.apex.tracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
object ApexDimens {
    val RadiusCardGiant: Dp = 18.dp
    val RadiusCardStandard: Dp = 14.dp
    val RadiusCanvasMap: Dp = 16.dp
    val RadiusButtonTactical: Dp = 16.dp
    val RadiusPillFull: Dp = 30.dp
    val RadiusModal: Dp = 20.dp

    val PaddingScreenHorizontal: Dp = 16.dp
    val PaddingScreenVertical: Dp = 16.dp
    val SpacingCards: Dp = 14.dp
    val SpacingSmall: Dp = 6.dp
    val SpacingMedium: Dp = 14.dp
    val SpacingLarge: Dp = 20.dp

    val HeightLiveMapCanvas: Dp = 340.dp
    val HeightElevationStrip: Dp = 85.dp
    val HeightPaceCard: Dp = 130.dp
    val HeightTacticalButton: Dp = 56.dp
    val HeightLockSlider: Dp = 56.dp
}

val LocalApexDimens = staticCompositionLocalOf { ApexDimens }
