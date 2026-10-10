package com.apex.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Animated Monospace Numeral Ticker:
 * Slides individual changing digits vertically with smooth fade when telemetry metrics increment,
 * while keeping unchanging digits and punctuation stable for a luxury electronic watch aesthetic.
 */
@Composable
fun AnimatedNumeralTicker(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val luxuryEase = remember { CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, char ->
            val slotKey = text.length - 1 - index
            key(slotKey) {
                if (char.isDigit()) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val isUp = targetState > initialState
                            if (isUp) {
                                (slideInVertically(tween(260, easing = luxuryEase)) { it / 2 } + fadeIn(tween(180)))
                                    .togetherWith(
                                        slideOutVertically(tween(240, easing = luxuryEase)) { -it / 2 } + fadeOut(tween(140))
                                    )
                            } else {
                                (slideInVertically(tween(260, easing = luxuryEase)) { -it / 2 } + fadeIn(tween(180)))
                                    .togetherWith(
                                        slideOutVertically(tween(240, easing = luxuryEase)) { it / 2 } + fadeOut(tween(140))
                                    )
                            }
                        },
                        label = "numeral_digit_$slotKey"
                    ) { targetChar ->
                        Text(
                            text = targetChar.toString(),
                            style = style,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                } else {
                    Text(
                        text = char.toString(),
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
