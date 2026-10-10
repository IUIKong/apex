package com.apex.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme

@Composable
fun TacticalControlsBar(
    isRecording: Boolean,
    isPaused: Boolean,
    isControlsLocked: Boolean,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onFinishClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = !isControlsLocked
    val alpha = if (enabled) 1f else 0.4f
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val buttonShape = remember { RoundedCornerShape(ApexDimens.RadiusButtonTactical) }
    val pauseIconShape = remember { RoundedCornerShape(1.dp) }
    val finishIconShape = remember { RoundedCornerShape(2.dp) }

    // Kinetic breathing pulse for the start indicator in idle state
    val infiniteTransition = rememberInfiniteTransition(label = "tactical_start_pulse")
    val idleDotScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_dot_scale"
    )
    val idleDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_dot_alpha"
    )

    val luxuryEase = remember { CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
    ) {
        AnimatedContent(
            targetState = isRecording,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(tween(340, easing = luxuryEase)) { it / 2 } + fadeIn(tween(260)))
                        .togetherWith(
                            slideOutVertically(tween(260, easing = luxuryEase)) { -it / 2 } + fadeOut(tween(200))
                        )
                } else {
                    (slideInVertically(tween(340, easing = luxuryEase)) { -it / 2 } + fadeIn(tween(260)))
                        .togetherWith(
                            slideOutVertically(tween(260, easing = luxuryEase)) { it / 2 } + fadeOut(tween(200))
                        )
                }
            },
            label = "tactical_controls_mode"
        ) { recording ->
            if (!recording) {
                // Idle State: Deep Espresso Start Button with kinetic breathing beacon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ApexDimens.HeightTacticalButton)
                        .clip(buttonShape)
                        .background(colors.textPrimary)
                        .tactilePress(
                            pressedScale = 0.96f,
                            enabled = enabled
                        ) {
                            if (SlideToLockController.isActionAllowed(isControlsLocked)) {
                                onStartClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer {
                                    scaleX = idleDotScale
                                    scaleY = idleDotScale
                                    this.alpha = idleDotAlpha
                                }
                                .clip(CircleShape)
                                .background(colors.punchyCrimson)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START SESSION",
                            style = typography.LabelUppercase.copy(
                                color = colors.canvasBackground,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                // Active Workout State: [Pause or Resume Animated] + [Finish]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pause / Resume Toggle Container with AnimatedContent
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = isPaused,
                            transitionSpec = {
                                (slideInHorizontally(tween(280, easing = luxuryEase)) { if (targetState) it / 3 else -it / 3 } + fadeIn(tween(220)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(220, easing = luxuryEase)) { if (targetState) -it / 3 else it / 3 } + fadeOut(tween(180))
                                    )
                            },
                            label = "pause_resume_transition"
                        ) { paused ->
                            if (!paused) {
                                // PAUSE Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(ApexDimens.HeightTacticalButton)
                                        .clip(buttonShape)
                                        .background(colors.surfaceElevated)
                                        .border(1.5.dp, colors.borderActive, buttonShape)
                                        .tactilePress(
                                            pressedScale = 0.95f,
                                            enabled = enabled
                                        ) {
                                            if (SlideToLockController.isActionAllowed(isControlsLocked)) {
                                                onPauseClick()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(pauseIconShape)
                                                .background(colors.laserAmber)
                                        )
                                        Spacer(modifier = Modifier.width(7.dp))
                                        Text(
                                            text = "PAUSE",
                                            style = typography.LabelUppercase.copy(
                                                fontSize = 12.sp,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                // RESUME Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(ApexDimens.HeightTacticalButton)
                                        .clip(buttonShape)
                                        .background(colors.electricLime)
                                        .tactilePress(
                                            pressedScale = 0.95f,
                                            enabled = enabled
                                        ) {
                                            if (SlideToLockController.isActionAllowed(isControlsLocked)) {
                                                onResumeClick()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colors.obsidian)
                                        )
                                        Spacer(modifier = Modifier.width(7.dp))
                                        Text(
                                            text = "RESUME",
                                            style = typography.LabelUppercase.copy(
                                                fontSize = 12.sp,
                                                color = colors.obsidian,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // FINISH Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(ApexDimens.HeightTacticalButton)
                            .clip(buttonShape)
                            .background(colors.electricCyan)
                            .tactilePress(
                                pressedScale = 0.95f,
                                enabled = enabled
                            ) {
                                if (SlideToLockController.isActionAllowed(isControlsLocked)) {
                                    onFinishClick()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(finishIconShape)
                                    .background(colors.obsidian)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "FINISH",
                                style = typography.LabelUppercase.copy(
                                    fontSize = 12.sp,
                                    color = colors.obsidian,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
