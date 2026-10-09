package com.apex.tracker.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.platform.LocalView
import com.apex.tracker.ui.sound.ApexAudioFeedback

/**
 * Luxury Atelier tactile button press modifier:
 * Provides a responsive physical press feedback (scale reduction to [pressedScale])
 * with an elastic spring rebound and subtle audio click feedback.
 */
fun Modifier.tactilePress(
    pressedScale: Float = 0.94f,
    enabled: Boolean = true,
    withAudioFeedback: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tactile_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null, // Custom physical scale replaces flat ripple
                    enabled = enabled,
                    onClick = {
                        if (withAudioFeedback && enabled) {
                            ApexAudioFeedback.playClick(view)
                        }
                        onClick()
                    }
                )
            } else {
                Modifier
            }
        )
}
