package com.apex.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
import com.apex.tracker.ui.sound.ApexAudioFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import kotlin.math.roundToInt

object SlideToLockController {
    /**
     * Safety check helper: Control actions (pause, resume, finish) are strictly blocked
     * when controls are locked.
     */
    fun isActionAllowed(isLocked: Boolean): Boolean {
        return !isLocked
    }
}

@Composable
fun SlideToLockGuard(
    isLocked: Boolean,
    onLockChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val trackBorderColor by animateColorAsState(
        targetValue = if (isLocked) colors.laserAmber else colors.borderSubtle,
        label = "lock_border"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isLocked) colors.laserAmber else colors.textPrimary,
        label = "thumb_color"
    )
    val trackBgColor by animateColorAsState(
        targetValue = if (isLocked) colors.laserAmberDim else colors.surface,
        label = "track_bg"
    )

    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    // Subtle breathing shimmer for directional guide chevrons
    val infiniteTransition = rememberInfiniteTransition(label = "slider_track_pulse")
    val chevronAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevron_alpha"
    )

    val snapSpring = remember {
        spring<Float>(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(ApexDimens.HeightLockSlider)
            .clip(pillShape)
            .background(trackBgColor)
            .border(
                width = 1.dp,
                color = trackBorderColor,
                shape = pillShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbSize = ApexDimens.HeightLockSlider - 8.dp
        val maxOffsetPx = with(density) { (maxWidth - thumbSize).toPx().coerceAtLeast(0f) }

        val offsetAnimatable = remember { Animatable(if (isLocked) maxOffsetPx else 0f) }
        var isDragging by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(isLocked, maxOffsetPx) {
            if (!isDragging) {
                val target = if (isLocked) maxOffsetPx else 0f
                if (offsetAnimatable.targetValue != target || offsetAnimatable.value != target) {
                    offsetAnimatable.animateTo(target, snapSpring)
                }
            }
        }

        // Center track text with animated directional guidance
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isLocked) {
                    Text(
                        text = "‹ ‹ ‹  ",
                        style = typography.LabelUppercase.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.laserAmber.copy(alpha = chevronAlpha)
                        )
                    )
                }
                Text(
                    text = if (isLocked) "SLIDE TO UNLOCK" else "SLIDE TO LOCK CONTROLS",
                    style = typography.LabelUppercase.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLocked) colors.laserAmber else colors.slateMuted,
                        letterSpacing = 1.2.sp
                    )
                )
                if (!isLocked) {
                    Text(
                        text = "  › › ›",
                        style = typography.LabelUppercase.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.slateMuted.copy(alpha = chevronAlpha)
                        )
                    )
                }
            }
        }

        // Draggable thumb pill
        val displayOffset = if (isDragging) dragOffset.coerceIn(0f, maxOffsetPx) else offsetAnimatable.value.coerceIn(0f, maxOffsetPx)
        val lockProgress = if (maxOffsetPx > 0f) (displayOffset / maxOffsetPx).coerceIn(0f, 1f) else (if (isLocked) 1f else 0f)

        Box(
            modifier = Modifier
                .offset { IntOffset(displayOffset.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragOffset = (dragOffset + delta).coerceIn(0f, maxOffsetPx)
                    },
                    onDragStarted = {
                        isDragging = true
                        dragOffset = offsetAnimatable.value.coerceIn(0f, maxOffsetPx)
                    },
                    onDragStopped = {
                        isDragging = false
                        offsetAnimatable.snapTo(dragOffset)
                        if (!isLocked && dragOffset > maxOffsetPx * 0.65f) {
                            ApexAudioFeedback.playClick(view)
                            onLockChanged(true)
                            offsetAnimatable.animateTo(maxOffsetPx, snapSpring)
                        } else if (isLocked && dragOffset < maxOffsetPx * 0.35f) {
                            ApexAudioFeedback.playClick(view)
                            onLockChanged(false)
                            offsetAnimatable.animateTo(0f, snapSpring)
                        } else {
                            // Snap back to current lock state
                            offsetAnimatable.animateTo(if (isLocked) maxOffsetPx else 0f, snapSpring)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedPadlockIcon(
                lockProgress = lockProgress,
                tint = colors.canvasBackground,
                cutoutColor = thumbColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Animated Padlock Icon:
 * Shackle smoothly swings open when unlocked (progress = 0) and clicks shut
 * flush into the lock body as the user slides across to locked (progress = 1).
 */
@Composable
private fun AnimatedPadlockIcon(
    lockProgress: Float,
    tint: androidx.compose.ui.graphics.Color,
    cutoutColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Lock Body (bottom 55% of icon)
        val bodyWidth = w * 0.72f
        val bodyHeight = h * 0.52f
        val bodyLeft = (w - bodyWidth) / 2f
        val bodyTop = h - bodyHeight - 1.5.dp.toPx()
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2.dp.toPx())

        // Draw Lock Body
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = cornerRadius
        )

        // Keyhole tumbler cutout in center of body
        val keyholeX = bodyLeft + bodyWidth / 2f
        val keyholeY = bodyTop + bodyHeight * 0.40f
        drawCircle(
            color = cutoutColor,
            radius = 1.3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(keyholeX, keyholeY)
        )
        drawLine(
            color = cutoutColor,
            start = androidx.compose.ui.geometry.Offset(keyholeX, keyholeY),
            end = androidx.compose.ui.geometry.Offset(keyholeX, bodyTop + bodyHeight * 0.74f),
            strokeWidth = 1.4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Shackle Geometry
        // When progress = 1 (locked): shackle is fully closed into body
        // When progress = 0 (unlocked): shackle is lifted and swung open by -26 degrees
        val shackleWidth = bodyWidth * 0.60f
        val shackleHeight = h * 0.40f
        val shackleLeft = (w - shackleWidth) / 2f
        val strokeWidthPx = 1.9.dp.toPx()

        val openFraction = (1f - lockProgress).coerceIn(0f, 1f)
        val shacklePivotX = shackleLeft + strokeWidthPx / 2f
        val shacklePivotY = bodyTop

        rotate(
            degrees = -26f * openFraction,
            pivot = androidx.compose.ui.geometry.Offset(shacklePivotX, shacklePivotY)
        ) {
            translate(left = 0f, top = -2.5.dp.toPx() * openFraction) {
                val shacklePath = androidx.compose.ui.graphics.Path().apply {
                    // Left leg anchored in lock body
                    moveTo(shackleLeft, bodyTop + 1.dp.toPx())
                    lineTo(shackleLeft, bodyTop - shackleHeight + shackleWidth / 2f)
                    // Top curved arch
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            shackleLeft,
                            bodyTop - shackleHeight,
                            shackleLeft + shackleWidth,
                            bodyTop - shackleHeight + shackleWidth
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    // Right leg descending into body
                    lineTo(shackleLeft + shackleWidth, bodyTop + 1.dp.toPx())
                }

                drawPath(
                    path = shacklePath,
                    color = tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidthPx,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }
    }
}
