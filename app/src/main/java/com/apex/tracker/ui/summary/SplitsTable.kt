package com.apex.tracker.ui.summary

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.state.DeltaCategory
import com.apex.tracker.ui.state.SplitDto
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme

@Composable
fun SplitsTable(
    splits: List<SplitDto>,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ApexDimens.RadiusCardStandard))
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(ApexDimens.RadiusCardStandard))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KILOMETER SPLITS BREAKDOWN",
                    style = typography.LabelUppercase.copy(fontSize = 10.sp)
                )
                Text(
                    text = "${splits.size} TOTAL",
                    style = typography.TelemetryMicro.copy(color = colors.slateSubtle)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Column Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "SPLIT", style = typography.LabelMicro, modifier = Modifier.weight(1.0f))
                Text(text = "PACE", style = typography.LabelMicro, modifier = Modifier.weight(1.0f))
                Text(text = "DELTA", style = typography.LabelMicro, modifier = Modifier.weight(1.1f))
                Text(text = "TIME", style = typography.LabelMicro, modifier = Modifier.weight(1.0f))
            }

            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)

            if (splits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No 1.0 km splits recorded for this activity",
                        style = typography.BodyText.copy(fontSize = 11.sp, color = colors.slateMuted)
                    )
                }
            } else {
                splits.forEachIndexed { index, split ->
                    val paceStr = UiFormatters.formatPace(split.averagePaceSecondsPerKm)
                    val timeStr = UiFormatters.formatDuration(split.durationSeconds)
                    val deltaDisplay = UiFormatters.formatPaceDelta(split.deltaSecondsVsAvg)
                    val deltaColor = when (deltaDisplay.category) {
                        DeltaCategory.AHEAD -> colors.electricLime
                        DeltaCategory.BEHIND -> colors.laserAmber
                        DeltaCategory.EVEN -> colors.slateSubtle
                    }

                    val rowBg = if (index % 2 == 0) colors.surface else colors.surface.copy(alpha = 0.5f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KM ${split.splitIndex}",
                            style = typography.MetricSmall.copy(fontSize = 12.sp, color = colors.electricCyan),
                            modifier = Modifier.weight(1.0f)
                        )
                        Text(
                            text = paceStr,
                            style = typography.MetricSmall.copy(fontSize = 12.sp),
                            modifier = Modifier.weight(1.0f)
                        )
                        Text(
                            text = deltaDisplay.formattedText,
                            style = typography.TelemetryMicro.copy(
                                fontSize = 11.sp,
                                color = deltaColor,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = timeStr,
                            style = typography.TelemetryMicro.copy(fontSize = 11.sp),
                            modifier = Modifier.weight(1.0f)
                        )
                    }

                    if (index < splits.size - 1) {
                        HorizontalDivider(color = colors.borderSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
