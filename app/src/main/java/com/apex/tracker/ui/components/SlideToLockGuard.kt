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
            Text(
                text = if (isLocked) "◀" else "▶",
                style = typography.LabelMicro.copy(
                    fontSize = 11.sp,
                    color = colors.canvasBackground,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
