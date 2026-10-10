package com.apex.tracker.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import com.apex.tracker.ui.theme.LightApexColorScheme

@Composable
fun CrashRecoveryBanner(
    onResumeClick: () -> Unit,
    onDiscardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val resumeTextColor = if (colors == LightApexColorScheme) Color.White else colors.canvasBackground

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val buttonShape = remember { RoundedCornerShape(8.dp) }
    val badgeShape = remember { RoundedCornerShape(4.dp) }

    val infiniteTransition = rememberInfiniteTransition(label = "crash_warning_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crash_beacon_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.laserAmberDim)
            .border(1.5.dp, colors.laserAmber, cardShape)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Warning pulsing beacon dot
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .clip(CircleShape)
                            .background(colors.laserAmber.copy(alpha = 0.35f))
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.laserAmber)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(colors.laserAmber)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "RESTORE",
                        style = typography.LabelMicro.copy(
                            fontSize = 8.5.sp,
                            color = colors.surfaceElevated,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "INTERRUPTED SESSION DETECTED",
                    style = typography.Headline.copy(
                        fontSize = 12.5.sp,
                        color = colors.laserAmber,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "An active tracking session was recovered from crash checkpoint. Instant lossless resume is available.",
                style = typography.BodyText.copy(fontSize = 11.sp, color = colors.textPrimary)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Resume button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(buttonShape)
                        .background(colors.electricLime)
                        .tactilePress(pressedScale = 0.94f) { onResumeClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RESUME SESSION",
                        style = typography.LabelUppercase.copy(
                            color = resumeTextColor,
                            fontWeight = FontWeight.Black
                        )
                    )
                }

                // Discard button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(buttonShape)
                        .background(colors.surfaceHigh)
                        .border(1.dp, colors.borderSubtle, buttonShape)
                        .tactilePress(pressedScale = 0.94f) { onDiscardClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DISCARD",
                        style = typography.LabelUppercase.copy(
                            color = colors.slateSubtle,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
