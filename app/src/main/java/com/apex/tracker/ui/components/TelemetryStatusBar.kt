package com.apex.tracker.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import java.util.Locale

@Composable
fun GnssSignalBars(
    activeBars: Int,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val barShape = remember { RoundedCornerShape(1.dp) }
    val barHeights = remember { listOf(4.dp, 7.dp, 10.dp, 13.dp) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until 4) {
            val isActive = i < activeBars
            val color = if (isActive) colors.electricLime else colors.borderSubtle
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeights[i])
                    .clip(barShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun TelemetryStatusBar(
    satelliteCount: Int,
    horizontalAccuracyMeters: Float,
    motionState: String,
    activityType: String,
    batteryProfile: String,
    isDarkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    onActivityTypeChanged: ((String) -> Unit)? = null,
    onBatteryProfileChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val activeBars = when {
        satelliteCount == 0 -> 0
        horizontalAccuracyMeters in 0.01f..5.0f && satelliteCount >= 8 -> 4
        horizontalAccuracyMeters in 0.01f..10.0f && satelliteCount >= 5 -> 3
        horizontalAccuracyMeters in 0.01f..25.0f && satelliteCount >= 3 -> 2
        else -> 1
    }

    val isMoving = motionState.uppercase(Locale.US) == "MOVING"

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    val (motionColor, motionText) = when (motionState.uppercase(Locale.US)) {
        "MOVING" -> Pair(colors.electricLime, "MOVING")
        "STOPPED" -> Pair(colors.punchyCrimson, "STOPPED")
        "PAUSED" -> Pair(colors.laserAmber, "PAUSED")
        "INITIALIZING" -> Pair(colors.laserAmber, "ACQUIRING")
        else -> Pair(colors.slateMuted, "READY")
    }

    var batteryMenuExpanded by remember { mutableStateOf(false) }
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: GNSS Reception & Satellite Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GnssSignalBars(activeBars = activeBars)
                Text(
                    text = "${satelliteCount}S",
                    style = typography.TelemetryMicro.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.slateMuted,
                        fontSize = 10.sp
                    )
                )

                val accText = if (horizontalAccuracyMeters > 0f) {
                    String.format(Locale.US, "±%.1fm", horizontalAccuracyMeters)
                } else "SEARCHING"
                val accColor = if (horizontalAccuracyMeters in 0.01f..10f) colors.electricLime else colors.laserAmber

                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, pillShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = accText,
                        style = typography.TelemetryMicro.copy(
                            color = accColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Right: Motion State Pill + Power Profile Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Motion Pill
                Row(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, pillShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer {
                                this.alpha = if (isMoving) pulseAlpha else 1f
                            }
                            .clip(CircleShape)
                            .background(motionColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = motionText,
                        style = typography.TelemetryMicro.copy(
                            color = motionColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Battery Profile Pill
                Box {
                    val profileLabel = when (batteryProfile.uppercase(Locale.US)) {
                        "BALANCED" -> "BALANCED"
                        "ECO" -> "ECO"
                        else -> "MAX ACC"
                    }

                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.borderSubtle, pillShape)
                            .clickable(enabled = onBatteryProfileChanged != null) {
                                batteryMenuExpanded = true
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = profileLabel,
                            style = typography.TelemetryMicro.copy(
                                fontSize = 9.sp,
                                color = colors.slateMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    DropdownMenu(
                        expanded = batteryMenuExpanded,
                        onDismissRequest = { batteryMenuExpanded = false }
                    ) {
                        listOf("MAX_ACCURACY", "BALANCED", "ECO").forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.replace("_", " ")) },
                                onClick = {
                                    onBatteryProfileChanged?.invoke(profile)
                                    batteryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
