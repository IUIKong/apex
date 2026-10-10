package com.apex.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.state.DeltaCategory
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme

@Composable
fun GlanceablePaceCard(
    currentPaceSecPerKm: Double,
    paceDeltaSec: Double,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardGiant) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    val formattedPace = remember(currentPaceSecPerKm) { UiFormatters.formatPace(currentPaceSecPerKm) }
    val deltaDisplay = remember(paceDeltaSec) { UiFormatters.formatPaceDelta(paceDeltaSec) }

    val deltaColor = when (deltaDisplay.category) {
        DeltaCategory.AHEAD -> colors.electricLime
        DeltaCategory.BEHIND -> colors.laserAmber
        DeltaCategory.EVEN -> colors.slateSubtle
    }

    val deltaBg = when (deltaDisplay.category) {
        DeltaCategory.AHEAD -> colors.electricLimeDim
        DeltaCategory.BEHIND -> colors.laserAmberDim
        DeltaCategory.EVEN -> colors.surfaceHigh
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceElevated)
            .border(
                width = 1.dp,
                color = colors.borderSubtle,
                shape = cardShape
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Category label & Delta Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp)
                            .background(colors.electricCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PRIMARY TELEMETRY • CURRENT PACE",
                        style = typography.LabelUppercase
                    )
                }

                // Delta pill
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(deltaBg)
                        .border(1.dp, deltaColor.copy(alpha = 0.5f), pillShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = deltaDisplay.formattedText,
                        style = typography.TelemetryMicro.copy(
                            color = deltaColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Giant Monospace Numerals & Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedNumeralTicker(
                    text = formattedPace,
                    style = typography.MetricGiant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "/km",
                    style = typography.LabelUppercase.copy(
                        fontSize = 16.sp,
                        color = colors.slateSubtle
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}
