package com.apex.tracker.ui.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.state.DeltaCategory
import com.apex.tracker.ui.state.SplitDto
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import kotlin.math.roundToInt

@Composable
fun SplitCard(
    split: SplitDto,
    modifier: Modifier = Modifier
) {
    val animProgress = remember(split.splitIndex) { Animatable(0f) }
    LaunchedEffect(split.splitIndex) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val paceStr = UiFormatters.formatPace(split.averagePaceSecondsPerKm)
    val timeStr = UiFormatters.formatDuration(split.durationSeconds)
    val deltaDisplay = UiFormatters.formatPaceDelta(split.deltaSecondsVsAvg)
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }

    val deltaColor = when (deltaDisplay.category) {
        DeltaCategory.AHEAD -> colors.electricLime
        DeltaCategory.BEHIND -> colors.laserAmber
        DeltaCategory.EVEN -> colors.slateSubtle
    }

    Box(
        modifier = modifier
            .width(130.dp)
            .graphicsLayer {
                val p = animProgress.value.coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.82f + 0.18f * p
                scaleY = 0.82f + 0.18f * p
            }
            .clip(cardShape)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(8.dp)
    ) {
        Column {
            // Header: KM Index & Delta Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KM ${split.splitIndex}",
                    style = typography.LabelUppercase.copy(
                        fontSize = 10.sp,
                        color = colors.electricCyan,
                        fontWeight = FontWeight.Black
                    )
                )

                Text(
                    text = deltaDisplay.formattedText,
                    style = typography.TelemetryMicro.copy(
                        fontSize = 9.sp,
                        color = deltaColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pace Value
            Text(
                text = paceStr,
                style = typography.MetricMedium.copy(fontSize = 20.sp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Duration Subtext
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPLIT TIME",
                    style = typography.LabelMicro.copy(fontSize = 8.sp, color = colors.slateMuted)
                )
                Text(
                    text = timeStr,
                    style = typography.TelemetryMicro.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            }
        }
    }
}

@Composable
fun RealtimeSplitTicker(
    splits: List<SplitDto>,
    acceptedDistanceMeters: Double,
    modifier: Modifier = Modifier
) {
    val progressMeters = (acceptedDistanceMeters % 1000.0).toFloat()
    val progressFraction = (progressMeters / 1000.0f).coerceIn(0f, 1f)
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val barShape = remember { RoundedCornerShape(2.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Ticker Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.electricCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1.0 KM SPLITS",
                        style = typography.LabelUppercase.copy(fontSize = 10.sp, letterSpacing = 1.sp)
                    )
                }

                Text(
                    text = "${progressMeters.roundToInt()} / 1000 m",
                    style = typography.TelemetryMicro.copy(
                        color = colors.electricCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Next Split Progress Bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(barShape),
                color = colors.electricCyan,
                trackColor = colors.surfaceHigh,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Splits list or Empty Banner
            if (splits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Splits trigger automatically every 1.0 km",
                        style = typography.BodyText.copy(
                            fontSize = 11.sp,
                            color = colors.slateMuted
                        )
                    )
                }
            } else {
                val reversedSplits = remember(splits) { splits.asReversed() }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reversedSplits, key = { it.splitIndex }) { split ->
                        SplitCard(
                            split = split,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}
