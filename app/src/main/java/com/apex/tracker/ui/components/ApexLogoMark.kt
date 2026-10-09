package com.apex.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun DrawScope.drawApexPeaks(primaryColor: Color, accentColor: Color) {
    val scale = size.width / 48f

    val outerPeak = Path().apply {
        moveTo(24f * scale, 8f * scale)
        lineTo(40f * scale, 34f * scale)
        lineTo(34f * scale, 34f * scale)
        lineTo(24f * scale, 18f * scale)
        lineTo(14f * scale, 34f * scale)
        lineTo(8f * scale, 34f * scale)
        close()
    }
    drawPath(outerPeak, color = primaryColor)

    val innerPeak = Path().apply {
        moveTo(24f * scale, 22f * scale)
        lineTo(34f * scale, 38f * scale)
        lineTo(29.5f * scale, 38f * scale)
        lineTo(24f * scale, 29f * scale)
        lineTo(18.5f * scale, 38f * scale)
        lineTo(14f * scale, 38f * scale)
        close()
    }
    drawPath(innerPeak, color = accentColor)
}

/**
 * Minimal Blackesque Luxury Apex Runner Motif:
 * Clean, sharp, razor-thin minimalist chevron runner motif in crisp white / platinum
 * set within a sleek obsidian black badge, perfectly visible and iconic on all light & dark screens.
 */
@Composable
fun ApexLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    showBadge: Boolean = true,
    primaryColor: Color = Color(0xFFFFFFFF),
    accentColor: Color = Color(0xFFF5F5F4)
) {
    if (showBadge) {
        val cornerRadius = size * 0.26f
        val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color(0xFF0C0A09))
                .border(1.dp, Color(0xFF262626), shape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.70f)) {
                drawApexPeaks(primaryColor, accentColor)
            }
        }
    } else {
        Canvas(modifier = modifier.size(size)) {
            drawApexPeaks(primaryColor, accentColor)
        }
    }
}
